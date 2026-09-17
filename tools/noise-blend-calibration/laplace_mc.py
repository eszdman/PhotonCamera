#!/usr/bin/env python3
"""
Experiment: replace noisehist's median-of-squared-deviations-from-median
(a local CONSTANT model) with a local LINEAR model residual - implemented as
the fixed 3x3 annihilator kernel (Immerkaer Laplacian [1,-2,1;-2,4,-2;1,-2,1],
which is exactly the center residual of a LSQ plane fit over the 3x3 window).
A pure ramp then contributes ~zero; only noise (and true 2nd-order structure)
remains. Per-texel value = median5(|L_c|/6) across the 4 packed channels.

Compares, through the same histogram + fit pipeline as mc.py:
  A baseline : blend + spatial kernel + median-chain statistic (mc.py)
  B laplace  : blend + spatial kernel + Laplacian statistic
  C laplace0 : blend + Laplacian, no spatial kernel (cheapest)
Run: python3 laplace_mc.py
"""
import numpy as np

from mc import (blend_frames, spatial_conv, make_frames, fit_histogram,
                blur_gauss, H, W, MARGIN, NUM_BINS)

LAP = np.array([[1, -2, 1], [-2, 4, -2], [1, -2, 1]], float)


def laplacian(img):
    p = np.pad(img, ((1, 1), (1, 1), (0, 0)))
    out = np.zeros_like(img)
    for di in range(3):
        for dj in range(3):
            out += LAP[di, dj] * p[di:di + H, dj:dj + W]
    return out


def laplace_stat(img):
    L = np.abs(laplacian(img)) / 6.0  # E[L^2] = 36 sigma^2 for white noise
    five = [L[..., 0], L[..., 1], L[..., 2], L[..., 3], L.mean(-1)]
    return np.median(np.stack(five), axis=0)


def estimate_laplace(frames, f, spatial):
    blend = blend_frames(frames, f)
    img = spatial_conv(blend, f) if spatial else blend
    var = laplace_stat(img)
    br = np.sqrt(np.maximum(img.mean(-1), 0.0) + 1e-8)
    return br, var


def measure_B(spatial):
    b = {}
    for f in range(1, 10):
        stats = []
        for seed in range(3):
            rng = np.random.default_rng(1000 * f + seed)
            frames = [rng.standard_normal((H, W, 4)) for _ in range(f)]
            _, var = estimate_laplace(frames, f, spatial)
            stats.append(float(cropped(var).mean()))
        b[f] = float(np.mean(stats))
    return b


def end_to_end(b, spatial):
    err = {}
    for f in (1, 2, 3, 5, 9):
        ratios = []
        for seed in range(2):
            rng = np.random.default_rng(2000 * f + seed)
            scene = gradient_scene()
            S, O = 5e-3, 1e-5
            frames = make_frames(scene, S, O, rng, f)
            br, var = estimate_laplace(frames, f, spatial)
            fit_s, _ = fit_histogram(br, var, f, b)
            if fit_s is not None:
                ratios.append(fit_s / S)
        err[f] = float(np.mean(ratios))
    return err


from mc import gradient_scene, cropped  # noqa: E402


def demo(b_end, spatial, label):
    S, O = 5e-3, 1e-5
    rng0 = np.random.default_rng(11)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    base = np.clip(0.15 + 0.45 * (xx / W) + 0.1 * (yy / H), 0.02, 0.9)

    def norm(t, std):
        return t * (std / t.std())

    tex_fine = norm(0.7 * blur_gauss(rng0.standard_normal((H, W)), 1.2)
                    + 0.3 * rng0.standard_normal((H, W)), 0.05)
    tex_coarse = norm(blur_gauss(rng0.standard_normal((H, W)), 2.5), 0.06)
    tex_strong = norm(0.7 * blur_gauss(rng0.standard_normal((H, W)), 1.2)
                      + 0.3 * rng0.standard_normal((H, W)), 0.08)
    fpn_std = 0.5 * float(np.sqrt(S * 0.4 + O))

    cases = [("flat", None, 0.0), ("fine tex 0.05", tex_fine, 0.0),
             ("coarse tex 0.06", tex_coarse, 0.0), ("strong tex 0.08", tex_strong, 0.0),
             ("pixel FPN", None, fpn_std)]
    for name, tex, fpn in cases:
        rng7 = np.random.default_rng(7)
        scene = base if tex is None else np.clip(base + tex, 0.02, 0.98)
        sig_true = float(np.sqrt(S * scene + O).mean())
        fpn_field = rng7.standard_normal((H, W, 4)) * fpn if fpn > 0 else None
        line = [f"{label} {name}:"]
        for f in (1, 5, 9):
            frames = make_frames(scene, S, O, rng7, f, fpn=fpn_field)
            br, var = estimate_laplace(frames, f, spatial)
            cells = []
            for cutoff in (45, 25, 12):
                fit_s, fit_o = fit_histogram(br, var, f, b_end, var_cnt_max=cutoff)
                if fit_s is None:
                    cells.append(f"cut{c}:FAIL")
                    continue
                sig = float(np.sqrt(max(fit_s, 0) * scene + max(fit_o, 0)).mean())
                cells.append(f"cut{cutoff}:{sig / sig_true:.2f}")
            line.append(f"f={f}[" + " ".join(cells) + "]")
        print("  ".join(line))


if __name__ == "__main__":
    for spatial in (True, False):
        b = measure_B(spatial)
        print(f"\n=== Laplacian statistic, spatial_kernel={'on' if spatial else 'off'} ===")
        print("B_lin:", " ".join(f"{b[f]:.4f}" for f in range(1, 10)))
        err = end_to_end(b, spatial)
        print("scale err:", " ".join(f"f={f}:{err[f]:.3f}" for f in sorted(err)))
        b_end = {f: b[f] * np.sqrt(err[f]) for f in err}
        demo(b_end, spatial, "laplace" + ("+" if spatial else "0"))
