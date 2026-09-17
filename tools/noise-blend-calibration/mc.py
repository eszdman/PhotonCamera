#!/usr/bin/env python3
"""
Monte-Carlo calibration for the ESD4D noise-blend noise estimator.

Replicates in numpy the exact chain that ESD4D runs for adaptive noise
estimation:

  1. temporal blend: blend(p) = sum_i w_i * frame_i(p + d_i), offsets on a
     3x3 grid assigned center-first so the kernel shape is progressive in the
     frame count (f=9 full Gaussian, f=5 plus, f=2 two-tap, ...). Scene
     detail is correlated across frames -> convolved with the kernel; noise
     is independent -> only drops by the known factor sum(w_i^2).
  2. spatial kernel: the SAME weights applied once more inside
     merge/noisehist.glsl (uniform kernel[9]) before the deviation
     statistic, suppressing surviving detail by K*K while noise drops by
     another known factor sum(k^2).
  3. shader statistic: per texel (br, var) as in noisehist.glsl -
     approximate 5x5 median (median9 of 9 overlapping 3x3 block medians),
     median of squared deviations the same way, median5 across
     {r,g,b,a,mean}; br = sqrt(quad mean of the median), var = sqrt(median5).
  4. binning: 64x64 (brightness, variance) histogram with the same scales
     (brightness 64*sqrt(3), variance 384 * B[f]/B[1] so bin occupancy is
     frame-count independent).
  5. fit: the Java per-brightness-row lowest-bins cutoff + weighted linear
     regression variance = S*brightness + O, variance rescaled by the
     calibration table.

Outputs the Java table (end-to-end calibrated) and suppression demos.
Run: python3 mc.py
"""
import numpy as np

# Must match ESD4D.BLEND_GRID: center first, then edges, then corners. Taking
# the first f slots gives the progressive kernel shapes (9 -> 3x3 Gaussian,
# 5 -> plus, 2 -> two-tap).
BLEND_GRID = [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)]
SIGMA_G = 1.0
H = W = 256
MARGIN = 6
NUM_BINS = 64
BRIGHT_SCALE = 64.0 * np.sqrt(3.0)
# Variance axis scale is frame-count dependent: varScale(f) = (NUM_BINS-1) /
# (B[f] * SIGMA_REF) so bin 63 always maps to sigma = SIGMA_REF regardless of
# the kernel, and bin resolution is ~2.4x-31x finer than the old fixed 384.
SIGMA_REF = 0.12


def gauss_w(dx, dy, s=SIGMA_G):
    return float(np.exp(-(dx * dx + dy * dy) / (2.0 * s * s)))


def kernel_weights(f):
    """Normalized progressive kernel for f frames + its sum(w^2)."""
    slots = BLEND_GRID[:f]
    g = np.array([gauss_w(dx, dy) for dx, dy in slots])
    w = g / g.sum()
    return slots, w, float((w ** 2).sum())


def shift(img, dy, dx):
    ys = np.clip(np.arange(H) - dy, 0, H - 1)
    xs = np.clip(np.arange(W) - dx, 0, W - 1)
    return img[np.ix_(ys, xs)]


def blend_frames(frames, f):
    slots, w, _ = kernel_weights(f)
    out = np.zeros_like(frames[0])
    for wi, (dx, dy), fr in zip(w, slots, frames):
        out += wi * shift(fr, dy, dx)
    return out


def spatial_conv(img, f):
    """The second, spatial application of the same kernel (noisehist.glsl)."""
    slots, w, _ = kernel_weights(f)
    out = np.zeros_like(img)
    for wi, (dx, dy) in zip(w, slots):
        out += wi * shift(img, dy, dx)
    return out


def approx_med5x5(img):
    """Shader's approximate 5x5 median: median9 of the 9 overlapping 3x3 block
    medians (top-left corners at window -2..0). img: (H, W, C)."""
    p = np.pad(img, ((2, 2), (2, 2), (0, 0)))
    blocks = []
    for bi in range(3):
        for bj in range(3):
            stack = [p[bi + k:bi + k + H, bj + l:bj + l + W]
                     for k in range(3) for l in range(3)]
            blocks.append(np.median(np.stack(stack), axis=0))
    return np.median(np.stack(blocks), axis=0)


def shader_br_var(img):
    """The noisehist.glsl per-texel (br, var) pair for an (H, W, 4) field."""
    med = approx_med5x5(img)
    sq = (img - med) ** 2
    v = approx_med5x5(sq)
    five = [v[..., 0], v[..., 1], v[..., 2], v[..., 3], v.mean(-1)]
    var = np.sqrt(np.median(np.stack(five), axis=0) + 1e-8)
    # Production texels are clamped to [0,1] so the median is non-negative;
    # guard the sim the same way against noise-only negative excursions.
    br = np.sqrt(np.maximum(np.mean(med, axis=-1), 0.0) + 1e-8)
    return br, var


def cropped(x):
    return x[MARGIN:-MARGIN, MARGIN:-MARGIN]


def estimate_field(frames, f):
    """Full GPU-side pipeline: temporal blend + spatial kernel + statistic."""
    return shader_br_var(spatial_conv(blend_frames(frames, f), f))


def make_frames(scene, S, O, rng, f, handshake=2, fpn=None):
    """Frames in normalized units: shifted scene + fixed FPN + iid noise with
    sigma(I) = sqrt(S*I + O), clamped to [0,1] like production merge00."""
    sigma = np.sqrt(S * scene + O)
    frames = []
    for _ in range(f):
        hx, hy = rng.integers(-handshake, handshake + 1, 2)
        sc = shift(scene, hy, hx)
        noise = rng.standard_normal((H, W, 4))
        fr = sc[..., None] + noise * sigma[..., None]
        if fpn is not None:
            fr = fr + fpn
        frames.append(np.clip(fr, 0.0, 1.0))
    return frames


def fit_histogram(br, var, f, calib_B, var_cnt_max=45, low_percent=None,
                  gate=None):
    """The Java CPU fit, replicated. Modes:
      - var_cnt_max: lowest-N-occupied-bins cutoff per row (legacy)
      - low_percent: trimmed mean, lowest P% of mass per row (partial weight)
      - gate: two-pass adaptive gate - fit once on all bins, then keep only
        bins with implied variance <= gate*(fitS*b + fitO) and refit.
        Scale-free: no per-threshold calibration, rejects texture and
        saturation-capped bins through the fitted noise model itself.
    """
    var_scale = (NUM_BINS - 1) / (calib_B[f] * SIGMA_REF)
    brBin = np.minimum(NUM_BINS - 1, (cropped(br) * BRIGHT_SCALE).astype(int)).ravel()
    varBin = np.minimum(NUM_BINS - 1, (cropped(var) * var_scale).astype(int)).ravel()
    hist = np.bincount(brBin * NUM_BINS + varBin, minlength=NUM_BINS * NUM_BINS).astype(float)
    hist = hist.reshape(NUM_BINS, NUM_BINS)
    if low_percent is not None:
        # Trimmed mean: per row, keep the lowest P% of texel mass with the
        # boundary bin weighted fractionally (a binary bin cut would sit in
        # the extreme lower tail - the |d| distribution has its mode at
        # bin 0 - and its response jumps between scenes).
        row_tot = hist.sum(axis=1, keepdims=True)
        budget = (low_percent / 100.0) * row_tot
        cum_ex = np.concatenate([np.zeros((NUM_BINS, 1)),
                                 np.cumsum(hist, axis=1)[:, :-1]], axis=1)
        allowed = np.clip(budget - cum_ex, 0.0, hist)

    def run_fit(weights):
        sw = swb = swv = swb2 = swbv = 0.0
        for bin_ in range(NUM_BINS - 1):  # Java skips the brightest bin
            var_cnt = 0
            for vin in range(NUM_BINS):
                count = hist[bin_, vin]
                if count <= 0:
                    continue
                if low_percent is not None:
                    count = allowed[bin_, vin]
                    if count <= 0:
                        continue
                else:
                    if (var_cnt >= 30 and vin == 63) or var_cnt > var_cnt_max:
                        continue
                var_cnt += 1
                if weights is not None and weights[bin_, vin] <= 0:
                    continue
                if weights is not None:
                    count = min(count, weights[bin_, vin])
                brightness = (((bin_ + 0.5) / BRIGHT_SCALE) ** 2)
                variance = ((vin + 0.5) / var_scale) ** 2 / calib_B[f] ** 2
                sw += count
                swb += count * brightness
                swv += count * variance
                swb2 += count * brightness * brightness
                swbv += count * brightness * variance
        denom = sw * swb2 - swb * swb
        if abs(denom) < 1e-20 or sw == 0:
            return None, None
        fit_s = (sw * swbv - swb * swv) / denom
        fit_o = (swv - fit_s * swb) / sw
        return fit_s, fit_o

    if gate is not None:
        fit_s, fit_o = run_fit(None)
        if fit_s is None:
            return None, None
        rows = np.arange(NUM_BINS)[:, None]
        model_var = gate * (max(fit_s, 1e-12)
                            * (((rows + 0.5) / BRIGHT_SCALE) ** 2) + max(fit_o, 0.0))
        implied = ((np.arange(NUM_BINS)[None, :] + 0.5) / var_scale) ** 2 / calib_B[f] ** 2
        weights = np.where(implied <= model_var, hist, 0.0)
        return run_fit(weights)
    return run_fit(None)


def measure_stat_const():
    """B_lin[f] = E[var_stat]/sigma for pure white noise through the whole
    two-stage kernel (used for the histogram variance-axis stretch and as the
    first-pass calibration)."""
    b_lin, sumw2 = {}, {}
    for f in range(1, 10):
        stats = []
        for seed in range(3):
            rng = np.random.default_rng(1000 * f + seed)
            frames = [rng.standard_normal((H, W, 4)) for _ in range(f)]
            _, var = estimate_field(frames, f)
            stats.append(float(cropped(var).mean()))
        b_lin[f] = float(np.mean(stats))
        _, _, sumw2[f] = kernel_weights(f)
    return b_lin, sumw2


def gradient_scene(rng_seed=0):
    """Near-black strip (so brightness bin 0 is occupied and the Java minBr
    rescale is the identity) + a brightness gradient spanning the range."""
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    scene = 0.62 * (xx / W) * (0.55 + 0.45 * yy / H)
    scene[:, :16] = 0.0
    return np.clip(scene, 0.0, 0.92)


def end_to_end(b_lin):
    """Full chain on noise-only gradient scenes; returns fit_S/true_S.
    Injected S is sized so the variance axis spans enough bins to bin
    meaningfully (mirrors what the on-device injection test must use)."""
    scale_err = {}
    for f in range(1, 10):
        ratios = []
        for seed in range(2):
            rng = np.random.default_rng(2000 * f + seed)
            scene = gradient_scene()
            S, O = 5e-3, 1e-5
            frames = make_frames(scene, S, O, rng, f)
            br, var = estimate_field(frames, f)
            fit_s, _ = fit_histogram(br, var, f, b_lin)
            if fit_s is not None:
                ratios.append(fit_s / S)
        scale_err[f] = float(np.mean(ratios))
    return scale_err


def blur_gauss(img, sigma):
    k = int(np.ceil(3 * sigma))
    x = np.arange(-k, k + 1)
    g = np.exp(-x ** 2 / (2 * sigma ** 2))
    g /= g.sum()
    for axis in (0, 1):
        img = np.apply_along_axis(lambda m: np.convolve(m, g, mode="same"), axis, img)
    return img


def demo(b_end):
    """Suppression demos: old single-frame path (f=1) vs progressive blends,
    across texture types and per-brightness-row cutoff settings."""
    S, O = 5e-3, 1e-5
    rng = np.random.default_rng(11)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    base = np.clip(0.15 + 0.45 * (xx / W) + 0.1 * (yy / H), 0.02, 0.9)

    def norm(t, std):
        return t * (std / t.std())

    tex_fine = norm(0.7 * blur_gauss(rng.standard_normal((H, W)), 1.2)
                    + 0.3 * rng.standard_normal((H, W)), 0.05)
    tex_coarse = norm(blur_gauss(rng.standard_normal((H, W)), 2.5), 0.06)
    tex_strong = norm(0.7 * blur_gauss(rng.standard_normal((H, W)), 1.2)
                      + 0.3 * rng.standard_normal((H, W)), 0.08)
    fpn_std = 0.5 * float(np.sqrt(S * 0.4 + O))

    cases = [
        ("flat", None, 0.0),
        ("fine texture std=0.05", tex_fine, 0.0),
        ("coarse texture std=0.06", tex_coarse, 0.0),
        ("strong texture std=0.08", tex_strong, 0.0),
        ("pixel FPN std=0.5*sigma", None, fpn_std),
    ]
    for name, tex, fpn in cases:
        rng7 = np.random.default_rng(7)
        scene = base if tex is None else np.clip(base + tex, 0.02, 0.98)
        sigma_mean = float(np.sqrt(S * scene + O).mean())
        fpn_field = rng7.standard_normal((H, W, 4)) * fpn if fpn > 0 else None
        print(f"{name}: true mean sigma={sigma_mean:.5f}")
        for f in (1, 2, 5, 9):
            frames = make_frames(scene, S, O, rng7, f, fpn=fpn_field)
            br, var = estimate_field(frames, f)
            ratios = []
            for cutoff in (45, 25, 12):
                fit_s, fit_o = fit_histogram(br, var, f, b_end, var_cnt_max=cutoff)
                if fit_s is None:
                    ratios.append(None)
                    continue
                sig = float(np.sqrt(max(fit_s, 0) * scene + max(fit_o, 0)).mean())
                ratios.append(sig / sigma_mean)
            label = "single(old)" if f == 1 else f"{f}-blend"
            cells = "  ".join(f"cut{c}:{r:.2f}" if r else f"cut{c}:FAIL"
                              for c, r in zip((45, 25, 12), ratios))
            print(f"  {label:>11}: {cells}")
        print()


if __name__ == "__main__":
    B_LIN, SUMW2 = measure_stat_const()
    print(f"{'f':>2} {'taps':>4} {'B_lin':>9} {'noise var factor':>16}")
    for f in range(1, 10):
        print(f"{f:>2} {f:>4} {B_LIN[f]:>9.5f} {SUMW2[f] ** 2:>16.5f}")

    scale_err = end_to_end(B_LIN)
    print("\nend-to-end noise-only scale error (fit S / true S):")
    print("  " + "  ".join(f"f={f}:{scale_err[f]:.3f}" for f in sorted(scale_err)))
    # Fold the residual into the final table so the fit lands on truth.
    B_END = {f: B_LIN[f] * np.sqrt(scale_err[f]) for f in B_LIN}
    print("\nJava table NOISE_BLEND_VAR_STAT (index = frame count, 1..9):")
    print("{ " + ", ".join(f"{B_END[f]:.5f}f" for f in range(1, 10)) + " }")

    rng = np.random.default_rng(11)
    print("\n--- suppression demos (calibrated end-to-end; lower is better) ---")
    demo(B_END)
