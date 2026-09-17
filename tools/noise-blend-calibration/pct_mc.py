#!/usr/bin/env python3
"""
Experiment: realistic texture-to-noise ratios (T = 3/5/8 sigma, what real
low-ISO scenes look like - the earlier demos only tested T ~ 1.8 sigma and
hid the leak) and the CPU-side fix: per-brightness-row LOWER PERCENTILE by
count instead of the lowest-N-occupied-bins cutoff. The percentile picks the
flat-region texels of each brightness; the calibration (B table) is re-folded
at the same percentile so noise-only scenes still land on truth.
Run: python3 pct_mc.py
"""
import numpy as np

from mc import (fit_histogram, blur_gauss, gradient_scene, H, W)
from luma_mc import (blend_luma, luma_stat, make_wb_frames, measure_B)

S, O = 5e-3, 1e-5
SIG = float(np.sqrt(S * 0.4 + O))  # ~0.045, mid-scene sigma


def estimate(frames, f):
    return luma_stat(blend_luma(frames, f), f)


def fold_table(mode_cfg):
    """Re-fold the B table on noise-only gradient scenes for a given filter."""
    b = measure_B("luma")
    from luma_mc import WB
    errs = []
    for seed in range(2):
        rng = np.random.default_rng(2000 + seed)
        scene = gradient_scene()
        frames = make_wb_frames(scene, S, O, rng, 9)
        br, var = estimate(frames, 9)
        fit_s, _ = fit_histogram(br, var, 9, b, **mode_cfg)
        if fit_s is not None:
            errs.append(fit_s / S)
    err = float(np.mean(errs))
    return {f: b[f] * np.sqrt(err) for f in b}, err


def scene_with_texture(rng, tex_std, mixed=False):
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    base = np.clip(0.10 + 0.35 * (xx / W) + 0.08 * (yy / H), 0.02, 0.6)
    if tex_std == 0:
        return base, None
    tex = 0.7 * blur_gauss(rng.standard_normal((H, W)), 1.2) \
        + 0.3 * rng.standard_normal((H, W))
    tex *= tex_std / tex.std()
    if mixed:
        # 35% of texels textured, randomly interleaved so both populations
        # share the brightness rows (the realistic capture case).
        mask = (rng.random((H, W)) < 0.35).astype(float)
        tex = tex * mask
    return np.clip(base + tex, 0.02, 0.95), tex_std / SIG


if __name__ == "__main__":
    configs = [("bins45 (shipped)", dict(var_cnt_max=45)),
               ("trim50", dict(low_percent=50)),
               ("trim25", dict(low_percent=25)),
               ("trim10", dict(low_percent=10))]
    tables = {}
    for name, cfg in configs:
        tables[name], err = fold_table(cfg)
        print(f"{name}: fold err={err:.3f}")

    rng0 = np.random.default_rng(11)
    scenes = []
    for label, t_std, mixed in (("flat", 0.0, False),
                                 ("mix35% T=3sig", 3 * SIG, True),
                                 ("mix35% T=5sig", 5 * SIG, True),
                                 ("all T=3sig", 3 * SIG, False),
                                 ("all T=5sig", 5 * SIG, False)):
        scene, ratio = scene_with_texture(rng0, t_std, mixed)
        scenes.append((label, scene))

    print(f"\n{'filter':>16}", "  ".join(f"{n:>14}" for n, _ in scenes))
    for name, cfg in configs:
        btab = tables[name]
        row = [f"{name:>16}"]
        for label, scene in scenes:
            rng = np.random.default_rng(7)
            frames = make_wb_frames(scene, S, O, rng, 9)
            br, var = estimate(frames, 9)
            fit_s, fit_o = fit_histogram(br, var, 9, btab, **cfg)
            sig = float(np.sqrt(max(fit_s, 0) * scene + max(fit_o, 0)).mean())
            truth = float(np.sqrt(S * scene + O).mean())
            row.append(f"{sig / truth:>14.2f}")
        print("  ".join(row))
