#!/usr/bin/env python3
"""
Calibration + validation for the FINAL estimator configuration:
  - progressive temporal blend (unchanged, BLEND_GRID prefix per frame count)
  - FIXED full 3x3 Gaussian (sigma_g = 1) difference operator on the quad
    luma, independent of frame count (the temporal kernel stays the only
    f-adaptive part)
  - two-pass adaptive gate fit (gate = 2.0), absolute-brightness domain
Outputs the Java NOISE_BLEND_VAR_STAT table and the scene demos.
Run: python3 fixed_mc.py
"""
import numpy as np

from mc import (fit_histogram, blur_gauss, gradient_scene, cropped, gauss_w,
                shift, BLEND_GRID, kernel_weights, H, W)
from luma_mc import make_wb_frames, WB

# Fixed full 3x3 Gaussian operator (sigma_g = 1), same for every frame count.
_G = np.array([gauss_w(dx, dy) for dx, dy in BLEND_GRID])
_G = _G / _G.sum()

S, O = 5e-3, 1e-5
SIG = float(np.sqrt(S * 0.4 + O))


def blend_luma(frames, f):
    slots, w, _ = kernel_weights(f)
    out = np.zeros(frames[0].shape[:2])
    for wi, (dx, dy), fr in zip(w, slots, frames):
        out += wi * shift(fr.mean(-1), dy, dx)
    return out


def luma_stat_fixed(luma):
    kmean = np.zeros_like(luma)
    for wq, (dx, dy) in zip(_G, BLEND_GRID):
        kmean += wq * shift(luma, dy, dx)
    var = np.abs(luma - kmean)
    br = np.sqrt(np.maximum(kmean, 0.0) + 1e-8)
    return br, var


def estimate(frames, f):
    return luma_stat_fixed(blend_luma(frames, f))


def measure_axis():
    sqrt_wb = np.sqrt(WB)
    b = {}
    for f in range(1, 10):
        stats = []
        for seed in range(3):
            rng = np.random.default_rng(1000 * f + seed)
            frames = [rng.standard_normal((H, W, 4)) * sqrt_wb[None, None, :]
                      for _ in range(f)]
            _, var = estimate(frames, f)
            stats.append(float(cropped(var).mean()))
        b[f] = float(np.mean(stats))
    return b


def end_to_end(b):
    err = {}
    for f in range(1, 10):
        ratios = []
        for seed in range(2):
            rng = np.random.default_rng(2000 * f + seed)
            frames = make_wb_frames(gradient_scene(), S, O, rng, f)
            br, var = estimate(frames, f)
            fs, _ = fit_histogram(br, var, f, b, gate=2.0)
            if fs is not None:
                ratios.append(fs / S)
        err[f] = float(np.mean(ratios))
    return err


def demo(table):
    rng0 = np.random.default_rng(11)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    base = np.clip(0.10 + 0.35 * (xx / W) + 0.08 * (yy / H), 0.02, 0.6)

    def mk_tex(std):
        t = 0.7 * blur_gauss(rng0.standard_normal((H, W)), 1.2) \
            + 0.3 * rng0.standard_normal((H, W))
        return t * std / t.std()

    scenes = [("flat", base)]
    for tstd, mix in ((3 * SIG, 0.35), (5 * SIG, 0.35), (3 * SIG, 1.0), (5 * SIG, 1.0)):
        t = mk_tex(tstd)
        if mix < 1:
            t = t * (rng0.random((H, W)) < mix)
        label = f"{'mix' + str(int(mix * 100)) + '%' if mix < 1 else 'all '} T={tstd / SIG:.0f}s"
        scenes.append((label, np.clip(base + t, 0.02, 0.95)))

    print(f"\n{'filter':>12}" + "".join(f"{n:>13}" for n, _ in scenes))
    for gname, cfg in (("gate2.0", dict(gate=2.0)), ("bins45", dict(var_cnt_max=45))):
        row = f"{gname:>12}"
        for label, scene in scenes:
            rng = np.random.default_rng(7)
            frames = make_wb_frames(scene, S, O, rng, 9)
            br, var = estimate(frames, 9)
            fs, fo = fit_histogram(br, var, 9, table, **cfg)
            sig = float(np.sqrt(max(fs, 0) * scene + max(fo, 0)).mean())
            truth = float(np.sqrt(S * scene + O).mean())
            row += f"{sig / truth:>13.2f}"
        print(row)


if __name__ == "__main__":
    axis = measure_axis()
    print("axis B:", " ".join(f"{axis[f]:.4f}" for f in range(1, 10)))
    err = end_to_end(axis)
    print("gate2 err:", " ".join(f"f={f}:{err[f]:.3f}" for f in sorted(err)))
    table = {f: axis[f] * np.sqrt(max(err[f], 1e-6)) for f in axis}
    print("Java table NOISE_BLEND_VAR_STAT (fixed operator, luma, gate2):")
    print("{ " + ", ".join(f"{table[f]:.5f}f" for f in range(1, 10)) + " }")
    demo(table)
