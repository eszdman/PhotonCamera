#!/usr/bin/env python3
"""
Real-image benchmark for Photon Camera's block-pyramid burst alignment.

Data: real Apple ProRAW DNGs (iPhone 12 Pro Max, linear Raw). The linear RGB
planes are re-mosaiced into a Bayer raw, then a burst pair is synthesized:
  - alter frame = homography warp of the base (hand-shake model: small
    rotation + translation + scale + perspective, no in-frame motion)
  - independent per-frame photon noise (sigma = sqrt(s*noiseS + noiseO))
  - sensor quantization to u16
Ground truth per alignment tile is the local homography displacement at the
tile center.

Pipeline is app-faithful:
  normalize.glsl (variants) -> histogram BL + normalizebl.glsl (unsharp)
  -> GLUtils.createPyramidStore (Catmull-Rom bicubic downscale, exactly
  replicating textureBicubicHardware at f=0.5 => [-1,9,9,-1]/16)
  -> the ACTUAL app align.glsl compute shader via moderngl with the Java
  loop's defines/uniforms (PyramidAlignment.Run).

Usage:
  ./align_bench_real.py                  # default config table
  ./align_bench_real.py --config old --scene proraw3:day
  ./align_bench_real.py --list
"""
import argparse
import os
import re
import sys
import time

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
APP_ROOT = os.path.join(HERE, "..", "..")
SHADER_PATH = os.path.join(APP_ROOT, "app", "src", "main", "assets",
                           "shaders", "alignment", "align.glsl")
# pre-robust-cost shader (plain SAD), exported with
#   git show cd7136d7^:app/src/main/assets/shaders/alignment/align.glsl
LEGACY_SHADER_PATH = os.path.join(HERE, "legacy_align.glsl")
DATA_DIR = os.path.join(HERE, "data")

WL = 4095.0  # ProRAW white level (12 bit)
CFA = np.array([[0, 1], [1, 2]], np.int32)  # RGGB: site (y%2, x%2) -> plane

# ---------------------------------------------------------------- raw loading

def load_proraw(path, max_side=2016):
    """ProRAW linear RGB planes in [0,1], optionally center-cropped so the
    long side is max_side (sensor px)."""
    import rawpy
    with rawpy.imread(path) as r:
        raw = r.raw_image.copy()  # (H, W, 4) u16, RGBG linear (copy: view dies with the handle)
    lin = raw[:, :, :3].astype(np.float32) / WL
    h, w = lin.shape[:2]
    if max_side and max(h, w) > max_side:
        s = max_side / max(h, w)
        ch, cw = int(h * s), int(w * s)
        y0, x0 = (h - ch) // 2, (w - cw) // 2
        lin = lin[y0:y0 + ch, x0:x0 + cw]
    return np.ascontiguousarray(lin)


# ------------------------------------------------------------------- warping

def random_homography(shape, seed, max_rot_deg=0.25, max_trans=10.0,
                      max_scale=0.0015, max_persp=2e-4):
    """H maps base sensor coords -> alter sensor coords (projective).
    Rotation/scale/translation act in px; the perspective term is defined in
    centered unit coords (|q|<=1 at the corners) so max_persp is a
    physically-scaled knob (2e-4 -> ~0.15 px at the frame corner)."""
    rng = np.random.default_rng(seed)
    h, w = shape
    cx, cy = w / 2, h / 2
    th = np.deg2rad(rng.uniform(-max_rot_deg, max_rot_deg))
    c, s = np.cos(th), np.sin(th)
    R = np.array([[c, -s], [s, c]])
    sc = 1.0 + rng.uniform(-max_scale, max_scale)
    t = rng.uniform(-max_trans, max_trans, 2)
    v = rng.uniform(-max_persp, max_persp, 2)
    # homography in centered unit coords, then map back to px
    Hn = np.array([[sc * R[0, 0], sc * R[0, 1], t[0] / cx],
                   [sc * R[1, 0], sc * R[1, 1], t[1] / cy],
                   [v[0], v[1], 1.0]], np.float64)
    C2 = np.array([[1 / cx, 0, -1], [0, 1 / cy, -1], [0, 0, 1]], np.float64)
    C1 = np.array([[cx, 0, cx], [0, cy, cy], [0, 0, 1]], np.float64)
    return C1 @ Hn @ C2


def apply_h(points, Hm):
    """points (..., 2) -> mapped (..., 2)."""
    p = np.concatenate([points, np.ones(points.shape[:-1] + (1,))], axis=-1)
    q = p @ Hm.T
    return q[..., :2] / q[..., 2:3]


def bilinear_sample(plane, xs, ys):
    """plane (H,W) float32, xs/ys float arrays same shape -> clamped bilinear."""
    h, w = plane.shape
    xs = np.clip(xs, 0.0, w - 1.001)
    ys = np.clip(ys, 0.0, h - 1.001)
    x0 = np.floor(xs).astype(np.int32)
    y0 = np.floor(ys).astype(np.int32)
    fx = xs - x0
    fy = ys - y0
    p00 = plane[y0, x0]
    p10 = plane[y0, x0 + 1]
    p01 = plane[y0 + 1, x0]
    p11 = plane[y0 + 1, x0 + 1]
    return (p00 * (1 - fx) * (1 - fy) + p10 * fx * (1 - fy)
            + p01 * (1 - fx) * fy + p11 * fx * fy)


def mosaic_frame(planes, Hm, noise_s, noise_o, rng, ident=False):
    """Render one raw frame (H, W) u16: mosaic of warped planes + photon noise.

    planes: (H, W, 3) clean linear scene in [0,1] (sensor-res).
    Hm: base->alter homography (identity for the base frame)."""
    h, w = planes.shape[:2]
    if ident:
        scene = planes
    else:
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        # inverse map: alter(p) = base(H^-1(p))
        dst = apply_h(np.stack([xx, yy], axis=-1), np.linalg.inv(Hm))
        scene = np.stack([bilinear_sample(planes[:, :, c], dst[..., 0], dst[..., 1])
                          for c in range(3)], axis=-1)
    cfa = CFA[np.arange(h)[:, None] % 2, np.arange(w)[None, :] % 2]
    site = np.take_along_axis(scene, cfa[..., None], axis=-1)[..., 0]
    var = site * noise_s * WL * WL + noise_o * WL * WL
    n = rng.standard_normal(site.shape).astype(np.float32) * np.sqrt(var)
    return np.rint(np.clip(site * WL + n, 0, WL)).astype(np.uint16)


def make_object_scenes(scene, obj, seed):
    """Two scene variants with a moving object: a textured patch pasted at
    position A in the base scene and at A+shift in the alter scene (the
    background itself is unwarpd here - the homography is applied later by
    mosaic_frame). The patch is a crop from a high-variance region elsewhere
    in the image, so it has realistic texture. Returns (scene_base,
    scene_alter); obj = dict(bbox=(x, y, w, h), shift=(dx, dy)) in sensor px."""
    h, w = scene.shape[:2]
    ox_, oy_, ow, oh = obj["bbox"]
    dx, dy = obj["shift"]
    rng = np.random.default_rng(seed + 999)
    # find a textured crop for the object: best std among random candidates
    best, best_std = None, -1.0
    for _ in range(24):
        cx = int(rng.integers(0, w - ow))
        cy = int(rng.integers(0, h - oh))
        # keep the source away from the destination so the patch is not
        # self-repeating at the same place
        if abs(cx - ox_) < ow and abs(cy - oy_) < oh:
            continue
        crop = scene[cy:cy + oh, cx:cx + ow]
        s = float(crop.std())
        if s > best_std:
            best_std, best = s, crop
    patch = best if best is not None else scene[oy_:oy_ + oh, ox_:ox_ + ow]
    base = scene.copy()
    base[oy_:oy_ + oh, ox_:ox_ + ow] = patch
    alter = scene.copy()
    x2, y2 = ox_ + dx, oy_ + dy
    # clip the pasted region to the frame
    sx0, sy0 = max(0, x2), max(0, y2)
    sx1, sy1 = min(w, x2 + ow), min(h, y2 + oh)
    alter[sy0:sy1, sx0:sx1] = patch[sy0 - y2:sy1 - y2, sx0 - x2:sx1 - x2]
    return base, alter


# ------------------------------------------------- normalize variants (numpy)

def normalize_pass(raw_u16, mode, sigma, black_level=0.0, exposure=1.0):
    """Faithful numpy port of normalize.glsl variants.
    Returns (H/2, W/2, 4) fp16 + effective noise sample count N_eff."""
    h, w = raw_u16.shape
    f = raw_u16.astype(np.float32)
    f = (f - black_level) / (WL - black_level)
    f = np.clip(f, 0.0, 1.0)
    h2, w2 = h // 2, w // 2
    quads = np.stack([f[0:h:2, 0:w:2], f[0:h:2, 1:w:2],
                      f[1:h:2, 0:w:2], f[1:h:2, 1:w:2]], axis=-1)[:h2, :w2]
    # pad by 4 quads on all sides (edge clamp): pad[4+dy, 4+dx] == quads[dy,dx]
    # (the uncentered old-box window offsets 0..3 need 3 extra at the end)
    pad = np.pad(quads, [(4, 4), (4, 4), (0, 0)], mode="edge")

    def box_window(off0, size):
        """sum/min/max over quad window offsets off0..off0+size (same both axes)."""
        acc = np.zeros_like(quads)
        mn = np.full_like(quads, 1e9)
        mx = np.full_like(quads, -1e9)
        for j in range(size):
            for i in range(size):
                q = pad[4 + off0 + j: 4 + off0 + j + h2, 4 + off0 + i: 4 + off0 + i + w2]
                acc += q
                mn = np.minimum(mn, q)
                mx = np.maximum(mx, q)
        return acc, mn, mx

    if mode == "oldbox":
        # shipped-for-years variant: plain 4x4 quad box, window offset (0..3)
        acc, _, _ = box_window(0, 4)
        out = acc / 16.0
        n_eff = 16.0
    elif mode == "trimbox":
        acc, mn, mx = box_window(0, 4)
        out = (acc - mn - mx) / 14.0
        n_eff = 14.0
    elif mode in ("gauss", "gauss_notrim"):
        # current PREFILTER 1 (or no-trim variant): separable 5-tap centered
        s2 = 2.0 * max(sigma, 0.05) ** 2
        wx = np.exp(-(np.arange(5) - 2.0) ** 2 / s2)
        wx /= wx.sum()
        sumw = np.zeros_like(quads)
        mn = np.full_like(quads, 1e9)
        mx = np.full_like(quads, -1e9)
        wmn = np.zeros_like(quads)
        wmx = np.zeros_like(quads)
        for j in range(5):
            for i in range(5):
                wgt = wx[i] * wx[j]
                q = pad[2 + j: 2 + j + h2, 2 + i: 2 + i + w2]
                sumw += wgt * q
                ismn = q < mn
                ismx = q > mx
                mn = np.where(ismn, q, mn)
                mx = np.where(ismx, q, mx)
                wmn = np.where(ismn, wgt, wmn)
                wmx = np.where(ismx, wgt, wmx)
        if mode == "gauss":
            out = (sumw - wmn * mn - wmx * mx) / (1.0 - wmn - wmx)
        else:
            out = sumw
        n_eff = 1.0 / float((wx ** 2).sum() ** 2)
    else:
        raise ValueError(mode)
    out = np.clip(out, 0.0, 1.0)
    out = np.clip(out * exposure, 0.0, 1.0)
    return out.astype(np.float16), n_eff


# ------------------------------------------------ GLHistogram BL + normalizebl

def estimate_black_level(norm_base):
    """30th-percentile-per-channel estimate like PyramidAlignment + GLHistogram
    (1024 bins, exposure 64, floor binning, stride-3 sampling)."""
    v = norm_base.astype(np.float32)[::3, ::3]  # resize=3
    bl = np.zeros(4, np.float32)
    for c in range(4):
        counts, _ = np.histogram(v[..., c], bins=1024, range=(0.0, 1.0))
        # bin index == floor(v * 1024)? GL: floor(v*64*1023) clamped to 1023
        # (v*65472). floor(v*1024) is within one bin of it; use GL exact:
        idx = np.clip((v[..., c] * (64.0 * 1023.0)).astype(np.int64), 0, 1023)
        counts = np.bincount(idx.ravel(), minlength=1024)
        cum = np.cumsum(counts)
        total = cum[-1]
        j = int(np.searchsorted(cum, total * 0.3))
        bl[c] = j / (1024 - 1.0) / 64.0
    return bl


def normalizebl(norm, black_level, sharpness=1.0):
    """normalizebl.glsl: unsharp mix + per-channel BL subtract, clamp."""
    p = norm.astype(np.float32)
    pad = np.pad(p, [(1, 1), (1, 1), (0, 0)], mode="edge")
    left = pad[1:-1, :-2]
    right = pad[1:-1, 2:]
    up = pad[:-2, 1:-1]
    down = pad[2:, 1:-1]
    lap = (left + right + up + down) - p * 3.0
    out = p * (1.0 - sharpness) + lap * sharpness
    out = np.clip((out - black_level) / (1.0 - black_level), 0.0, 1.0)
    return out.astype(np.float16)


# ------------------------------------------------------------- app pyramid

CR_W = np.array([-1.0, 9.0, 9.0, -1.0], np.float32) / 16.0  # Catmull-Rom f=.5

def catmull_down2(img):
    """createPyramidStore level step: textureBicubicHardware at f=0.5
    (sample pos 2x+1.5 => taps 2x..2x+3, CLAMP_TO_EDGE), fp16 storage."""
    h, w = img.shape[:2]
    oh, ow = max(1, h // 2), max(1, w // 2)
    p = np.pad(img.astype(np.float32), [(2, 2), (2, 2), (0, 0)], mode="edge")
    acc = np.zeros((oh,) + p.shape[1:], np.float32)
    for k in range(4):
        acc += CR_W[k] * p[k:k + 2 * oh: 2]
    acc2 = np.zeros((oh, ow) + img.shape[2:], np.float32)
    for k in range(4):
        acc2 += CR_W[k] * acc[:, k:k + 2 * ow: 2]
    return acc2.astype(np.float16)


def pyramid_levels(img, levelcount):
    lv = [img]
    for _ in range(levelcount - 1):
        lv.append(catmull_down2(lv[-1]))
    return lv


# ------------------------------------------------------------ align shader

def _port_compute_shader(path, defines, local_size):
    with open(path) as f:
        lines = f.read().splitlines()
    out = []
    for line in lines:
        if "#import" in line:
            continue
        if line.strip() == "#define LAYOUT //":
            continue
        if line.strip() == "LAYOUT":
            out.append(f"layout(local_size_x = {local_size}, local_size_y = {local_size}, local_size_z = 1) in;")
            continue
        m = re.match(r"\s*#define\s+(\w+)\s+", line)
        if m and m.group(1) in defines:
            out.append(f"#define {m.group(1)} {defines[m.group(1)]}")
            continue
        out.append(line)
    src = "\n".join(l for l in out if not l.strip().startswith("precision"))
    return "#version 430\n" + src


_active_shader_path = [SHADER_PATH]


def set_shader_path(p):
    _active_shader_path[0] = p


class AlignRunner:
    SAMPLER_UNITS = (("baseTexture", 1), ("alterTexture", 2),
                     ("prevAlignment", 0), ("baseCurve", 0), ("alterCurve", 0))

    def __init__(self, ctx, defines, raw_half, gate=3.0, tile_al=16, prefilter_n=3.74):
        self.ctx = ctx
        self.raw_half = raw_half
        self.gate = gate
        self.tile_al = tile_al
        self.prefilter_n = prefilter_n
        self.prog = ctx.compute_shader(
            _port_compute_shader(_active_shader_path[0], defines, local_size=tile_al // 2))
        self.dummy = ctx.texture((1, 1), 4, dtype="f2")

    def tex(self, arr):
        t = self.ctx.texture((arr.shape[1], arr.shape[0]), 4, dtype="f2")
        t.write(np.ascontiguousarray(arr).astype(np.float16).tobytes())
        return t

    def setu(self, name, value):
        try:
            self.prog[name].value = value
        except KeyError:
            pass

    def run(self, base_levels, alter_levels, noise_s, noise_o, gate_sched=None,
            poly=None, poly_last_only=False, poly_hybrid=False, poly_sel=False,
            fit="ls", recenter=None, local=False, local_all=False, ransac_once=False, seed_outliers=False, seed_delay=0):
        """poly: None | 'affine' | 'bilinear' | 'quad' - after each level's
        dispatch, robustly fit the global polynomial motion model to the
        matcher residuals against the propagated polynomial and write the
        fitted (fractional) field back, so the polynomial accumulates
        sub-texel precision down the pyramid. poly_hybrid keeps per-tile
        matches that agree with the model (local motion detail) and replaces
        only outlier tiles."""
        base_t = [self.tex(l) for l in base_levels]
        alter_t = [self.tex(l) for l in alter_levels]
        p = self.prog
        w0, h0 = self.raw_half
        n = len(alter_t)
        t0 = time.perf_counter()
        poly_coef = None
        ransac_done = False
        fits_seen = 0
        for i in range(n - 2, -1, -1):
            wi, hi = alter_t[i].size
            integral_norm = ((w0 * h0) / (alter_t[i + 1].size[0] * alter_t[i + 1].size[1])) ** 0.5 * self.prefilter_n
            first = 1 if i == n - 2 else 0
            base_t[i].use(1)
            alter_t[i].use(2)
            self.dummy.use(0)
            alter_t[i + 1].bind_to_image(0, read=False, write=True)
            self.setu("noiseS", noise_s)
            self.setu("noiseO", noise_o)
            self.setu("integralNorm", integral_norm)
            self.setu("significancy", gate_sched(i) if gate_sched else self.gate)
            self.setu("first", first)
            self.setu("rawHalf", tuple(self.raw_half))
            self.setu("exposure", 1.0)
            for name, unit in self.SAMPLER_UNITS:
                self.setu(name, unit)
            if not first:
                alter_t[i + 2].use(0)
                self.setu("prevAlignment", 0)
            gx, gy = wi // (self.tile_al // 2) + 1, hi // (self.tile_al // 2) + 1
            p.run(gx, gy, 1)
            self.ctx.finish()
            if poly is not None and not (ransac_once and ransac_done) \
                    and (not poly_last_only or i == 0):
                tex = alter_t[i + 1]

                def decode(tex, valid):
                    d = np.frombuffer(tex.read(), dtype=np.float16).reshape(
                        tex.size[1], tex.size[0], 4).astype(np.float32)[:valid[1], :valid[0]]
                    return (np.rint(d[..., 0] * w0) + d[..., 2],
                            np.rint(d[..., 1] * h0) + d[..., 3])

                def grid_coords(wlev, hlev, th, tw):
                    tyg, txg = np.mgrid[0:th, 0:tw]
                    # tile k sits at 8k level texels; 16k/W - 1 maps the same
                    # physical position to the same (u, v) on every level
                    return ((16.0 * txg / wlev - 1.0).astype(np.float64),
                            (16.0 * tyg / hlev - 1.0).astype(np.float64))

                def run_fit(vx, vy, uu, vv, coef_in, scale):
                    """Fit the residual against the propagated field
                    (coef_in*scale in this level's texel units). Frozen tiles
                    (residual exactly 0) get weight 0 - they only echo prev.
                    Returns (fx, fy, coef_out) with coef_out in THIS level's
                    units; fx/fy None signals too-few-tiles passthrough."""
                    px = eval_poly(coef_in[0], uu, vv) * scale if coef_in is not None \
                        else np.zeros_like(vx, np.float64)
                    py = eval_poly(coef_in[1], uu, vv) * scale if coef_in is not None \
                        else np.zeros_like(vy, np.float64)
                    moved = (np.abs(vx - px) + np.abs(vy - py)) > 0.25
                    # for single-shot seeding always exclude frozen tiles:
                    # with ~100+ tiles even a 10% mover share is plenty of
                    # samples, and the frozen cluster would otherwise win
                    # the consensus outright
                    zero_frac = 0.0 if ransac_once else 0.2
                    wgt = np.where(moved, 1.0, 0.0 if moved.mean() >= 0.25 else zero_frac)
                    fit_fn = ransac_polynomial_field if fit == "ransac" else fit_polynomial_field
                    crx, cry, _inl = fit_fn(vx - px, vy - py, uu, vv, deg=poly, wgt=wgt)
                    if crx is None:
                        return None, None, None, vx, vy
                    fx = px + eval_poly(crx, uu, vv)
                    fy = py + eval_poly(cry, uu, vv)
                    coef_out = ((scale * coef_in[0] + crx) if coef_in is not None else crx,
                                (scale * coef_in[1] + cry) if coef_in is not None else cry)
                    return fx, fy, coef_out, vx, vy

                def dispatch(i, first):
                    wi, hi_ = alter_t[i].size
                    base_t[i].use(1)
                    alter_t[i].use(2)
                    self.dummy.use(0)
                    alter_t[i + 1].bind_to_image(0, read=False, write=True)
                    for name, unit in self.SAMPLER_UNITS:
                        self.setu(name, unit)
                    if not first:
                        alter_t[i + 2].use(0)
                        self.setu("prevAlignment", 0)
                    self.setu("first", first)
                    self.setu("integralNorm", ((w0 * h0) /
                              (alter_t[i + 1].size[0] * alter_t[i + 1].size[1])) ** 0.5 * self.prefilter_n)
                    p.run(wi // (self.tile_al // 2) + 1, hi_ // (self.tile_al // 2) + 1, 1)
                    self.ctx.finish()

                valid = (min(tex.size[0], gx), min(tex.size[1], gy))
                vx, vy = decode(tex, valid)
                uu, vv = grid_coords(wi, hi, *vx.shape)
                fx, fy, coef_new, vx0, vy0 = run_fit(vx, vy, uu, vv, poly_coef, 2.0)
                if i == 0 and coef_new is not None and poly_sel:
                    # selector mode: output the polynomial only for the
                    # wrong-basin failure mode (many tiles far off the model)
                    resx = vx0 - fx
                    resy = vy0 - fy
                    far = float((np.hypot(resx, resy) > 2.5).mean())
                    self.last_poly_decision = (far, far > 0.12)
                    if far <= 0.12:
                        tex.write(encode_alignment(vx0, vy0, w0, h0).tobytes(),
                                  viewport=(0, 0, int(valid[0]), int(valid[1])))
                        self.ctx.finish()
                        continue
                if coef_new is None:
                    fx, fy = vx, vy  # too few tiles: pass raw through
                else:
                    poly_coef = coef_new
                if ransac_once and fits_seen < seed_delay:
                    fits_seen += 1
                    continue
                if ransac_once and coef_new is not None:
                    # single-shot RANSAC seeding: the fitted field replaces
                    # THIS level's tile offsets once; every finer level then
                    # runs the plain per-tile descent, so the final offsets
                    # are RANSAC + the accumulated per-level local
                    # differences. Local motion keeps its own offsets (the
                    # descent is per-tile), while the coarse-level basin
                    # locks the RANSAC replaced cannot re-poison the field.
                    if recenter:
                        # one sharpening round at the seeding level: the
                        # first RANSAC lands on the mover MODE (integer
                        # cluster), not the sub-texel truth. Re-match with
                        # the RANSAC field as prev so the raws distribute
                        # +-1 AROUND it, then refit - the consensus polish
                        # then interpolates to the true fractional position.
                        wi1, hi1 = alter_t[i + 1].size
                        gx1 = wi1 // (self.tile_al // 2) + 1
                        gy1 = hi1 // (self.tile_al // 2) + 1
                        valid1 = (min(alter_t[i + 2].size[0], gx1),
                                  min(alter_t[i + 2].size[1], gy1))
                        uu1, vv1 = grid_coords(wi1, hi1, valid1[1], valid1[0])
                        alter_t[i + 2].write(encode_alignment(
                            eval_poly(poly_coef[0], uu1, vv1) / 2.0,
                            eval_poly(poly_coef[1], uu1, vv1) / 2.0,
                            w0, h0).tobytes(),
                            viewport=(0, 0, int(valid1[0]), int(valid1[1])))
                        self.ctx.finish()
                        dispatch(i, first=0)
                        vx2, vy2 = decode(tex, valid)
                        fx2, fy2, coef2, _, _ = run_fit(vx2, vy2, uu, vv,
                                                        poly_coef, 1.0)
                        if coef2 is not None:
                            poly_coef = coef2
                            fx, fy = fx2, fy2
                    if seed_outliers:
                        # minimal intervention: only tiles that disagree
                        # with the consensus (wrong-basin locks) take the
                        # RANSAC value; agreeing tiles keep their own raw
                        # locks, so clean bursts descend exactly like the
                        # plain per-tile matcher
                        res_o = np.hypot(vx0 - fx, vy0 - fy)
                        outl_o = res_o > 2.5
                        fx = np.where(outl_o, fx, vx0)
                        fy = np.where(outl_o, fy, vy0)
                    tex.write(encode_alignment(fx, fy, w0, h0).tobytes(),
                              viewport=(0, 0, int(valid[0]), int(valid[1])))
                    self.ctx.finish()
                    ransac_done = True
                    continue
                if recenter and coef_new is not None and (recenter == "all" or i == 0):
                    # one re-centering round: feed the fitted field back as
                    # the prev for THIS level (written into gauss[i+2] at the
                    # level-(i+1) tile grid, halved to level-(i+1) units) and
                    # re-match. Tiles now start near the model, so their
                    # integer matches distribute +-1 AROUND the truth instead
                    # of truncating one-sided toward the stale prev - the
                    # refit then lands sub-texel instead of ~1px short.
                    wi1, hi1 = alter_t[i + 1].size
                    gx1, gy1 = wi1 // (self.tile_al // 2) + 1, hi1 // (self.tile_al // 2) + 1
                    valid1 = (min(alter_t[i + 2].size[0], gx1), min(alter_t[i + 2].size[1], gy1))
                    uu1, vv1 = grid_coords(wi1, hi1, valid1[1], valid1[0])
                    alter_t[i + 2].write(encode_alignment(
                        eval_poly(poly_coef[0], uu1, vv1) / 2.0,
                        eval_poly(poly_coef[1], uu1, vv1) / 2.0, w0, h0).tobytes(),
                        viewport=(0, 0, int(valid1[0]), int(valid1[1])))
                    self.ctx.finish()
                    dispatch(i, first=0)
                    vx2, vy2 = decode(tex, valid)
                    fx2, fy2, coef2, _, _ = run_fit(vx2, vy2, uu, vv, poly_coef, 1.0)
                    if coef2 is not None:
                        poly_coef = coef2
                        fx, fy = fx2, fy2
                if (local or local_all) and coef_new is not None:
                    # LOCAL MOTION mode: RANSAC inliers keep the global
                    # polynomial (sub-texel background), but tiles that
                    # coherently disagree with the model keep their raw
                    # per-tile matches - a genuinely moving object wins the
                    # local match while the global fit ignores it. Isolated
                    # disagreements (noise locks on flat regions) have no
                    # coherent neighbourhood and fall back to the model.
                    rawx = vx2 if (recenter and (recenter == "all" or i == 0)
                                   and coef2 is not None) else vx0
                    rawy = vy2 if (recenter and (recenter == "all" or i == 0)
                                   and coef2 is not None) else vy0
                    from numpy.lib.stride_tricks import sliding_window_view
                    outl = np.hypot(rawx - fx, rawy - fy) > 2.5
                    pad_o = np.pad(outl.astype(np.float32), 1, mode="constant")
                    nb = sliding_window_view(pad_o, (3, 3)).sum(axis=(-2, -1)) - outl
                    coh = outl & (nb >= 3)
                    if local_all and i != 0:
                        # per-level mode: coherent outliers keep their raw
                        # offsets so moving objects descend the pyramid with
                        # their OWN motion (like the per-tile matcher) while
                        # the background rides the RANSAC polynomial
                        mx = medfilt3(np.where(coh, rawx, fx))
                        my = medfilt3(np.where(coh, rawy, fy))
                        fx = np.where(coh, mx, fx)
                        fy = np.where(coh, my, fy)
                    # the object tiles' raw matches saturate at +-2 texels
                    # around the prev they started from, so a far-moving
                    # object gains ~2 texels per re-centering round. Blend
                    # the local field in, re-match with the blend as prev,
                    # and repeat until the coherent-outlier field converges
                    # (or 3 extra rounds).
                    for _round in range(4 if i == 0 else 0):
                        if not coh.any():
                            break
                        mx = medfilt3(np.where(coh, rawx, fx))
                        my = medfilt3(np.where(coh, rawy, fy))
                        nfx = np.where(coh, mx, fx)
                        nfy = np.where(coh, my, fy)
                        if _round and float(np.max(np.hypot(nfx - fx, nfy - fy))) < 0.5:
                            fx, fy = nfx, nfy
                            break
                        fx, fy = nfx, nfy
                        wi1, hi1 = alter_t[i + 1].size
                        gx1, gy1 = wi1 // (self.tile_al // 2) + 1, hi1 // (self.tile_al // 2) + 1
                        valid1 = (min(alter_t[i + 2].size[0], gx1), min(alter_t[i + 2].size[1], gy1))
                        # L1 tile t ~ L0 tile 2t: sample the blended field
                        th1, tw1 = valid1[1], valid1[0]
                        sy = (np.arange(th1) * 2).clip(0, fx.shape[0] - 1)
                        sx = (np.arange(tw1) * 2).clip(0, fx.shape[1] - 1)
                        alter_t[i + 2].write(encode_alignment(
                            fx[sy][:, sx] / 2.0, fy[sy][:, sx] / 2.0, w0, h0).tobytes(),
                            viewport=(0, 0, int(tw1), int(th1)))
                        self.ctx.finish()
                        dispatch(i, first=0)
                        vx3, vy3 = decode(tex, valid)
                        outl3 = np.hypot(vx3 - fx, vy3 - fy) > 2.5
                        pad_o3 = np.pad(outl3.astype(np.float32), 1, mode="constant")
                        nb3 = sliding_window_view(pad_o3, (3, 3)).sum(axis=(-2, -1)) - outl3
                        coh = outl3 & (nb3 >= 2)
                        rawx, rawy = vx3, vy3
                if poly_hybrid and coef_new is not None:
                    near = (np.hypot(vx0 - fx, vy0 - fy) < 1.5)
                    fx = np.where(near, vx0, fx)
                    fy = np.where(near, vy0, fy)
                tex.write(encode_alignment(fx, fy, w0, h0).tobytes(),
                          viewport=(0, 0, int(valid[0]), int(valid[1])))
                self.ctx.finish()
        valid = (min(alter_t[1].size[0], w0 // (self.tile_al // 2) + 1),
                 min(alter_t[1].size[1], h0 // (self.tile_al // 2) + 1))
        data = np.frombuffer(alter_t[1].read(), dtype=np.float16).reshape(
            alter_t[1].size[1], alter_t[1].size[0], 4).astype(np.float32)[:valid[1], :valid[0]]
        ms = (time.perf_counter() - t0) * 1e3
        for t in base_t + alter_t:
            t.release()
        if poly is not None:
            # fractional offsets survived the polynomial pass-through
            ox = data[..., 0] * w0 + data[..., 2]
            oy = data[..., 1] * h0 + data[..., 3]
        else:
            ox = np.rint(data[..., 0] * w0) + data[..., 2]
            oy = np.rint(data[..., 1] * h0) + data[..., 3]
        return ox, oy, ms


# ----------------------------------------------------------------- configs

def auto_sigma(ns, no):
    rel = float(np.sqrt(0.1 * ns + no) / 0.1)
    return float(np.clip(0.9 + 1.1 * np.log2(rel / 0.08), 1.0, 2.0))


def cfg_norm_sigma(cfg, ns, no):
    s = cfg.get("sigma", 0.0)
    return auto_sigma(ns, no) if s == "auto" else float(s)


CONFIGS = {
    # ---- shipped references (legacy = pre-robust-cost app shader, see legacy_align.glsl)
    "old":       dict(norm="oldbox", robust=0, offsets=5, gate=0.0, shader="legacy"),
    "old_o9":    dict(norm="oldbox", robust=0, offsets=9, gate=0.0, shader="legacy"),
    "current":   dict(norm="gauss", sigma="auto", robust=1, trunc=3.0, offsets=5, gate=3.0),
    # ---- normalize variants on plain SAD (like-before cost)
    "sad_trimbox": dict(norm="trimbox", robust=0, offsets=5, gate=0.0),
    "sad_g10":     dict(norm="gauss", sigma=1.0, robust=0, offsets=5, gate=0.0),
    "sad_g15":     dict(norm="gauss", sigma=1.5, robust=0, offsets=5, gate=0.0),
    "sad_g20":     dict(norm="gauss", sigma=2.0, robust=0, offsets=5, gate=0.0),
    "sad_g15nt":   dict(norm="gauss_notrim", sigma=1.5, robust=0, offsets=5, gate=0.0),
    "sad_o9":      dict(norm="oldbox", robust=0, offsets=9, gate=0.0),
    # ---- robust cost sweeps on best normalize
    "trunc3":   dict(norm="gauss", sigma=1.5, robust=1, trunc=3.0, offsets=5, gate=3.0),
    "trunc6":   dict(norm="gauss", sigma=1.5, robust=1, trunc=6.0, offsets=5, gate=3.0),
    "trunc12":  dict(norm="gauss", sigma=1.5, robust=1, trunc=12.0, offsets=5, gate=3.0),
    "trunc6_k0": dict(norm="gauss", sigma=1.5, robust=1, trunc=6.0, offsets=5, gate=0.0),
    "trunc3_k0": dict(norm="gauss", sigma=1.5, robust=1, trunc=3.0, offsets=5, gate=0.0),
    "charb6":   dict(norm="gauss", sigma=1.5, robust=2, trunc=6.0, offsets=5, gate=3.0),
    "chi2":     dict(norm="gauss", sigma=1.5, robust=3, trunc=6.0, offsets=5, gate=3.0),
    "chi2_k0":  dict(norm="gauss", sigma=1.5, robust=3, trunc=6.0, offsets=5, gate=0.0),
    # ---- OFFSETS 9 variants
    "trunc6_o9": dict(norm="gauss", sigma=1.5, robust=1, trunc=6.0, offsets=9, gate=3.0),
    "chi2_o9":   dict(norm="gauss", sigma=1.5, robust=3, trunc=6.0, offsets=9, gate=3.0),
    # ---- sigma-normalized SAD (no truncation) + significance gate
    "nsad":      dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=5, gate=0.0),
    "nsad_k15":  dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=5, gate=1.5),
    "nsad_k3":   dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=5, gate=3.0),
    "nsad_k45":  dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=5, gate=4.5),
    "nsad_o9":   dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=9, gate=3.0),
    "chi2_k15":  dict(norm="gauss", sigma=1.5, robust=3, trunc=100.0, offsets=5, gate=1.5),
    "chi2_k45":  dict(norm="gauss", sigma=1.5, robust=3, trunc=100.0, offsets=5, gate=4.5),
    # ---- nsad on other prefilters
    "nsad_oldbox": dict(norm="oldbox", robust=1, trunc=100.0, offsets=5, gate=3.0),
    "nsad_nt":     dict(norm="gauss_notrim", sigma=1.5, robust=1, trunc=100.0, offsets=5, gate=3.0),
    # ---- nsad_k15 fine tuning
    "k15_ob":   dict(norm="oldbox", robust=1, trunc=100.0, offsets=5, gate=1.5),
    "k15_tb":   dict(norm="trimbox", robust=1, trunc=100.0, offsets=5, gate=1.5),
    "k15_g10":  dict(norm="gauss", sigma=1.0, robust=1, trunc=100.0, offsets=5, gate=1.5),
    "k15_g20":  dict(norm="gauss", sigma=2.0, robust=1, trunc=100.0, offsets=5, gate=1.5),
    "k15_gnt":  dict(norm="gauss_notrim", sigma=1.5, robust=1, trunc=100.0, offsets=5, gate=1.5),
    "k15_ga":   dict(norm="gauss", sigma="auto", robust=1, trunc=100.0, offsets=5, gate=1.5),
    "k15_o9":   dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=9, gate=1.5),
    "k10":      dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=5, gate=1.0),
    "k20":      dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=5, gate=2.0),
    "k30":      dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=5, gate=3.0),
    # ---- decisive round: OFFSETS=9 fixed
    "old_o9":      dict(norm="oldbox", robust=0, offsets=9, gate=0.0),
    "k15_ob_o9":   dict(norm="oldbox", robust=1, trunc=100.0, offsets=9, gate=1.5),
    "k15_ga_o9":   dict(norm="gauss", sigma="auto", robust=1, trunc=100.0, offsets=9, gate=1.5),
    "k15_gnt_o9":  dict(norm="gauss_notrim", sigma=1.5, robust=1, trunc=100.0, offsets=9, gate=1.5),
    "k10_o9":      dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=9, gate=1.0),
    "k20_o9":      dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=9, gate=2.0),
    "k30_o9":      dict(norm="gauss", sigma=1.5, robust=1, trunc=100.0, offsets=9, gate=3.0),
    "chi2_k15_o9": dict(norm="gauss", sigma=1.5, robust=3, trunc=100.0, offsets=9, gate=1.5),
    # ---- final shipped configuration (rewritten shaders)
    "final":       dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0),
    "final_o5":    dict(norm="gauss_notrim", sigma=1.5, offsets=5, gate=2.0),
    "final_k15":   dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=1.5),
    "final_k30":   dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=3.0),
    # ---- polynomial global-motion fit (residual accumulation per level,
    #      robust to wrong-basin tiles)
    "poly_aff":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="affine"),
    "poly_bl":     dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="bilinear"),
    "poly_quad":   dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad"),
    "poly_quad_h": dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", poly_hybrid=True),
    "poly_last":   dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", poly_last_only=True),
    # ---- 3-pass refinement (reach +-3 texels per level)
    "final_r3":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, refines=3),
    "poly_r3":     dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", refines=3),
    "poly_r3_k15": dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=1.5, poly="quad", refines=3),
    # ---- relaxed gate for poly (the robust fit cleans noise locks)
    "poly_k15":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=1.5, poly="quad"),
    "poly_k10":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=1.0, poly="quad"),
    "poly_k05":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.5, poly="quad"),
    "poly_k0":     dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.0, poly="quad"),
    # ---- selector: polynomial output only when the raw field fights the model
    "poly_sel":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", poly_sel=True),
    "poly_sel_k05": dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.5, poly="quad", poly_sel=True),
    "poly_sel_k0": dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.0, poly="quad", poly_sel=True),
    # ---- RANSAC global-motion fit (consensus instead of averaging; runs at
    #      every level with enough tiles, propagates only the RANSAC field)
    "ransac":      dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", fit="ransac"),
    "ransac_k10":  dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=1.0, poly="quad", fit="ransac"),
    "ransac_k05":  dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.5, poly="quad", fit="ransac"),
    "ransac_k0":   dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.0, poly="quad", fit="ransac"),
    # ---- RANSAC + re-centering (re-match around the fitted field, refit)
    "ransac_rc":   dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.0, poly="quad", fit="ransac", recenter="all"),
    "ransac_rcl":  dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.0, poly="quad", fit="ransac", recenter="last"),
    "ransac_rc_k2": dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", fit="ransac", recenter="all"),
    # ---- local motion: global RANSAC for the background, coherent outlier
    #      regions keep their raw per-tile matches (moving objects)
    "ransac_local": dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.0, poly="quad", fit="ransac", recenter="last", local=True),
    "ransac_local2": dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.0, poly="quad", fit="ransac", recenter="last", local_all=True),
    # ---- single-shot RANSAC seeding: fit ONCE at the first level with
    #      enough tiles, then plain per-tile descent (RANSAC + local diff)
    "seed":        dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", fit="ransac", ransac_once=True),
    "seed_k0":     dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.0, poly="quad", fit="ransac", ransac_once=True),
    "seed_k10":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=1.0, poly="quad", fit="ransac", ransac_once=True),
    "seed_rc":     dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", fit="ransac", ransac_once=True, recenter="seed"),
    "seed_rc_k0":  dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=0.0, poly="quad", fit="ransac", ransac_once=True, recenter="seed"),
    "seed_out":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", fit="ransac", ransac_once=True, seed_outliers=True),
    "seed_L2":     dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", fit="ransac", ransac_once=True, seed_delay=1),
    "seed_L1":     dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=2.0, poly="quad", fit="ransac", ransac_once=True, seed_delay=2),
    # ---- level-scaled gate schedules (coarser levels => stricter gate)
    "final_s2":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=1.5, gate_sched="sqrt2"),
    "final_sl":    dict(norm="gauss_notrim", sigma=1.5, offsets=9, gate=1.5, gate_sched="lin"),
    "final_s2_k1": dict(norm="gauss_notrim", sigma=1.5, offsets=1.0, gate_sched="sqrt2"),
}

SCENARIOS = {
    # same scene brightness (metered), different sensor gain:
    # noiseS/noiseO in normalized units ~= Photon Camera noise model at that ISO
    "day":    (1.0,   2.5e-4, 3e-6),    # ~ISO100
    "dusk":   (1.0,   1.6e-3, 3e-5),    # ~ISO800
    "night":  (1.0,   6.0e-3, 2.5e-4),  # ~ISO3200
}


# ------------------------------------------------------------------- metrics

def medfilt3(a):
    from numpy.lib.stride_tricks import sliding_window_view
    p = np.pad(a, 1, mode="edge")
    w = sliding_window_view(p, (3, 3))
    return np.median(w, axis=(-2, -1))


def evaluate(ox, oy, gtx, gty, border=6, obj_mask=None):
    ex = ox[border:-border, border:-border] - gtx[border:-border, border:-border]
    ey = oy[border:-border, border:-border] - gty[border:-border, border:-border]
    err = np.hypot(ex, ey)
    bx = ox[border:-border, border:-border] - medfilt3(ox)[border:-border, border:-border]
    by = oy[border:-border, border:-border] - medfilt3(oy)[border:-border, border:-border]
    blk = np.hypot(bx, by)
    out = dict(med=float(np.median(err)), p90=float(np.percentile(err, 90)),
               bad1=float((err > 1.01).mean() * 100), bad2=float((err > 2.01).mean() * 100),
               blk_med=float(np.median(blk)), blk_p90=float(np.percentile(blk, 90)))
    if obj_mask is not None:
        m = obj_mask[border:-border, border:-border]
        if m.any() and (~m).any():
            out["bg_bad2"] = float((err[~m] > 2.01).mean() * 100)
            out["obj_bad2"] = float((err[m] > 2.01).mean() * 100)
            out["obj_med"] = float(np.median(err[m]))
    return out


# ---------------------------------------------------- polynomial motion fit

def poly_basis(u, v, deg):
    if deg == "affine":
        return np.stack([np.ones_like(u), u, v], -1)
    if deg == "bilinear":
        return np.stack([np.ones_like(u), u, v, u * v], -1)
    return np.stack([np.ones_like(u), u, v, u * u, v * v, u * v], -1)


def eval_poly(coef, u, v):
    n = len(coef)
    deg = {3: "affine", 4: "bilinear"}.get(n, "quad")
    return poly_basis(u, v, deg) @ coef


def fit_polynomial_field(ox, oy, u, v, deg="quad", border=2, iters=3, wgt=None):
    """Robust global polynomial fit of a tile-offset field (per axis).

    ox/oy: offsets in the current level's texel units; u/v: normalized
    physical tile coordinates in [-1, 1] (16*tx/W - 1 - the same physical
    position maps to the same (u, v) on every pyramid level, so coefficients
    transfer across levels with only the x2 unit scale).
    wgt: per-tile fit weights (gate-frozen tiles that just carry the
    propagated prev offset hold no independent evidence and are downweighted
    by the caller).
    Fits on the interior tiles, rejecting outliers by MAD (wrong-basin
    locks). Returns (coefx, coefy, inlier_fraction) or (None, None, 1.0)
    when too few interior tiles exist (coarsest levels)."""
    A = poly_basis(u, v, deg)
    th, tw = ox.shape
    if wgt is None:
        wgt = np.ones_like(ox, np.float64)
    inner = np.zeros(u.shape, bool)
    b = min(border, max((th - 1) // 2, 1), max((tw - 1) // 2, 1))
    inner[b:th - b or None, b:tw - b or None] = True
    if inner.sum() < A.shape[-1] * 3:
        return None, None, 1.0

    def fit_axis(val):
        m = inner.copy()
        for _ in range(iters):
            wm = wgt[m][:, None]
            coef, *_ = np.linalg.lstsq(A[m] * wm, val[m] * wm[:, 0], rcond=None)
            res = np.abs(val - A @ coef)
            act = m & (wgt > 0.5)  # MAD over informative tiles only
            if not act.any():
                act = m
            mad = np.median(np.abs(res[act] - np.median(res[act])))
            sig = 1.4826 * mad + 1e-9
            # 1.5-texel floor: a correct integer match sits within +-1 texel
            # of the fractional truth (rounding), so anything closer than
            # 1.5 is signal - including the rare +-1 moves on coarse levels
            # where the truth is sub-texel (those carry the only observable
            # motion). Wrong-basin locks land >= 2 texels away and are cut.
            thr = max(2.5 * sig, 1.5)
            m = inner & (res < thr)
            if m.sum() < A.shape[-1] * 2:
                m = inner
                break
        coef, *_ = np.linalg.lstsq(A[m], val[m], rcond=None)
        return coef, float((m & inner).sum() / inner.sum())

    cx, ix = fit_axis(ox.astype(np.float64))
    cy, iy = fit_axis(oy.astype(np.float64))
    return cx, cy, min(ix, iy)


def encode_alignment(fx, fy, w0, h0):
    """alignmentToVec4 encoding: floor part /rawHalf in .xy, fract in .zw."""
    out = np.zeros(fx.shape + (4,), np.float16)
    out[..., 0] = (np.floor(fx) / w0).astype(np.float16)
    out[..., 1] = (np.floor(fy) / h0).astype(np.float16)
    out[..., 2] = (fx - np.floor(fx)).astype(np.float16)
    out[..., 3] = (fy - np.floor(fy)).astype(np.float16)
    return out


def ransac_polynomial_field(ox, oy, u, v, deg="quad", border=2, tau=1.5,
                            iters=400, wgt=None, seed=13):
    """RANSAC fit of the joint 2-axis polynomial motion model.

    Unlike the MAD-weighted least squares (which needs the majority of tiles
    on board and averages biased evidence), RANSAC hunts the largest
    consensus set: it samples minimal tile subsets (basis-size points),
    fits exactly, and counts tiles within tau texels of BOTH axes. This
    survives majority-wrong fields (wrong-basin locks) and truncation-biased
    mover distributions - it takes the dominant mode, not the mean.

    Gate-frozen tiles (wgt==0) are excluded from sampling and consensus
    scoring: they only echo the propagated prev field and would otherwise
    form a spurious perfect consensus at prev. Returns
    (coefx, coefy, inlier_fraction_of_scored) or (None, None, 1.0) when too
    few scored tiles exist (the coarsest levels - caller passes raw
    through, exactly like the LS path)."""
    A = poly_basis(u, v, deg)
    s = A.shape[-1]
    th, tw = ox.shape
    if wgt is None:
        wgt = np.ones_like(ox, np.float64)
    inner = np.zeros(u.shape, bool)
    b = min(border, max((th - 1) // 2, 1), max((tw - 1) // 2, 1))
    inner[b:th - b or None, b:tw - b or None] = True
    score = inner & (wgt > 0.5)
    if score.sum() < s * 3:
        return None, None, 1.0
    Ai = A[score]
    xi = ox.astype(np.float64)[score]
    yi = oy.astype(np.float64)[score]
    n = len(xi)
    rng = np.random.default_rng(seed)
    best_cnt, best_cx, best_cy = 0, None, None
    # adaptive trial count: standard RANSAC bound for the current inlier
    # ratio, capped at `iters`
    need = iters
    tried = 0
    while tried < need:
        idx = rng.integers(0, n, s)
        try:
            cx = np.linalg.solve(Ai[idx], xi[idx])
            cy = np.linalg.solve(Ai[idx], yi[idx])
        except np.linalg.LinAlgError:
            tried += 1
            continue  # duplicated index / degenerate sample
        cnt = int((np.hypot(Ai @ cx - xi, Ai @ cy - yi) < tau).sum())
        if cnt > best_cnt:
            best_cnt, best_cx, best_cy = cnt, cx, cy
            w = cnt / n
            den = np.log(max(1.0 - w ** s, 1e-9))  # guard: w^s rounds to 1.0
            # trials needed (from scratch) for 99.9% confidence at this
            # inlier ratio: log(1e-3)/log(1-w^s), both negative => positive
            need = iters if den >= 0 else int(min(iters, tried + np.log(1e-3) / den))
        tried += 1
    if best_cx is None:
        return None, None, 1.0
    # polish: least squares over the consensus set, re-grow it twice
    m = np.hypot(Ai @ best_cx - xi, Ai @ best_cy - yi) < tau
    for _ in range(2):
        if m.sum() < s:
            m = np.ones(n, bool)
            break
        best_cx, *_ = np.linalg.lstsq(Ai[m], xi[m], rcond=None)
        best_cy, *_ = np.linalg.lstsq(Ai[m], yi[m], rcond=None)
        m = np.hypot(Ai @ best_cx - xi, Ai @ best_cy - yi) < tau
    return best_cx, best_cy, float(m.mean())


# --------------------------------------------------------------------- bench

def build_burst(planes, scenario, seed, warp_seed, obj=None):
    b, ns, no = SCENARIOS[scenario]
    # metering: normalize exposure so highlights ~0.85, then scenario brightness
    hi = np.quantile(planes, 0.995)
    scene = np.clip(planes / max(hi, 1e-6) * 0.85 * b, 0.0, 1.0)
    rng = np.random.default_rng(seed)
    Hm = random_homography(scene.shape[:2], warp_seed)
    if obj is not None:
        scene_base, scene_alter = make_object_scenes(scene, obj, seed)
        base = mosaic_frame(scene_base, np.eye(3), ns, no, rng, ident=True)
        alter = mosaic_frame(scene_alter, Hm, ns, no, rng, ident=False)
    else:
        base = mosaic_frame(scene, np.eye(3), ns, no, rng, ident=True)
        alter = mosaic_frame(scene, Hm, ns, no, rng, ident=False)
    return base, alter, Hm, ns, no


def ground_truth(half_w, half_h, Hm, tile=8, obj=None):
    """GT offset (half-res px) per output tile (tile centers at half-res 8t).
    With a moving object: tiles whose sensor-space center falls inside the
    object bbox carry the object displacement H(p + shift) - p (the object
    sits at scene position p+shift before the frame's homography); other
    tiles carry the background H(p) - p. Also returns the object tile mask
    when obj is given."""
    th, tw = half_h // tile + 1, half_w // tile + 1
    tx = (np.arange(tw) * tile).astype(np.float32)
    ty = (np.arange(th) * tile).astype(np.float32)
    gx, gy = np.meshgrid(tx, ty)
    # half-res position (8t) -> sensor pos (16t + 1)
    px = gx * 2.0 + 1.0
    py = gy * 2.0 + 1.0
    pts = np.stack([px, py], axis=-1)
    mapped = apply_h(pts, Hm)
    gtx = (mapped[..., 0] - px) / 2.0
    gty = (mapped[..., 1] - py) / 2.0
    if obj is None:
        return gtx, gty, None
    ox_, oy_, ow, oh = obj["bbox"]
    dx, dy = obj["shift"]
    inside = (px >= ox_) & (px < ox_ + ow) & (py >= oy_) & (py < oy_ + oh)
    obj_mapped = apply_h(pts + np.array([dx, dy], np.float32), Hm)
    gtx = np.where(inside, (obj_mapped[..., 0] - px) / 2.0, gtx)
    gty = np.where(inside, (obj_mapped[..., 1] - py) / 2.0, gty)
    return gtx, gty, inside


def run_config(ctx, base_raw, alter_raw, Hm, ns, no, cfg_name, obj=None):
    cfg = CONFIGS[cfg_name]
    set_shader_path(LEGACY_SHADER_PATH if cfg.get("shader") == "legacy" else SHADER_PATH)
    half_h, half_w = base_raw.shape[0] // 2, base_raw.shape[1] // 2
    mode = cfg["norm"]
    sigma = cfg_norm_sigma(cfg, ns, no)
    nb, n_eff = normalize_pass(base_raw, mode, sigma)
    na, _ = normalize_pass(alter_raw, mode, sigma)
    bl = estimate_black_level(nb)
    base = normalizebl(nb, bl)
    alter = normalizebl(na, bl)
    if mode == "oldbox":
        pn = 4.0
    elif mode == "trimbox":
        pn = float(np.sqrt(14.0))
    else:
        wx = np.exp(-(np.arange(5) - 2.0) ** 2 / (2 * sigma * sigma))
        wx /= wx.sum()
        pn = 1.0 / float((wx ** 2).sum())
    levelcount = max(int(np.log2(half_w)) - 1, 2)
    base_lv = pyramid_levels(base, levelcount)
    alter_lv = pyramid_levels(alter, levelcount)
    # ROBUST/TRUNC only exist in the legacy shader; the port ignores defines
    # that are absent from the source, so extra keys are harmless
    defines = dict(OFFSETS=cfg.get("offsets", 9))
    if cfg.get("refines"):
        defines["REFINE_PASSES"] = int(cfg["refines"])
    if cfg.get("robust") is not None:
        defines["ROBUST"] = cfg["robust"]
    if "trunc" in cfg:
        defines["TRUNC"] = float(cfg["trunc"])
    runner = AlignRunner(ctx, defines, (half_w, half_h), gate=cfg.get("gate", 1.5),
                         tile_al=16, prefilter_n=pn)
    sched = cfg.get("gate_sched")
    if sched == "sqrt2":
        gate_sched = lambda i: cfg.get("gate", 1.5) * (2.0 ** (i / 2.0))
    elif sched == "lin":
        gate_sched = lambda i: cfg.get("gate", 1.5) * (1.0 + i)
    else:
        gate_sched = None
    ox, oy, ms = runner.run(base_lv, alter_lv, ns, no, gate_sched=gate_sched,
                            poly=cfg.get("poly"),
                            poly_last_only=bool(cfg.get("poly_last_only")),
                            poly_hybrid=bool(cfg.get("poly_hybrid")),
                            poly_sel=bool(cfg.get("poly_sel")),
                            fit=cfg.get("fit", "ls"),
                            recenter=cfg.get("recenter"),
                            local=bool(cfg.get("local")),
                            local_all=bool(cfg.get("local_all")),
                            ransac_once=bool(cfg.get("ransac_once")),
                            seed_outliers=bool(cfg.get("seed_outliers")),
                            seed_delay=int(cfg.get("seed_delay", 0)))
    gtx, gty, obj_mask = ground_truth(half_w, half_h, Hm, obj=obj)
    m = evaluate(ox, oy, gtx, gty, obj_mask=obj_mask)
    m["ms"] = ms
    return m, (ox, oy, gtx, gty)


IMAGES = {
    "proraw2": "proraw2.dng",  # dark night city, 2.5x
    "proraw3": "proraw3.dng",  # day scene
    "proraw4": "proraw4.dng",  # indoor dusk
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", action="append", default=None, choices=sorted(IMAGES))
    ap.add_argument("--scenario", action="append", default=None, choices=sorted(SCENARIOS))
    ap.add_argument("--config", action="append", default=None, choices=sorted(CONFIGS))
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--warpseed", type=int, default=3)
    ap.add_argument("--maxside", type=int, default=1536, help="max sensor side (crop)")
    ap.add_argument("--save", default=None, help=".npz path to dump ox/oy/gt of last run")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--objshift", type=int, nargs=2, default=None, metavar=("DX", "DY"),
                    help="add a moving object: extra shift in half-res px (e.g. --objshift 6 -4)")
    ap.add_argument("--objsize", type=int, nargs=2, default=(300, 220), metavar=("W", "H"),
                    help="moving object size in sensor px")
    args = ap.parse_args()
    if args.list:
        for k, v in CONFIGS.items():
            print(f"{k:14s} {v}")
        return
    import moderngl
    ctx = moderngl.create_context(standalone=True)
    imgs = args.image or ["proraw3", "proraw4", "proraw2"]
    scens = args.scenario or ["day", "night"]
    cfgs = args.config or ["final"]
    obj = None
    if args.objshift is not None:
        # object placed off-center; shift converts half-res px -> sensor px
        obj = dict(bbox=(420, 320, args.objsize[0], args.objsize[1]),
                   shift=(args.objshift[0] * 2, args.objshift[1] * 2))
    cache = {}
    hdr = (f"{'burst':<18}{'config':<14}{'med':>7}{'p90':>7}{'bad1%':>7}{'bad2%':>7}"
           f"{'bg_bad2':>8}{'obj_med':>8}{'obj_bad2':>8}{'ms':>6}")
    print(hdr)
    print("-" * len(hdr))
    results = {}
    for img in imgs:
        if img not in cache:
            cache[img] = load_proraw(os.path.join(DATA_DIR, IMAGES[img]),
                                     max_side=args.maxside)
        planes = cache[img]
        for sc in scens:
            base_raw, alter_raw, Hm, ns, no = build_burst(planes, sc, args.seed,
                                                          args.warpseed, obj=obj)
            for cf in cfgs:
                m, dump = run_config(ctx, base_raw, alter_raw, Hm, ns, no, cf, obj=obj)
                results[(img, sc, cf)] = m
                print(f"{img+':'+sc:<18}{cf:<14}{m['med']:>7.2f}{m['p90']:>7.2f}"
                      f"{m['bad1']:>7.1f}{m['bad2']:>7.1f}"
                      f"{m.get('bg_bad2', float('nan')):>8.1f}{m.get('obj_med', float('nan')):>8.2f}"
                      f"{m.get('obj_bad2', float('nan')):>8.1f}{m['ms']:>6.0f}")
                if args.save:
                    np.savez(args.save, ox=dump[0], oy=dump[1], gtx=dump[2], gty=dump[3])
    print("\n(med/p90/bad = per-tile offset error vs GT (bg+object piecewise), half-res px;"
          " bg_bad2/obj_med/obj_bad2 = background-only bad2% and object-region metrics)")
    ctx.release()


if __name__ == "__main__":
    main()
