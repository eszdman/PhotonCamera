#!/usr/bin/env python3
"""
Experiment: f-matched difference operator - the spatial mean uses the SAME
progressive weights as the temporal blend kernel (largest symmetric prefix,
so planes are still annihilated: 3/5/7/9 taps for f=2..4/5..6/7..8/9).
Cascade detail leak becomes K*(1-K), bounded by 0.25 - frequencies the
temporal kernel did not blur are exactly where the operator is blind.

Compare against the fixed full-Gaussian operator (diff_mc.py).
Run: python3 matched_mc.py
"""
import numpy as np

from mc import (blend_frames, make_frames, fit_histogram, blur_gauss,
                gradient_scene, cropped, gauss_w, shift, BLEND_GRID, H, W)

# Pair-ordered grid so every prefix of odd length is symmetric.
_PAIR_ORDER = [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1),
               (1, 1), (-1, -1), (1, -1), (-1, 1)]


def op_kernel(f):
    """Symmetric-prefix operator taps for frame count f."""
    odd = f if f % 2 == 1 else f - 1
    n = max(3, min(9, odd))
    slots = _PAIR_ORDER[:n]
    g = np.array([gauss_w(dx, dy) for dx, dy in slots])
    g = g / g.sum()
    return slots, g, float(np.sqrt(1.0 + (g ** 2).sum() - 2.0 * g[0]))


def matched_diff_stat(img, f):
    slots, g, norm = op_kernel(f)
    kmean = np.zeros_like(img)
    for w, (dx, dy) in zip(g, slots):
        kmean += w * shift(img, dy, dx)
    d = np.abs(img - kmean) / norm
    five = [d[..., 0], d[..., 1], d[..., 2], d[..., 3], d.mean(-1)]
    var = np.median(np.stack(five), axis=0)
    br = np.sqrt(np.maximum(kmean.mean(-1), 0.0) + 1e-8)
    return br, var


def estimate_matched(frames, f):
    return matched_diff_stat(blend_frames(frames, f), f)


def measure_B():
    b = {}
    for f in range(1, 10):
        stats = []
        for seed in range(3):
            rng = np.random.default_rng(1000 * f + seed)
            frames = [rng.standard_normal((H, W, 4)) for _ in range(f)]
            _, var = estimate_matched(frames, f)
            stats.append(float(cropped(var).mean()))
        b[f] = float(np.mean(stats))
    return b


def end_to_end(b):
    err = {}
    for f in range(1, 10):
        ratios = []
        for seed in range(2):
            rng = np.random.default_rng(2000 * f + seed)
            scene = gradient_scene()
            S, O = 5e-3, 1e-5
            frames = make_frames(scene, S, O, rng, f)
            br, var = estimate_matched(frames, f)
            fit_s, _ = fit_histogram(br, var, f, b)
            if fit_s is not None:
                ratios.append(fit_s / S)
        err[f] = float(np.mean(ratios))
    return err


def demo(b_end):
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
        line = [f"matched {name}:"]
        for f in (2, 5, 9):
            frames = make_frames(scene, S, O, rng7, f, fpn=fpn_field)
            br, var = estimate_matched(frames, f)
            cells = []
            for cutoff in (63, 45, 25):
                fit_s, fit_o = fit_histogram(br, var, f, b_end, var_cnt_max=cutoff)
                if fit_s is None:
                    cells.append(f"cut{cutoff}:FAIL")
                    continue
                sig = float(np.sqrt(max(fit_s, 0) * scene + max(fit_o, 0)).mean())
                cells.append(f"cut{cutoff}:{sig / sig_true:.2f}")
            line.append(f"f={f}[" + " ".join(cells) + "]")
        print("  ".join(line))


if __name__ == "__main__":
    b = measure_B()
    print("B_lin:", " ".join(f"{b[f]:.4f}" for f in range(1, 10)))
    err = end_to_end(b)
    print("scale err:", " ".join(f"f={f}:{err[f]:.3f}" for f in sorted(err)))
    b_end = {f: b[f] * np.sqrt(err[f]) for f in err}
    demo(b_end)
