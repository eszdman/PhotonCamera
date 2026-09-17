# Alignment offline benchmark (moderngl)

Two benchmarks live here:

- `align_bench.py` — original synthetic-scene benchmark (integer shifts,
  optional hot pixels). Kept for history; its conclusions were superseded by
  the real-image bench below.
- `align_bench_real.py` — **real-image benchmark**: real Apple ProRAW DNGs
  (iPhone 12 Pro Max, linear Raw) re-mosaiced to Bayer, warped with a random
  hand-shake homography (rotation + translation + scale + perspective, no
  in-frame motion — how most real bursts behave), per-frame photon noise,
  ground truth per tile from the homography.

## Setup

```
python3 -m venv .venv
.venv/bin/pip install moderngl numpy pillow rawpy
# real DNGs (iPhone 12 Pro Max ProRAW) into data/:
#   https://www.libraw.org/node/2626 has working dropbox links (?dl=1)
```

## Usage

```
.venv/bin/python align_bench_real.py                       # shipped config on all images
.venv/bin/python align_bench_real.py --config old --config final --scenario night
.venv/bin/python align_bench_real.py --seed 41 --warpseed 123   # other shake/noise draws
.venv/bin/python align_bench_real.py --list
```

## Method (align_bench_real.py)

- Pipeline is app-faithful:
  normalize.glsl variants (numpy model, verified equal to the actual shader
  within fp16 epsilon) -> GLHistogram 30th-percentile black level +
  normalizebl.glsl unsharp (sharpness=1.0) -> `GLUtils.createPyramidStore`
  pyramid (Catmull-Rom bicubic downscale, exactly replicating
  `textureBicubicHardware` at f=0.5 => taps [-1,9,9,-1]/16) -> the **actual**
  `align.glsl` from the app assets via moderngl, with the Java loop's
  per-level `integralNorm` (x prefilter noise factor), `first` flag,
  dispatch sizes and prev-alignment propagation.
- Burst synthesis: linear RGB planes -> per-CFA-site inverse-homography
  bilinear sampling -> per-frame photon noise (sigma = sqrt(s*noiseS +
  noiseO) in normalized units) -> u16 quantization. Scenarios = same scene
  metering, different ISO (day 2.5e-4, dusk 1.6e-3, night 6e-3 noiseS).
- Ground truth: per-tile displacement of the homography at the tile center
  (tiles are 8 half-res texels, centered on tile_xy*8 like the shader's
  work groups).
- Metrics (half-res px): `med`/`p90`/`bad1%`/`bad2%` = tile error vs GT;
  `blk` = deviation from the 3x3 median offset (blocky-artifact indicator).
- `legacy_align.glsl` = the pre-robust-cost shader
  (`git show cd7136d7^:...align.glsl`); configs with `shader="legacy"`
  (`old`, `old_o9`) run it for A/B against the shipped pipeline.

## Results that drove the shipped configuration

5 seeds x {proraw3 (day scene), proraw4 (indoor)} x {day, night ISO3200},
tile error > 2 half-res px (bad2) averaged:

| config                     | day bad2 | night bad2 | notes |
|----------------------------|----------|------------|-------|
| old (legacy plain SAD, o5) | 21.8%    | 38.9%      | also ~80% of tiles carry a systematic 1px bias (rgba16f round bug in vec4ToAlignment) |
| old_o9 (legacy + diagonals)| 19.9%    | 36.3%      | diagonal propagation alone is a big win |
| uncommitted "improvements" | 26-55%   | 33-79%     | truncation at 3 sigma + k=3 gate + gaussian trim: uniformly *worse* than old on real images |
| **final (shipped)**        | **16.2%**| **28.3%**  | noise-normalized L1, NO truncation, gate k=2.0, OFFSETS=9, gaussian sigma 1.5 no trim |

Shipped parameters (all fixed constants now, no tunables):
- `align.glsl`: noise-normalized L1 cost without truncation (hot pixels are
  invisible after the prefilter + pyramid; truncation only flattened real
  detail), significance gate k=2.0, OFFSETS=9, fixed `getAlignment` clamp
  (old `textureSize(baseTexture)/TILE_AL-1` collapsed to 0 / undefined at
  coarse levels, scrambling large-warp propagation).
- `normalize.glsl`: centered 5-tap gaussian sigma=1.5, no min/max trim
  (trim vs no-trim measured identical — removing it keeps detail).
- `PyramidAlignment.java`: `integralNorm` multiplies by the prefilter's
  noise factor 1/sum(w1d^2) = 4.47.

Known remaining failure mode: large low-texture regions (walls/sky) can lock
coherently wrong at coarse levels on some noise draws; the field stays smooth
(low `blk`), so the merge impact is soft rather than blocky-ghosty. The gate
schedule experiments (`final_s2`, `final_sl`) did not improve it.

## Polynomial-only global motion (poly_* configs) — SIMULATION RESULTS, not shipped

Motivation: for hand-shake-only bursts (no in-frame motion — how most real
bursts behave) the true motion field IS a smooth projective map, so fitting a
global polynomial to the tile offsets at each level and propagating only the
polynomial (never per-tile "decimal movements") should reject wrong-basin
locks as outliers and give sub-texel precision.

Implementation (bench only, `AlignRunner.run(poly=...)`): after each level's
dispatch, decode the tile offsets, robustly fit (MAD outliers, 1.5-texel
floor, gate-frozen tiles weighted 0 — they only echo prev and carry no
evidence) the residual against the propagated polynomial
`P_i = 2*P_{i+1} + fit(v_raw - 2*P_{i+1})` in normalized physical coords
(same (u,v) on every level), write the fractional field back, continue.

What worked (5 seeds, 3 ProRAW images, day/dusk/night):
- Basin-failure bursts (seed 41: raw per-tile field 52-57% bad tiles):
  polynomial cuts bad2 to 10-20% — the wrong coherent region is rejected.
- Small shakes (seed 29): sub-texel polynomial output lands nearly every
  tile within 1px: bad2 ~0.0% vs final's 0.3-7.4% (poly_k05/k0).
- Relaxing the shader gate to 0.5-0 (the fit does the rejection) is
  monotonically better with poly on those bursts.

What breaks it (seed 7, and why it is NOT shipped):
- On some well-behaved bursts the fit lands a systematic ~1.5px bias: the
  integer matcher's ±1 search around prev produces a truncation-biased
  distribution (movers undershoot, frozen tiles echo prev), and the LS fit
  of that distribution inherits the bias — final: bad2 2.4-12.5%,
  poly(all gates): 20-48%. bad1 ~100% (whole field uniformly short).
- A per-burst selector (output poly only when many tiles sit >= 2.5 texels
  off the model = basin signature) misfires: the biased-fit case produces
  the same "disagreement" signature as the basin case, and picks the wrong
  source on both seed 7 and parts of seed 29.
- 3 refinement passes (`final_r3`) changed nothing (identical numbers, 2x
  slower): reach is not the constraint, the gate/cost is.

To make it shippable it needs an unbiased sub-texel evidence source — e.g.
exposing the per-candidate tile costs (parabolic minimum of the cost at
prev±1) instead of integer argmins, which also makes the shader gate
unnecessary. All poly variants remain in the bench (`poly_quad`, `poly_k05`,
`poly_k0`, `poly_sel*`, `poly_last`, `poly_aff`, `poly_bl`, `poly_quad_h`)
for that future work.

## RANSAC global motion (`ransac_*` configs) — the winning variant

Replaces the MAD least-squares fit with RANSAC over the same residual-
accumulated quadratic: sample minimal tile subsets (6 points for quad), fit
exactly, count tiles within tau=1.5 texels of BOTH axes, keep the largest
consensus, polish with LS on the consensus. Gate-frozen tiles are excluded
from sampling and scoring (they would form a spurious perfect consensus at
prev). Adaptive trial count (99.9% confidence bound), capped at 400.

Key addition — **re-centering** (`recenter="last"`): after the last level's
fit, the fitted field is written back as the prev for that level and the
level is re-matched once, then refit. Tiles then start near the true offset,
so their integer matches distribute +-1 AROUND the truth instead of
truncating one-sided toward the stale prev — this kills the systematic
~1.5px undershoot bias that sank the plain polynomial version (seed 7).

`ransac_rcl` = RANSAC per level + one re-centering round at L0, gate 0
(the consensus does the rejection; the shader gate only froze informative
tiles). Measured vs the shipped `final` config (30 bursts: 3 ProRAW images
x day/dusk/night x 5 seeds):

| regime | final bad2 | ransac_rcl bad2 |
|---|---|---|
| wrong-basin bursts (seed 41) | 32-57% | **0.3-2.4%** |
| small shake (seed 29) | 0.3-7.5% | **0.0-5.9%** |
| large shake bias case (seed 7) | 2.4-12.5% | **0.0-6.7%** |
| all 30 bursts, average | 29.7% | **10.8%** |
| hard night city (proraw2:night) | 57-83% | **56-72%** |

Median errors land sub-pixel (0.7-1.2px typical) since the output field is
fractional. bad1 (1-2px band) can be higher than per-tile output — the
smooth field may sit just under 1px off — but bad2 (the ghosting class) is
equal or better on every tested burst. Bench wall time 15-70ms (dominated
by the per-level Python readback/fit/upload; on-device the RANSAC itself is
sub-ms in Java on <=7k tiles, the cost is ~8 small GPU readbacks).

Not yet wired into the app: needs the per-level alignment readback + RANSAC
+ writeback in `PyramidAlignment` (CPU side, no shader change - the
fractional prev encoding is already supported by align.glsl's
vec4ToAlignment/floor handling).

## Local motion with RANSAC (`ransac_local*`, `--objshift`)

`ransac_rcl` outputs the global polynomial for EVERY tile, so a genuinely
moving object rides the background field (measured with the moving-object
burst mode: obj_med = the full object shift, obj_bad2 = 100%). In the app
that is safe - the merge's diff weights reject the misaligned object (it is
reconstructed from fewer frames, no ghosting) - but it is not local
alignment. The plain per-tile matcher (`final`) DOES align moving objects
(obj_med 0.5px) because each tile keeps its own offset down the pyramid.

Two local-motion modes were added on top of RANSAC (`--objshift 6 -4`
object, ~19x14 tiles, day/night):

- `ransac_local` (L0 only): RANSAC inliers take the polynomial; tiles that
  COHERENTLY disagree (>=3 outlier neighbours) keep median-filtered raw
  matches, with adaptive blended re-centering rounds (each round lets
  object tiles advance ~2 texels toward their local offset).
- `ransac_local2` (per level): same, but coherent outliers keep their raw
  offsets at every pyramid level, so objects descend with their own motion
  like the per-tile matcher.

Measured (object shift +6,-4 half-res px over the hand shake):

| config | bg_bad2 | obj_med | obj_bad2 |
|---|---|---|---|
| final (per-tile) | 6.3-16.9% | 0.5px | 10-30% |
| ransac_rcl | 0.0-13.9% | 7.0-7.3px | 100% |
| ransac_local2 | 2.7-14.7% | 1.4-3.1px | 40-96% |

Local motion is recovered only PARTIALLY: object tiles fight the global
prev at L0 and integer argmins of weak-texture objects stall ~1px short
(the same missing cost-interpolation limit as the truncation bias). And on
no-motion bursts the per-level blending gives back part of the pure RANSAC
win (0.6 -> 13-17% bad2 on some night bursts): flat-region noise locks
form coherent blobs indistinguishable from moving objects without a
per-tile confidence signal.

Conclusion: for the no-in-frame-motion majority, pure `ransac_rcl` is the
best measured config. Full local alignment on top of it needs the per-tile
match COST exposed from the shader: high-confidence coherent outliers =
moving objects (keep local), low-confidence ones = noise locks (take the
RANSAC field). That single signal also unlocks unbiased sub-texel fits.

## Single-shot RANSAC seeding (`seed*` configs)

Architecture (the original idea, verified): run RANSAC ONCE at the first
level with enough tiles (L3 here, ~130 tiles; coarser grids pass raw
through), write the RANSAC field into that level's alignment, and let the
plain per-tile descent continue below it — final offsets = RANSAC +
accumulated per-level local differences. One fit, one texture write.

Measured (gate 2.0):

| burst class | final | seed | ransac_rcl |
|---|---|---|---|
| moving object, obj_med | 0.5-2.6px | **1.1-2.3px** | 7.0-7.3px |
| moving object, total bad2 | 28.1-47.4% | **18.3-32.4%** | 4.9-40.1% |
| basin bursts (no object) | 32.2-56.8% | **10.6-27.8%** | 0.3-2.4% |
| small shake (no object) | 0.3-7.5% | 0.3-10.5% | 0.0-5.9% |
| clean large shake (no object) | **2.4-12.5%** | 14.2-32.7% | 0.0-6.7% |

What it delivers: local alignment is PRESERVED (objects descend with their
own offsets from the RANSAC base — the strongest property of this design),
basin failures are roughly halved vs the plain per-tile matcher, and it is
the cheapest RANSAC variant (one fit + one write). What it gives up: on
clean large-shake bursts the single coarse seed lands one mover-MODE off
the sub-texel truth (integer argmins again), and gate-frozen tiles ride
that error down the pyramid — 14-33% bad2 vs final's 2-12%. Variant
findings: outlier-only seeding (`seed_out`) is a no-op because the basin
failure forms BELOW the seeding level; seeding one level finer (`seed_L2`/
`seed_L1`) is equivalent (the residual accumulation converges to the same
field); a sharpening re-match at the seeding level (`seed_rc`) makes it
worse (the coarse-level consensus jumps modes); gate 0 (`seed_k0`) trades
seed-7 days for seed-29 nights — gate 2.0 is the better default.

Bottom line across all three architectures measured: per-level RANSAC
(`ransac_rcl`) for maximum background quality when motion is global;
single-shot seeding (`seed`) when local motion must survive; both are
limited by the same missing signal (per-candidate match costs for unbiased
sub-texel evidence).
