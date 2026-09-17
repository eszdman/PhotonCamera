#!/usr/bin/env python3
"""
Experiment: brightness-only (luma) estimation - the f-matched difference
operator applied to the quad luma (mean of the 4 packed Bayer channels)
instead of per-channel with median5. Two claimed advantages over the shipped
per-channel statistic, both caused by real Bayer data that the earlier
monochrome synthetic scenes hid:

  1. chroma cancellation: color edges/texture are per-channel steps with
     opposite signs; they cancel exactly in the luma mean but leak through
     per-channel differencing + median5.
  2. white-point invariance: sigma_luma^2 = sum_c(S*I_c + O)/16 = S*b + O
     with b the quad-mean brightness - the channel weights cancel
     identically, for ANY white point. The per-channel median5 statistic has
     no such identity (its calibration depends on the channel weighting).

Scenes here use pre-WB channel weights (R,G,G,B = 0.45,1,1,0.30) plus
chroma texture and color-edge cases.
Run: python3 luma_mc.py
"""
import numpy as np

from mc import (fit_histogram, blur_gauss, gradient_scene, cropped, gauss_w,
                shift, H, W)
from matched_mc import op_kernel, _PAIR_ORDER  # noqa: F401
from mc import BLEND_GRID, kernel_weights

WB = np.array([0.45, 1.0, 1.0, 0.30])  # pre-WB channel weights (RGGB-ish)


def blend_luma(frames, f):
    slots, w, _ = kernel_weights(f)
    out = np.zeros(frames[0].shape[:2])
    for wi, (dx, dy), fr in zip(w, slots, frames):
        out += wi * shift(fr.mean(-1), dy, dx)
    return out


def blend_vec(frames, f):
    slots, w, _ = kernel_weights(f)
    out = np.zeros_like(frames[0])
    for wi, (dx, dy), fr in zip(w, slots, frames):
        out += wi * shift(fr, dy, dx)
    return out


def luma_stat(luma_blend, f):
    slots, g, _ = op_kernel(f)
    kmean = np.zeros_like(luma_blend)
    for wq, (dx, dy) in zip(g, slots):
        kmean += wq * shift(luma_blend, dy, dx)
    var = np.abs(luma_blend - kmean)
    br = np.sqrt(np.maximum(kmean, 0.0) + 1e-8)
    return br, var


def matched_stat(vec_blend, f):
    slots, g, _ = op_kernel(f)
    kmean = np.zeros_like(vec_blend)
    for wq, (dx, dy) in zip(g, slots):
        kmean += wq * shift(vec_blend, dy, dx)
    d = np.abs(vec_blend - kmean)
    five = [d[..., 0], d[..., 1], d[..., 2], d[..., 3], d.mean(-1)]
    var = np.median(np.stack(five), axis=0)
    br = np.sqrt(np.maximum(kmean.mean(-1), 0.0) + 1e-8)
    return br, var


def make_wb_frames(scene, S, O, rng, f, tex_ch=None, handshake=2):
    """Frames with pre-WB channel weights: I_c = scene*WB_c + tex_c,
    per-channel noise sigma_c = sqrt(S*I_c + O)."""
    tex_ch = np.zeros((H, W, 4)) if tex_ch is None else tex_ch
    frames = []
    for _ in range(f):
        hx, hy = rng.integers(-handshake, handshake + 1, 2)
        sc = shift(scene, hy, hx)
        tch = shift(tex_ch, hy, hx)
        I_c = np.clip(sc[..., None] * WB[None, None, :] + tch, 0.0, 1.0)
        sigma = np.sqrt(S * I_c + O)
        frames.append(np.clip(I_c + rng.standard_normal((H, W, 4)) * sigma, 0.0, 1.0))
    return frames


def measure_B(mode):
    b = {}
    sqrt_wb = np.sqrt(WB)  # shot-dominated noise scales with sqrt(channel)
    for f in range(1, 10):
        stats = []
        for seed in range(3):
            rng = np.random.default_rng(1000 * f + seed)
            frames = [rng.standard_normal((H, W, 4)) * sqrt_wb[None, None, :] for _ in range(f)]
            if mode == "luma":
                br, var = luma_stat(blend_luma(frames, f), f)
            else:
                br, var = matched_stat(blend_vec(frames, f), f)
            stats.append(float(cropped(var).mean()))
        b[f] = float(np.mean(stats))
    return b


def end_to_end(b, mode):
    err = {}
    for f in range(1, 10):
        ratios = []
        for seed in range(2):
            rng = np.random.default_rng(2000 * f + seed)
            scene = gradient_scene()
            S, O = 5e-3, 1e-5
            frames = make_wb_frames(scene, S, O, rng, f)
            if mode == "luma":
                br, var = luma_stat(blend_luma(frames, f), f)
            else:
                br, var = matched_stat(blend_vec(frames, f), f)
            fit_s, _ = fit_histogram(br, var, f, b)
            if fit_s is not None:
                ratios.append(fit_s / S)
        err[f] = float(np.mean(ratios))
    return err


def demo(b_luma_end, b_matched_end):
    S, O = 5e-3, 1e-5
    rng0 = np.random.default_rng(11)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    base = np.clip(0.15 + 0.45 * (xx / W) + 0.1 * (yy / H), 0.02, 0.9)

    def norm(t, std):
        return t * (std / t.std())

    lum_tex = norm(0.7 * blur_gauss(rng0.standard_normal((H, W)), 1.2)
                   + 0.3 * rng0.standard_normal((H, W)), 0.05)
    chroma_tex = norm(blur_gauss(rng0.standard_normal((H, W)), 1.2), 0.06)
    # color edges: smooth random field, R up / B down, luma flat
    edges = norm(blur_gauss(rng0.standard_normal((H, W)), 3.0), 0.10)

    def chroma_field(t, gmix=0.2):
        f4 = np.zeros((H, W, 4))
        f4[..., 0] = t          # R +
        f4[..., 3] = -t         # B -
        f4[..., 1] = gmix * t   # Gr
        f4[..., 2] = gmix * t   # Gb
        return f4

    cases = [
        ("wb flat", None),
        ("wb luma tex 0.05", lambda: np.broadcast_to(lum_tex[..., None] * WB[None, None, :], (H, W, 4)).copy()),
        ("chroma tex 0.06", lambda: chroma_field(chroma_tex)),
        ("color edges 0.10", lambda: chroma_field(edges, 0.0)),
    ]
    for name, texf in cases:
        tex = texf() if texf else None
        rng7 = np.random.default_rng(7)
        # truth: model in quad-mean brightness domain, evaluated on the
        # luma of the WB-weighted scene (equal-channel luma = scene*mean(WB))
        scene_eff = base * float(WB.mean()) if tex is None else None
        sig_true = float(np.sqrt(S * base * float(WB.mean()) + O).mean())
        line = [f"{name}:"]
        for f in (5, 9):
            frames = make_wb_frames(base, S, O, rng7, f, tex_ch=tex)
            cells = []
            for mode, btab, est in (("perCh", b_matched_end, None), ("luma", b_luma_end, None)):
                if mode == "luma":
                    br, var = luma_stat(blend_luma(frames, f), f)
                else:
                    br, var = matched_stat(blend_vec(frames, f), f)
                fit_s, fit_o = fit_histogram(br, var, f, btab, var_cnt_max=45)
                if fit_s is None:
                    cells.append(f"{mode}:FAIL")
                    continue
                # evaluate fitted sigma at the effective luma brightness
                sig = float(np.sqrt(max(fit_s, 0) * (base * float(WB.mean()))
                                    + max(fit_o, 0)).mean())
                cells.append(f"{mode}:{sig / sig_true:.2f}")
            line.append(f"f={f}[" + " ".join(cells) + "]")
        print("  ".join(line))


if __name__ == "__main__":
    bl = measure_B("luma")
    bm = measure_B("matched")
    print("B_luma:   ", " ".join(f"{bl[f]:.4f}" for f in range(1, 10)))
    print("B_perCh:  ", " ".join(f"{bm[f]:.4f}" for f in range(1, 10)))
    el = end_to_end(bl, "luma")
    em = end_to_end(bm, "matched")
    print("luma err:  ", " ".join(f"f={f}:{el[f]:.3f}" for f in sorted(el)))
    print("perCh err: ", " ".join(f"f={f}:{em[f]:.3f}" for f in sorted(em)))
    bl_end = {f: bl[f] * np.sqrt(el[f]) for f in el}
    bm_end = {f: bm[f] * np.sqrt(em[f]) for f in em}
    print("Java luma table:", "{ " + ", ".join(f"{bl_end[f]:.5f}f" for f in range(1, 10)) + " }")
    print()
    demo(bl_end, bm_end)
