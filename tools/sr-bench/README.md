# SR host bench (moderngl) — real shaders, synthetic bursts

Runs the **actual app shader** `app/src/main/assets/shaders/merge/srrecover.glsl`
through moderngl on synthetic handheld bursts and scores the recovered
above-Nyquist band against ground truth. The deposit
(`merge/srscatter`) is mirrored in numpy only to build the fixed-point
accumulators; the recovery pass is the real GLSL, so restoration gain,
gates and band-edge changes are A/B'd here and only confirmed on device.

This extends the approach of `tools/alignment-bench/` (which drove the
shipped alignment configuration the same way) to the SR path.

## Setup

```
python3 -m venv .venv
.venv/bin/pip install moderngl numpy
```

`moderngl` needs a GL 3.1-compute-capable context; EGL + a GPU or Mesa
llvmpipe both work.

## Usage

```
.venv/bin/python sr_bench.py                          # 24 frames, 3x, shipped gain
.venv/bin/python sr_bench.py --frames 8               # low frame count
.venv/bin/python sr_bench.py --noise 0.03             # high-ISO noise
.venv/bin/python sr_bench.py --gainmax 1.8            # A/B the restoration cap
```

## Method

- Analytic scene (48 sinusoids, 1/f, up to the output Nyquist) through a
  pixel-aperture × lens MTF; per-frame random sub-pixel shifts and
  Gaussian noise; bilinear deposit into `(value, weight)` fixed-point
  accumulators.
- The accumulators are uploaded as R32UI and `merge/srrecover.glsl` runs
  for real; the output band+gate is unpacked and compared against the
  true band scored with the **same** radius-2 radial filter the shader
  uses (scoring with a different operator would blame the recovery for
  the filter difference).
- Metrics: correlation with the true above-Nyquist band, recovered
  amplitude ratio, mean gate.

## Current numbers (seed 7, 3x, noise 0.01, 24 frames)

| `--gainmax` | corr | amplitude ratio |
|---|---|---|
| 1.0 | 0.697 | 0.319 |
| 1.8 | 0.697 | 0.573 |
| **2.2 (shipped)** | **0.697** | **0.701** |
| 3.5 | 0.697 | 1.115 |

(8 frames: corr 0.644, ratio 0.738 at the shipped gain.) Correlation is
gain-invariant here because the synthetic band SNR is high; the
noise-limited upper end is what the per-pixel gate (and a high-`--noise`
run) exercises. Note the shipped 2.2 under-fills the band (0.70); the
noise gate decides how far above it a real scene can be pushed - the
matrix is the instrument for that.

`merge/srlattice.glsl` also runs for real, cross-checked against an
independent numpy reduce (relative fp16 agreement). The synthetic luma
carries a DC pedestal because the app's fixed-point accumulators pack a
non-negative luma.

## Roadmap

1. Package the rest of the merge chain (`srluma`, `srscatter`, `srrefine`,
   `srlattice`) so the deposit itself runs on the GPU, not mirrored, and
   the same setup feeds the alignment experiments.
2. Add `srpre/band.glsl` (render path) with a synthetic aniso output to
   score end-to-end replacement and overshoot.
3. Scenario matrix: day/dusk/night (noise), 8/24/40 frames, static,
   handheld, moving object; plus the acutance/kernel A/Bs with an
   overshoot metric.
4. Wire `glslangValidator` compile checks into a script so a broken SR
   shader fails before any device round.

## Gates in this tool

```
python3 check_shaders.py        # glslang compile gate for every SR shader
PY=.venv/bin/python sh run_matrix.sh   # frames x noise(ISO) x gain matrix
```

The compile gate runs all eight SR shaders the way the app loads them
(LAYOUT expanded, `#version 310 es` prepended); the matrix prints the
corr/ratio for every cell so a tuning change is visible across frame
counts and ISO in one pass.

## `--scatter`: the real GPU deposit

`--scatter` builds the accumulators with the **actual `merge/srclear` +
`merge/srscatter`** shaders instead of the numpy mirror, so the whole
input path (per-site luma, atlas sub-pixel motion, trust/clip weights,
fixed-point atomics) runs as shipped. The numpy mirror stays as a sanity
cross-check (weight totals must agree exactly; the field diff is printed
for information).

Measured (24 frames, 3x, noise 0.01, shipped gain): numpy deposit corr
0.695 / ratio 0.702; GPU deposit corr 0.626 / ratio 0.718; both pass.

Note: the app packs luma in [0, 8] with a hard clamp (16.16 fixed point),
so the synthetic scene is scaled with a small DC pedestal to stay inside
that range - a signed or over-range synthetic field silently clips and
looks "smooth" (this cost one debugging round).

## Band layer (srpre/band.glsl, rendered)

The post-delivery shader is rendered on a synthetic reconstruction (true
low band + half the true above-Nyquist band with a 1 px phase error) with
the recovered band map from the run above. It checks the three properties
the app relies on:

- **gate-0 passthrough is exact** (max err ~5e-4, fp16): with no recovered
  data the layer must not touch the reconstruction at all.
- **tiled origin is exact** (0.00000): the bottom band rendered from a
  haloed input window with `u_inOrigin`/`u_tileOrigin` matches the
  full-frame render - this is the head-driver streaming contract.
- **bounded, sane replacement**: the band correlation is preserved and the
  peak stays ~1.2x the truth band.

Measured (24 frames, 3x): numpy deposit and real GPU deposit both PASS the
whole chain (scatter -> lattice -> recover -> band).

## Open finding: the band filters may be too narrow (measured)

With the scene optically band-limited (the realistic case), the recovery is
nearly exact (corr 0.857, amplitude ratio 0.960) - but the delivered band
does not improve the reconstruction, and the numbers say why:

- the reconstruction's true above-Nyquist error (single high-pass at the raw
  Nyquist) is ~0.043, dominated by the aniso's own broadband content where
  the truth has only ~0.014;
- the recovery/band-layer DoG (radius-2) captures only ~0.003 of the aniso's
  band and ~0.001 of the recovered band - a narrow slice of the
  [raw Nyquist, 1.5x raw Nyquist] range the truth occupies.

So the delivery can only correct a small part of the recoverable band. The
lever to test next is the filter width: widen the recovery and band-layer
band-pass to span the full optical range, and re-run the reconstruction
comparison (informational until then).

## Registration is the real limit on the recovered band (measured)

The widening above is now *done* (the sign fix + a wider band, sigma clamped
to 1.6, DoG radius 3), and the band-pass matches on both sides. The next
question was whether the delivery is safe on real handheld bursts. It is
not, and the bench can now show exactly why.

`--scatter --reg R` injects a per-frame registration error of R output px
into the real srscatter deposit (the trust inputs stay neutral, so this is a
worst-case geometric bound). 18 frames, 3x, noise 0.01:

| reg (out px) | recovered corr | residual | reconstruction vs aniso |
|--------------|----------------|----------|--------------------------|
| 0            | 0.766          | 70%      | 0.0537 -> 0.0491 (-8.6%) |
| 1.0          | 0.614          | 89%      | 0.0537 -> 0.0510 (-5.0%) |
| 2.0          | 0.357          | 108%     | 0.0537 -> 0.0548 (+2.0%, hurts) |

More frames help, but not enough when registration is poor: at reg 2 the
delivery only becomes net-neutral at 40 frames (corr 0.461, residual 92%);
at reg 0 it is already beneficial at 8 frames.

The widening is not the cause. Sign-fix-only (sigma <= 0.95, DoG radius 2),
18 frames: reg 0 corr 0.693 / residual 81% (band rms 0.014, delivery
0.0432 -> 0.0416); reg 1 residual 104% (FAIL); reg 2 residual 122% (FAIL).
The corrected band is simply registration-limited at both widths.

The obvious conditioning levers do not fix it (18 frames, widened):

- **band top** (`--ftop` 0.75/0.65/0.60/0.55, reg 1): delivery 0.0510 /
  0.0517 / 0.0525 / 0.0535 - narrowing the band only *removes* the delivery,
  it does not make it alias-robust (the metric scores against the full true
  band, so a narrowed band just leaves the reconstruction's own error).
- **gain cap** (`--gainmax` 2.2/1.5/1.0, reg 2): residual 120% / 104% / 97%
  - it removes the over-amplification but the recovered band is still wrong
  (corr 0.289), so the reconstruction does not improve.
- **the app's own trust** (feeding the registration residual the shader reads
  from diffPacked/basePacked): the mean gate falls 0.52 -> 0.33 -> 0.15 at
  reg 0/1/2, but it only scales the whole band down - it cannot separate the
  alias from the detail, and reg 2 stays marginal (0.0539 vs 0.0537).

Mechanism: the delivered band includes the *partially cancelled mirror* of
the raw baseband (the classic aliasing image). Multi-frame sub-pixel
diversity cancels it; how well scales with frame count and registration
accuracy. At 18 frames with ideal random dither the residual is already 70%;
real handheld motion is correlated (lower diversity), so it is worse. The
corrected band therefore adds real detail when registration is good and
structured aliasing when it is not - matching the 2x/3x roof-shingle
herringbone seen on device (18-frame handheld).

Implication: the recovery itself is sound; the delivery needs a per-pixel
*reliability* signal that tracks registration/sub-pixel diversity, or the
registration (the durable fix) has to improve. Tuning the band top, the gain
cap, or the existing residual trust does not substitute for either.

## The sub-pixel refinement was a no-op (fixed)

`srrefine` fits `d = -M^-1 v` (the translation minimizing `sum((r+d.g)^2)`),
whose explained energy is `-(v.d)` (positive) and explained fraction is
`-(v.d)/rr`. The shader evaluated `+(v.d)/rr` - its negative - so `conf`
clamped to 0 and `delta` stayed 0 for every cell: the per-cell Lucas-Kanade
correction was never applied, and the drizzle ran on the coarse atlas
positions only. That is exactly the residual misregistration that blurs the
fusion and feeds the above-Nyquist band uncancelled aliasing.

Fixed the confidence sign and the residual (`rr + v.delta`, not
`rr - v.delta`). `refine_bench` now gates the shader instead of reporting
informationally - sweep (planted raw px -> recovered packed texels):

| planted | recovered dx | residual |
|---------|--------------|----------|
| 0.0 (static) | 0.000 | 0.0014 |
| 0.6 | 0.358 | 0.0229 |
| 1.2 | 0.616 | 0.0647 |
| 0.6, gain 1.05 | 0.376 | 0.0241 |
| 0.6, gain 0.95 | 0.355 | 0.0147 |

The correction is active, signed correctly, and zero on a static pair (no
noise warping). Before the fix every row read dx = 0.000.

## Settled: the top native band is real, and SR delivers it (measured)

The question was whether the SR looking softer than Disabled near the raw
Nyquist was correct de-aliasing or a real under-delivery. The bench now
settles it by splitting the synthetic scene by raw frequency and comparing
on an *ideal* (sinc) equal-coverage resample - no one-sided filter - so the
band energies are an MTF comparison, not a resampler artifact.

Top native band = [0.5, 1.0] x raw Nyquist, 18 frames, 3x:

| quantity | value |
|----------|-------|
| native band energy | 51.4 => genuine 49.9 + fold 1.6 (**alias fraction 3%**) |
| reconstruction alone (aniso) | 0.62 x genuine |
| final (aniso + band layer) | 0.81 x genuine, corr(genuine)=0.92, corr(fold)=0.09 |

So Disabled's near-Nyquist content is 97% real scene, not aliasing, and the
SR reconstruction + band deliver ~81% of its energy at 0.92 correlation
with the true content and essentially none of the folded image. The earlier
"soft" reading (0.37-0.51) was an artifact of a box downsample low-passing
only the SR side.

Kernel sensitivity (sigmaScale): 0.75 -> 0.50, 0.55 -> 0.62, 0.40 -> 0.66,
0.30 -> 0.66 (saturates). Full-image rms: 0.0902 / 0.0859 / 0.0856. The
sharpening gain past 0.40 is ~+0.5% amplitude - below any visible
threshold, and it is the synthetic isotropic KernelMap that sets the
ceiling, not the real (edge-adapted) KernelNet map. So kernel tuning is not
the lever; the residual device gap is the fused input (registration).

## Fix: texture acutance gate floor (real detail, no alias)

The settle showed the below-Nyquist *texture* under-delivers because the
UpscaleCrop acutance is elongation-gated: flats and isotropic texture
(foliage, concrete) get none of it, and their sharpness is whatever the
convex reconstruction kernel passes - which saturates as the kernel
sharpens (see above). The fix adds a floor to that gate, so the same
Weber-capped, zero-mean acutance also deconvolves texture. It cannot alias
(convex base + bounded zero-mean addition), and the bench confirms it:

| SR_ACUT_FLAT | aniso E/E_gen | final E/E_gen | corr(fold) | full rms |
|--------------|---------------|---------------|------------|----------|
| 0.00 (old)   | 0.62          | 0.81          | 0.09       | 0.0859   |
| 0.10         | 0.72          | 0.91          | 0.09       | 0.0832   |
| **0.20 (shipped)** | 0.80    | **0.99**      | 0.10       | 0.0815   |
| 0.35         | 0.88          | 1.08          | 0.10       | 0.0802   |

corr(genuine) stays 0.92 throughout; the folded-image correlation barely
moves. At 0.20 the SR now reaches parity with Disabled in the genuine
below-Nyquist band at equal coverage. `--acutflat N` overrides it (the
shader default is the compile-time `SR_ACUT_FLAT`).

## Gates: alias budget + edge/halo validation

The settle check is now a hard gate: the SR must deliver `E_sr/E_genuine >=
0.90` with `corr(fold) <= 0.15` and a native `alias fraction <= 10%`. This is
what turns the "is it real or aliased?" question into a build condition.

A new edge/halo check renders the real UpscaleCrop on a 2.0 step edge (plus a
0.9x-Nyquist grating) and measures the overshoot past the step and the flat
noise, at acutance floor 0 vs the shipped floor. It bounds the texture-acutance
fix:

| SR_ACUT_FLAT | step overshoot | flat noise | result |
|--------------|----------------|------------|--------|
| 0.00 | 0.023 | 0.0082 | FAIL (delivery 0.81 < 0.90) |
| **0.20 (shipped)** | 0.037 | 0.0099 | **PASS** |
| 0.35 | 0.047 | 0.0112 | FAIL (overshoot > +0.02) |

So the shipped floor adds ~0.7% of the step in overshoot and ~21% flat-noise
amplification, both bounded; a larger floor is rejected by the gate.

## Fusion-in-loop hook (WIP)

`--fusion` feeds the real `srlattice` output (the fused image the device
reconstruction consumes) into the reconstruction instead of the ideal raw
grid, so the `--reg` sweep scores below-Nyquist detail end-to-end. It shows
the right trend - genuine delivery 2.29 -> 1.46 -> 0.48 at reg 0/1/2 - but
the `srlattice` origin `(t+0.5)*exp-0.5` is not yet aligned with this bench's
input mapping (`t*exp`), so the numbers carry a sub-pixel offset
(corr(genuine) 0.16 at reg 0). Informational until the origin convention is
matched; not gated.

## The band is robust to smooth misregistration; random error is the killer

Adding a smooth spatially-varying registration ramp (`--regspatial A`, up to
A output px across the frame) barely touches the recovered band: at A = 2 the
per-region band correlation is 0.765/0.782/0.766 (L/C/R) and the final
reconstruction error 0.0809 - better than the uniform random case at the same
magnitude. A smooth warp is a local phase shift, which the band tolerates.

The degradation comes from *frame-to-frame random* registration error (the
`--reg` sweep: corr 0.766 -> 0.614 -> 0.357 at reg 0/1/2), which
phase-decorrelates the sub-pixel samples. That is also why a per-pixel
reliability gate has no obvious synthetic win here: the error is uniform in
effect, so gating is a global scale, and a crude per-site residual
(`--feedtrust`, |scene(pos+e)-scene(pos)|) over-suppresses (mean gate
0.5 -> 0.25, final 0.0809 -> 0.0869) because it also fires on ordinary
sub-pixel scene variation. Proving a *selective* gate needs the app's real
signed-residual trust (or a device dump), not this crude proxy.

Implication: the band's robustness is set by **alignment accuracy** (random
residual), not by a delivery gate. The refine sign fix was the big win;
further gains are alignment accuracy / more frames, not a new band gate.

## Fix: band below the raw Nyquist (drizzle-resolved near-Nyquist detail)

The reconstruction is a convex kernel, so it attenuates the last octave
below the raw Nyquist (settle: 0.80 of the genuine band). The drizzle's
multi-frame samples resolve that octave better, so lower the recovered
band's lower edge from the raw Nyquist (0.50) into it. Both srrecover and
the band layer move together (their extractors must match). Bench:

| SR_F_RAW_NYQ | full rms final | recovered corr | amplitude ratio | settle delivery |
|--------------|----------------|----------------|-----------------|-----------------|
| 0.50 (old)   | 0.0815         | 0.863          | 1.019           | 0.99            |
| 0.45         | 0.0777         | 0.871          | 1.042           | 1.07            |
| **0.42 (shipped)** | **0.0753** | **0.875** | 1.057         | 1.13            |
| 0.38         | 0.0723         | 0.881          | 1.078           | --              |

All PASS the alias budget (corr(fold) 0.09), and the reg sweep improves at
every level (reg 0 final 0.0806 -> 0.0747; reg 1 0.0806 -> 0.0786; reg 2
unchanged). The bench favors even lower, but the over-delivery ratio grows
with it, so 0.42 is the conservative choice pending device confirmation.

## Faithful trust model: the residual gate does not fix the band

`--feedtrust` now feeds the app's *actual* trust input - the grid base vs the
reg-shifted alter in the [0,1] packed domain - through srscatter's own 3x3
signed/abs-mean formula, instead of a proxy. It engages correctly but does
not help:

| reg | mean |packed residual| | noise band | mean gate | final rms |
|-----|---------------------------|------------|-----------|-----------|
| 0   | 0.0000                    | 0.05       | 0.605     | 0.0747    |
| 1   | 0.0175                    | 0.05       | 0.657     | 0.0786    |
| 2   | 0.0334 (p99 0.105)        | 0.05       | 0.596     | 0.0917    |

At reg 2 the gate drops (w ~ 0.7) but the reconstruction error is unchanged -
the band is wrong by a *phase* decorrelation of the sub-pixel samples, and an
intensity-residual gate suppresses signal and error together. So a per-pixel
reliability gate built on the existing residual cannot recover the loss; the
levers are registration accuracy or a new *phase-diversity* observable (how
evenly the deposit samples each output pixel's sub-pixel phase), not an
intensity gate.

## Lattice diversity predicts the above-Nyquist band (the settle cannot see it)

`--fixedjitter` (every frame one sub-pixel phase) and `--twophase` vary the
deposit's sub-pixel lattice diversity. The recovered *above-Nyquist* band
tracks it closely:

| lattice | recovered-band corr | amplitude ratio | final rms |
|---------|---------------------|-----------------|-----------|
| random  | 0.803               | 1.044           | 0.0747    |
| 2-phase | 0.519               | 0.669           | 0.0917    |
| fixed   | 0.401               | 1.070           | 0.0843    |

But the *below-Nyquist* settle is insensitive: at fixed phase it still reads
corr(genuine)=0.91, corr(fold)=0.08, delivery 0.98. The deficit is entirely
above the raw Nyquist, where the settle's alias budget does not look.

Important caveat: the band is still net-positive in rms even at zero
diversity (0.0843 < the 0.0933 reconstruction alone), and the existing
noise/coverage gate already partially suppresses there (mean gate 0.605 ->
0.393). So a diversity gate trades a little rms for removing the *structured*
(aliased) above-Nyquist content - a visual justification, not an rms one. The
app's synthetic jitter (`srJitter`) covers the static case; a per-pixel
phase-diversity gate would need a new accumulator (memory) and should be
driven by device evidence of low-diversity aliasing before shipping.

### SR_F_RAW_NYQ tuned to 0.38

Swept 0.50 -> 0.30 (both shaders + the mirror in lockstep). At reg 0 the band
lower edge improves monotonically to ~0.34, where it saturates (the 1.6
sigma clamp), but over-delivery grows (settle delivery 0.99 -> 1.27) and reg 2
turns slightly worse below 0.42. **0.38** is the balance: reg 0 final rms
0.0719 (vs 0.0747 at 0.42, 0.0806 at 0.50), reg 1 0.0768, recovered-band corr
0.818; settle delivery 1.21, corr(fold) 0.08 (no alias); edge/halo unchanged.

### Synthetic jitter (`srJitter`): keep it

Modelled the app's motion-gated golden-ratio dither (`--srjitter`,
`--static`). A truly static burst has no lattice diversity (recovered-band
corr 0.42); the app's default 0.25 is neutral in the bench (0.42) while a
larger 0.6 raises it to 0.61. With real motion present the dither is partly
gated but still lifts corr 0.818 -> 0.841 at 0.25.

The dither can only *add* sub-pixel diversity - it cannot remove detail or
add alias - and it is gated off where the frame already moved (the handheld
normal case). So removing it can only hurt static/tripod bursts and never
helps; there is no bench evidence it causes mesh/banding. Keep it.

## Fix: anti-aliased srlattice reduce (folded above-Nyquist content)

For integer expansion the fused-lattice reduce sampled a single output texel
(`c` is an integer, so the "bilinear" gather collapsed to a point sample).
That passes the above-Nyquist drizzle content through into the raw grid,
folding it into the below-Nyquist band the reconstruction consumes.
Measured against an ideal (sinc) reduce: `corr(lattice, analytic low)` 0.670
vs 0.964, `E_lat/E_low` 1.35 vs 0.92, fusion reconstruction final rms 0.0961
vs 0.0878. The shader now area-averages the n x n output texels covering each
raw pixel (the correct anti-aliased resample; the above-Nyquist SR detail is
delivered separately by the recovered band). After the fix: 0.0878 (== the
ideal 0.0869), corr(lattice, low) 0.964.

## Frame weighting / trust re-tune: already covered, not a lever

`--regmodel variable` gives each frame a different registration magnitude
(some sharp, some not). The faithful per-site trust already down-weights the
bad frames: recovered-band corr 0.469 -> 0.481, mean gate 0.616 -> 0.598
(18 frames, reg 2). That ~2.5% is the whole headroom - the residual is a
*phase* decorrelation, not an intensity mismatch, so no amount of trust
re-tuning recovers it. Frame weighting is therefore redundant with the
existing per-site trust; the remaining handheld lever is structural
(rolling-shutter / per-row motion), not weighting.

## High frame counts: healthy, and a further band extension

Frames sweep (scatter, reg 0/1): recovered-band corr 0.724 / 0.474 at 8 -> 0.872
/ 0.803 at 40 -> 0.884 / 0.827 at 90, mean gate 0.54 -> 0.81. No count
inversion; detail saturates ~40-60 frames. The reconstruction kernel is
optimal at every count (sigmaScale 0.55; sharper is worse), and gate/gain
only scale the band.

The one lever left is the band's lower edge: it kept improving to the 1.6
sigma clamp, so the clamp was the binding limit, not the optimum. Raising it
(1.6 -> 1.8) with `SR_F_RAW_NYQ 0.35` extends the band to a measured ratio of
1.095 (within the 1.10 sanity bound):

| | reg 0 | reg 1 | fusion 60f |
|---|-------|-------|-----------|
| 0.38 / 1.6 (old) | 0.0719 | 0.0768 | 0.0839 |
| **0.35 / 1.8 (new)** | **0.0699** | **0.0757** | **0.0803** |

No alias (corr(fold) 0.08), edge/halo unchanged; the high-frame-count gain
is the larger one.

## Fix: remove the acutance gate floor (artifact source)

The texture-acutance gate floor (`SR_ACUT_FLAT`, shipped 0.20) was the source
of the "tiny color specks / small highlight grids". A new flat/highlight check
(flat + bright highlight, with a *spatially varying* kernel map - a constant
map cannot grid) renders the real UpscaleCrop at floor 0 vs the floor and
measures the grid at the map pitch, the speck p99.9, and the highlight
structure:

| floor | grid | flat sd | highlight sd | speck p99.9 |
|-------|------|---------|--------------|-------------|
| 0.00 (shipped) | 0.00175 | 0.00307 | 0.0033 | 0.01123 |
| 0.20 | 0.00226 (+29%) | 0.00373 (+21%) | 0.0182 (+450%) | 0.01416 (+26%) |

The floor applied the unsharp in *isotropic* kernels - exactly flats and
highlights - amplifying the kernel-map grid and noise there. It is also no
longer needed: the recovered band's lower edge now extends below the raw
Nyquist (`SR_F_RAW_NYQ 0.35`), which delivers the near-Nyquist texture the
floor was compensating for (settle genuine delivery is already 1.10 at floor
0). Default is now 0 = edge-only acutance; the flat/highlight check is a gate
(regression at 0.20 fails).

## Periodic-texture (grating) target + spurious-grid / moire metrics

Added `--scene grating` (strong gratings straddling the raw Nyquist, several
orientations - a roof-shingle analog) and two metrics: a low-frequency beat
(`moire`, interior) and spurious HF error above the scene's top frequency
(`grid`). `--anisomap` gives elongated (edge) kernels so the elongation
acutance actually fires (a constant isotropic map leaves its gate at 0).

Findings on the recent 10-04 device samples (2x shingle cross-hatch):
- the mechanism is **near-Nyquist periodic content**: the below-Nyquist
  reconstruction dominates, and the edge-gated acutance adds a secondary
  +35% spurious HF on the grating (vs +4% on broadband) when edge kernels fire;
- the synthetic grating reconstructs *cleanly* in every bench path (analytic,
  deposit, fusion): the metric at 2x is dominated by the *attenuation* of the
  scene's own near-Nyquist content, not invented spurious. Lowering `ftop`
  makes it worse (it removes real band content).

So the exact device cross-hatch is **not reproducible synthetically** - it
depends on the real KernelNet map and the real fused lattice. Reproducing and
targeting it needs a device dump (real KernelsMap + basePacked/alterPacked +
atlas) through a `--replay` path. The grating target + metrics are the
protection groundwork and the gate once a reproducing scene exists.

## Output-grid drizzle reconstruction (architecture fix for >raw-Nyquist content)

The raw-grid lattice cannot represent content above the raw Nyquist: it folds
it. On the device dump the roof shingles (~0.9 cyc/raw) fold to a ~0.09 cyc/raw
moire, which the post then renders. The output-grid drizzle represents them
directly - reconstructing from it (depV/depW + CFA comb) removes the moire and
renders the shingles cleanly (validated on the dump, both offline and through
the real anisoupscale at 1:1).

Implemented: ESD4D runs srlattice at srFullTarget (the drizzle, with a
raw-grid fallback on allocation failure) and exports it; SRPreResolve adopts
an output-sized lattice as the working image, so UpscaleCrop runs at zoom 1
(its input equals the target). The pre-aniso raw-grid injection and the band
layer become no-ops in that path. Needs device validation.

## Drizzle compose (fixes the green) + R32F drizzle (fixes the memory)

The first output-grid attempt adopted the drizzle texture directly as the
post's working image. That texture is (luma, wEff, ...), so as RGB it read
(luma, ~2.2, 0) - a solid green - and it carries no chroma.

Fix: srpre/drizzlecompose builds the output-grid RGB the reconstruction
consumes - luma from the drizzle (black-corrected, holes fall back to the
crop), chroma from the demosaiced crop (bilinear upsample). SRPreResolve runs
it and sets the working image; UpscaleCrop then runs at zoom 1.

Memory: the drizzle is exported R32F (luma only) via merge/srlattice1 - half
of the RGBA16F lattice (r16f is not an ES 3.1 image load/store format). The
raw-grid RGBA16F path is unchanged and is the allocation fallback.

New compose_bench gate: composes a synthetic crop + drizzle and checks the
output luma equals the drizzle, the chroma equals the crop and there is no
green excess - the regression gate for this bug.

## Synthetic burst bench (burst_bench.py) and the drizzle band-layer grid

The device showed a "pixelated grid" with SR only, on edges and elsewhere.
drizzle_recon_bench could not see it because it builds a noiseless, dense,
single-sample-per-site drizzle - a fixture that cannot fail. burst_bench.py
runs the real chain end to end on a synthetic handheld burst instead:

    synthetic CFA frames (global motion, aperture MTF, per-site noise)
      -> real merge/srluma per-site luma (CFA-phase interpolation)
      -> real merge/srscatter deposit (holes / weights)   [output grid]
      -> real merge/srlattice1 drizzle
      -> real srpre/drizzlecompose (luma + crop chroma)
      -> real upscalecrop/anisoupscale at zoom 1
      -> real merge/srrecover + srpre/band.glsl (mode probe)
      -> metrics against the analytic ground truth

Metrics: deposit weight histogram, artifact HF residual, the residual in the
0.42-0.5 cyc/out lattice octave, residual by coverage quartile, and above-raw-
Nyquist delivery against the aperture-filtered truth (the fair reference: the
samples are aperture-integrated). `--bandmode replace|deconv` runs the real
band shader both ways; `--splatbox/--splatgauss` simulate a wider deposit
droplet; `--kmap const|edge|small` the KernelNet map; `--band off` ablates.

Measured (ramp scene: no HF content, so every HF residual is invented):

    band mode   artifactHF   artTop(0.42-0.5)   delivery (tones)
    off         0.0013       0.0013            0.37x
    replace     0.0073       0.0032            1.29x   <- the shipped bug
    deconv      0.0028       0.0017            0.52x   <- shipped fix
    Disabled    0.0052       -                 1.05x (alias, not detail)

The replace mode re-injects the deposit residue: its stored band is the DoG of
the RAW deposit while bandAniso is the DoG of the now-smoothed reconstruction,
so `gate*(rec - bandAniso)` adds back exactly the splat lattice the
reconstruction removed (gate mean 0.55, p90 0.97 in the bench) - the
pixelated grid. This path was live on device even after SRBandApply.Run was
made to skip the drizzle path, because the SR-only tiled head produce
(TileDriver.runHeadProduce) calls prepare()/renderTile() directly.

Fix: srpre/band.glsl gains srBandMode. On the output-grid drizzle path
(SRBandApply sets mode 1 from PostPipeline.srDrizzlePath) the reconstruction's
OWN band is deconvolved by the recovery's modeled gain -
`aniso + gate*(gain-1)*DoG(aniso)` - instead of being replaced by the raw
deposit's band. The gate still comes from the recovery's noise/coverage model.
Result: delivery 0.37x -> 0.52x (comb 0: 0.71x) while the no-HF artifact stays
below the Disabled reference (0.0028 < 0.0052). The `replace` probe FAILs the
burst_bench gates, the deconv path PASSes.

Real-dump corroboration (srsamples/srdump through --replay with
SR_REPLAY_BANDMODE): on the same crop, |replace-off| carries 3x the
high-frequency residue of |deconv-off| (top octave 0.074 vs 0.052 in 8-bit
levels; full-image std 2.78 vs 0.81), i.e. the replacement re-adds the
deposit's removed structure on real data too.

Structured kernel-map cases: --kmap blocky (random sigmas per map texel) and
--kmap blockedge (alternating elongated kernels) exercise the raw/4 map grid
(every map texel spans 8 output px at 2x). The deconv band stays below the
Disabled reference there too (0.0030 < 0.0052) while replace fails (0.0073).

Delivery correlation (tones, band off, vs the aperture-filtered truth):
SR corr 0.53-0.58 at 0.25-0.32 cyc/out but ~0.07 at 0.32-0.5, roughly
independent of the frame count (9 vs 40). The low/mid band is real but
aperture-limited; above ~0.32 cyc/out the samples carry almost nothing
(1-raw-px aperture sinc falls to 0.06 at the raw Nyquist ratio), so the
drizzle's normalized local average renders that band as a moire of the
sampling lattice - false detail. The replacement band amplified it directly
(the grid); the deconvolution boosts the smoothed version under the
recovery's noise/coverage gate and stays below the Disabled reference on the
no-HF scene. A true inverse of the irregular sampling (rather than a local
average) is the remaining improvement for the top band.

## Burst bench, second wave: trust, clip, gains, real map, grid metrics

The first fixture missed the device's structural mechanisms. Added:

- `--alignerr/--errfield`: per-cell residual alignment error (the app's
  SR_TILE_AL = 16 raw px grid, smooth/ramp/both), fed BOTH to the deposit
  position (atlas warp) and to the real trust gate through the packed
  diff/base textures, so srscatter's own 3x3 signed-mean formula decides the
  per-site weight. The gate barely fires on broadband texture (the signed mean
  cancels dipoles) but does drop weights on coherent gradients, as designed.
- `--gainerr`: per-frame photometric mismatch. 10% mismatch drives the
  sample-pitch autocorrelation of |SR-Disabled| to 0.68 - the metric sees a
  lattice; at realistic 1-3% it stays ~0.15.
- `--clipatten/--dc`: the app's `srClipAtten = 0.7` default. Near-clipped
  sites keep 30% weight (validated: mean deposit weight 1.25 -> 0.81 at
  dc=0.95). Highlights are base-dominated and under-averaged on device.
- `--kmap real`: loads the dump's actual KernelNet map. Measured at 1:1:
  s1 0.43..1.52, s2 0.23..0.53; after x8x0.55 rescale every axis pins at the
  1.0 output-px cap, so the map is isotropic, elongation = 1 and the
  acutance gate = 0. The KernelNet anisotropy is neutralized on the drizzle
  path - a real structural weakness (physical-scale clamps: min/max x exp).
- `--covadapt`: prototype of the coverage-adaptive kernel (composed alpha =
  deposit coverage). On the fixture it only changes low-coverage areas.
- Grid metrics: |SR - aligned Disabled| power at the lattice octave
  (0.42-0.5 cyc/out), at the raw/4 map pitch (0.125 at 2x), and the
  sample-pitch autocorrelation (lags (0,exp),(exp,0),(exp,exp)).

Result: with the real map, trust/misregistration, gains, clip attenuation,
CFA luma, chroma, jitter models and 9-40 frames at exp 2-3, the synthetic
chain stays at or below the Disabled reference on every artifact metric. No
grid reproduces data-side; the remaining suspects are the device render path
(R32F drizzle store / shared handoff / tiled head produce) or a build without
the band-deconv commit.

Measured fidelity levers (bench):
- SR_CFA_COMB 0.4 -> 0 costs real SR content: tones delivery 0.52 -> 0.71
  (band deconv, kmap edge); under clip conditions (dc 0.95, clipatten 0.7)
  the band corr rises 0.34 -> 0.57 and delivery 0.44 -> 0.59. The comb is a
  ±2-raw-px low-pass; its benefit is the CFA-phase bead suppression in
  highlights. srlattice1 now uses a coverage-weighted comb (exact weighted
  mean from srGather's sum(v*w), sum(w), faded where neighbours carry less
  coverage than the centre) so it cannot drag in hole zeros or thin areas;
  default weight unchanged pending a device sweep (SR_CFA_COMB 0.4/0.2/0).
- Coverage is discarded at the drizzle (luma only), so the reconstruction
  cannot widen where the deposit is thin. Carrying w (RG32F drizzle + composed
  alpha + a guarded anisoupscale term) is the next structural improvement.

Band error against the aperture truth (the fair reference, since the samples
are aperture-integrated): the deconv mode is neutral on tones (0.0033 -> 0.0033
at 9f, 0.0033 -> 0.0032 at 40f) and 3% worse on the edge scene (0.0059 ->
0.0062). It is not a large fidelity win; its value is that it avoids the
replace mode's re-injected deposit lattice (5.6x artifact on the no-HF scene).
The genuine above-raw-Nyquist content is confined to ~0.25-0.32 cyc/out
(corr 0.53-0.61); above that the local-average drizzle renders moire.

Decision on the comb (SR_CFA_COMB): dropped to 0 on the output-grid drizzle
(srlattice1), kept at 0.4 in the raw-grid fallback (srlattice). The beads were
validated on the raw-grid lattice, where a fixed class neighbourhood keeps the
CFA phase; the drizzle fuses sub-pixel-diverse samples across classes, so the
phase already averages down with frames (0.25 cyc/out excess: 1.82 -> 1.22 on
edge, 1.19 -> 0.69 on tones from 1 to 9 frames) while the comb costs ~27% of
the delivered above-raw-Nyquist band (delivery 0.52 -> 0.71 without it). If
dotted highlight edges return on device, re-enable by raising the define (the
coverage-weighted comb is still in place); a class-consistent srluma is the
proper fix.

## Edge morphology: staircase, rise, overshoot, and a fair Disabled reference

New in burst_bench: `--scene diag` (pure straight 31-degree step) and
`edge_metrics` measure, against the truth, the aligned Disabled and the SR:
- staircase = RMS deviation of the sub-pixel edge position from its best-fit
  line (offset/slope removed; jaggies show at the sample pitch);
- rise10-90 = edge transition width across the normal;
- overshoot = max excursion beyond the step's plateau levels.

`--disabled kern` runs the real anisoupscale in the legacy geometry (zoom=exp
on the raw crop, same real map, un-boosted acutance) as the Disabled
reference instead of the old (unfairly soft) bicubic proxy.

Measured (diag, kmap real, 9 frames): SR staircase 0.093 px vs Disabled 0.304
vs truth 0.020; rise SR 3.28 vs Disabled 5.85 vs truth 2.54; overshoot SR 6.1%
vs Disabled 0.6%. The SR drizzle path beats the fair Disabled on edges; no
aliasing regression reproduces. Wider deposit droplets (--splatbox) and the
physical-scale sigma clamps both made edges worse, so neither is a fix.

The edge metric did catch one real defect: the drizzle band deconvolution
doubled the edge overshoot (6.1% -> 11.5%) and collapsed the 10-90 rise at 40
frames (3.3 -> 6.9 px). Fixed by Weber-capping the deconv correction like the
acutance (0.01 of the local level): overshoot 7.1-7.6%, rise 3.2, and the band
correlation improved (tones corr 0.48 -> 0.53; diag 0.25-0.32 corr 0.77).

## Edge/gradient follow-up: srluma overshoot, gradient banding

The diag edge exposed the root of the beaded/ringed edges: with ideal samples
(--cfa off) the SR edge overshoot is 0.2%, with the real srluma it is 6.1% -
srluma's cross-channel correction (ownCorr) is unbounded and overshoots at
edges. srluma now Weber-caps it at 12% of the site level with a soft knee
(tanh): overshoot 3.3%, rise10-90 3.72 px (still sharper than the fair
Disabled's 5.85), staircase 0.109 px (Disabled 0.304), tones band corr 0.47
(0.25-0.32: 0.65). Texture corrections sit far below the cap and pass.

Gradient scene (`--scene grad`, a linear ramp) + banding metric: the
autocorrelation of the high-passed residual along the gradient direction.
Measured (kmap real): SR banding 0.29-0.34, hf 0.0013 vs Disabled 0.51, hf
0.0034 - SR gradients are cleaner than Disabled, and frame count does not
change it. The band deconv adds ~0.0008 hf on smooth content (0.0013 ->
0.0021) but stays below Disabled.

Prototypes kept in the bench: `--elongcap` (let the major kernel axis extend
past the cap, restoring the KernelNet elongation on the 1:1 path: corr
0.35 -> 0.44 on diag but overshoot doubles and tones corr drops - not
shipped) and `--corrcap` (the srluma cap above, shipped at 0.12).

## P0: frequency-split band deconvolution (shipped)

Sweeping the deconv knobs (new --gainmax/--gatek/--fnyq/--ftop/--gsplit) showed
the shipped uniform gain 2.2 was boosting non-correlated content: on tones the
0.25-0.5 cyc/out correlation *fell* as the gain rose (0.56 at gain 1.0 -> 0.47
at 2.2 at 9f; 0.67 -> 0.60 at 40f) while the L2 error against the aperture
truth stayed flat and edge overshoot / gradient HF grew (3.3% -> 5.0%; 0.0013
-> 0.0021). A stricter gate did not rescue it (the boost is moire, not noise).

New below-Nyquist metric (0.15-0.25 cyc/out) showed where the boost is real:
SR delivers amp 0.75x / corr 0.91 there, and the band boost restores it to
0.89-0.92x. So the band is split at the raw Nyquist: the genuine below part is
boosted at the full modeled gain, the mostly-moire above part is tapered
(SR_BAND_SPLIT 0.3). Measured at 9f/40f: top-band corr 0.47/0.60 -> 0.52/0.65,
below-Nyquist amp 0.84/0.86 (corr 0.92), edge overshoot 5.0% -> 4.4%, gradient
HF 0.0021 -> 0.0017, ramp gates PASS. Gain 2.2 with the split beats both
uniform gain 1.0 and uniform 1.4.

## P1 tested and rejected: coverage-adaptive kernel

The coverage-channel prototype (--covadapt, widen sigma where the deposit
coverage is low) was measured in low-coverage fixtures: 3-frame diag (delivery
0.48 -> 0.34, rise 3.78 -> 3.96, overshoot 3.1% -> 2.6%) and clip-attenuated
diag (0.36 -> 0.30, rise 3.86 -> 3.97, corr 0.46 -> 0.44). It trades sharpness
and delivery for a small overshoot reduction and no correlation gain, so the
RG32F coverage channel is not worth its structural risk. Kept as a bench
option; revisit only if a device case shows real low-coverage artifacts.

## P2: median same-colour estimate in srluma (shipped)

The Weber cap fixed the edge overshoot by bounding the symptom. Replacing the
mean of the four same-colour neighbours with their median fixes the cause: at a
step the mean pulls across the edge, so ownCorr = own - mean overshoots at the
CFA phase; the median ignores the far-side neighbour. Measured (diag, 9f):
overshoot 6.1% (mean, no cap) / 3.3% (mean + cap) -> 2.9% (median, no cap);
rise10-90 3.28 / 3.72 -> 2.97 px (Disabled 5.85); staircase 0.099 (Disabled
0.304). Tones: below-Nyquist delivery amp 0.84 -> 0.94x (corr 0.93), top-band
corr 0.52 -> 0.50 (mostly moire) with amp 0.39 -> 0.45; gradients resid
0.0272 -> 0.0239, banding 0.31. The 0.12 cap is removed (no longer needed),
and with the median the SR_BAND_SPLIT value is insensitive (0.0 vs 0.3 within
noise), so 0.3 stays. Ramp gates PASS, raw-grid bench PASS.

## P3: anisotropy restoration on the drizzle path (shipped)

The 1:1 sigma cap pinned both axes at 1.0 output px, so the KernelNet
elongation became 1 and the edge acutance gate never fired - the SR path was
an isotropic Gaussian while Disabled kept its anisotropic kernels. anisoupscale
gains srElongCap: the MINOR axis clamps at sigmaMaxPx, the MAJOR may extend to
sigmaMaxPx*srElongCap; UpscaleCrop sets 1.4 on the SR path, 1.0 elsewhere
(legacy unchanged).

Measured (kmap real, 9f): below-Nyquist delivery 0.94x -> 1.25x (corr 0.95),
0.32-0.5 correlation 0.18 -> 0.27, top-band corr 0.50 -> 0.54, edge rise
2.97 -> 2.77 px (truth 2.54, Disabled 5.85), staircase 0.099 -> 0.086. Cost:
the acutance now fires, so the dark-side undershoot rises 3.5% -> 7.9% of the
step - within its designed acutRel cap (4% of level). 1.8 over-elongates and
loses texture correlation (0.43); 1.4 is the knee. Ramp gates PASS, raw-grid
bench PASS.

## Regression harness + MTF50 + real-texture scene

baseline.py runs a fixed matrix (ramp/diag/grad/tones/photo x 9/40 frames, plus
a noise run) through burst_bench --metrics and freezes every metric to JSON;
--check re-runs and fails on any regression beyond 2% (correlations, MTF50,
delivery/amplitude regress on a decrease; artifacts/overshoot on an increase).
The current device-validated build is the frozen baseline.

burst_bench additions:
- MTF50 (coherent transfer vs the aperture truth, per radial bin) printed and
  in the metrics JSON.
- --scene photo: a real texture crop from the dump's lattice as the
  output-grid truth, sampled at the raw sites. First measurement: on real
  texture SR MTF50 0.223 vs Disabled 0.475 cyc/out, but SR is more correlated
  (0.81 vs 0.70) - Disabled's extra band is largely alias. A new provable
  target: raise the genuine real-texture amplitude without adding alias.
- --metrics <json>: machine-readable dump of the final report + gates.

## Phase 2.1: SR-only sigma scale (shipped) + the 40-frame deposit blur

The drizzle path's input is the fused deposit, already blurred by the 1-output-px
deposit splat, so the calibrated legacy sigma left it much softer than Disabled
on real texture. UpscaleCrop now applies SR_SIGMA_MULT = 0.45 to sigmaScale on
the SR path only (native/Disabled unchanged). Bench (real-texture crop):
MTF50 0.210 -> 0.410 cyc/out (Disabled 0.410), MTF area 0.543 -> 0.746
(Disabled 0.622), correlation 0.82 vs 0.71, band error 0.0167 vs Disabled
0.0202; tones delivery 0.50 -> 0.61, diag rise 2.79 -> 2.63 px, overshoot
within the acutance cap, ramp gates PASS. Also improved at 40 frames
(photo area 0.422 -> 0.547).

The robust MTF metric (smoothed highest crossing + MTF area) replaced the
first-crossing MTF50, which was bistable on the non-monotonic curve and
jittered with the fixed-point deposit atomics. The report now also prints the
deposit-only MTF, which exposed the next target: the drizzle's averaging PSF
widens with coverage (deposit MTF area 1.037 at 9f -> 0.667 at 40f), and
nothing deconvolves it - hence the remaining 40-frame gap (SR area 0.547 vs
Disabled 0.622). A coverage-aware deconvolution (more sharpening where the
deposit is dense) is the principled fix.

## Next target tested and rejected: coverage-aware deconvolution

The deposit MTF area drop with coverage (1.037 at 9f -> 0.667 at 40f) looked
like a coverage-dependent PSF to deconvolve, so --covsharp narrows the
reconstruction kernel where coverage is 1. It makes everything worse:
photo 9f area 0.746 -> 0.718 (0.75) / 0.677 (0.60) and corr 0.82 -> 0.78 /
0.76; photo 40f area 0.547 -> 0.532 / 0.508 and corr 0.89 -> 0.82 / 0.76;
band error rises above Disabled's. The 9f deposit area > 1 is aliasing that
correlates with the truth, while the 40f deposit is the more correct field
(area 0.667 at corr 0.89 vs Disabled 0.622 at 0.71) - so the "40-frame gap"
is Disabled's alias, not a missing deconvolution. The SR already beats
Disabled on the fair metrics at 40f (band error 0.0188 vs 0.0202).

The remaining genuine headroom is the above-0.32 cyc/out band (corr 0.56 at
40f, 0.29 at 9f): the local-average drizzle cannot invert the sampling there,
so it needs a true irregular-sampling inverse (or a further srluma gain), not
more gain or a sharper kernel.

## Next target tested and rejected: 8-point same-colour estimate

--luma8 replaces the 4-neighbour median with an 8-point trimmed mean
(cardinals + diagonals, same CFA parity). It raises amplitude/area (photo 9f
MTF50 0.410 -> 0.450, area 0.746 -> 0.953; tones delivery 0.61 -> 0.64) but
degrades the fair metrics: photo corr 0.82 -> 0.70, band error 0.0167 -> 0.0204
(worse than Disabled 0.0202), the raw/4 map-pitch grid doubles
(0.0175 -> 0.0410), diag overshoot 8.9% -> 9.7%, rise 2.63 -> 2.92. Rejected;
the shipped 4-neighbour median stays.

With coverage-aware sharpening and the 8-point estimate both rejected on the
fair metrics, the only remaining genuine headroom is the above-0.32 cyc/out
band (corr 0.56 at 40f, 0.29 at 9f). The local-average drizzle cannot invert
the sampling there: a true irregular-sampling inverse (a small regularized
least-squares solve per output pixel over the local sample set, likely a
compute pass) is the remaining R&D item, not more gain or a sharper kernel.

## High-zoom SR vs the pure anisotropic (srMaxExpand raised to 8x)

Bench (real-texture crop, 9 frames, dump KernelNet map), SR vs the legacy
aniso at equal crops:

    crop  SR MTF50 / area   aniso MTF50 / area   SR residual
    4x    0.205 / 0.315     0.050 / 0.092        0.101
    6x    0.170 / 0.281     0.070 / 0.099        0.148

At 6x the raw Nyquist is 0.083 cyc/out, yet the SR keeps coherent gain
0.64-0.73 at 0.11-0.15 - genuine above-Nyquist recovery, not interpolation;
the aniso is ~0 above 0.13. Sensor noise does not change it (0.01 -> identical
metrics; 9 frames average it). Widening the sigma clamps to the physical
envelope destroys the detail (6x MTF50 0.170 -> 0.050), so the sharp absolute
clamps stay. The trade is magnified raw grain (residual 0.101 -> 0.148), so the
default expansion was raised to 8x with the grain caveat documented.
