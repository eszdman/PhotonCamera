#!/usr/bin/env python3
"""
Offline benchmark for Photon Camera's block-pyramid burst alignment
(app/src/main/assets/shaders/alignment/align.glsl + the PyramidAlignment java loop).

Runs the *actual* app shader (unmodified, read from the app assets) inside a
desktop OpenGL 4.3+ context via moderngl, on synthetic bursts with known
ground-truth shifts, and measures:
  - robustness: tile alignment error vs. ground truth (median / p90 / % bad tiles)
  - speed:     ms per pyramid level and total

Scenes model the app's normalized input (rgba16f in [0,1], exposure=1):
photon noise is drawn as sigma = sqrt(signal*noiseS + noiseO), matching the
noise model used by the shader.

Usage:
  ./align_bench.py                     # full comparison table
  ./align_bench.py --scene night --config robust_gate9
  ./align_bench.py --list-configs
"""
import argparse
import os
import re
import sys
import time

import numpy as np

APP_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
SHADER_PATH = os.path.join(APP_ROOT, "app", "src", "main", "assets",
                           "shaders", "alignment", "align.glsl")
NORMALIZE_PATH = os.path.join(APP_ROOT, "app", "src", "main", "assets",
                              "shaders", "alignment", "normalize.glsl")

# ---------------------------------------------------------------- shader port

def _port_compute_shader(path, defines, local_size):
    """Adapt an app compute shader for desktop GL:
    - override '#define NAME val' lines (same first-match semantics as GLInterface)
    - strip '#import ...'
    - replace the LAYOUT trick with a real compute layout
    """
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


def load_align_shader(defines: dict, local_size=8):
    return _port_compute_shader(SHADER_PATH, defines, local_size)


def load_normalize_shader(defines: dict, local_size=8):
    return _port_compute_shader(NORMALIZE_PATH, defines, local_size)


# ---------------------------------------------------------------- scene synth

def fractal_texture(w, h, seed, beta=1.3):
    """1/f^beta fractal noise texture in [0,1]."""
    rng = np.random.default_rng(seed)
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    spectrum = 1.0 / ((fx * fx + fy * fy) + 1e-6) ** (beta / 2.0)
    f = (rng.standard_normal((h, w)) + 1j * rng.standard_normal((h, w))) * np.sqrt(spectrum)
    img = np.fft.ifft2(f).real
    img -= img.min()
    img /= max(img.max(), 1e-9)
    return img.astype(np.float32)


def make_scene(w, h, seed, brightness, lights):
    """Synthetic night-ish scene in linear-light units [0, ~1]."""
    tex = fractal_texture(w, h, seed, beta=1.1)
    fine = fractal_texture(w, h, seed + 11, beta=0.0)  # pixel-scale detail
    rng = np.random.default_rng(seed + 1)
    # structural content: edges / bands
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    bands = 0.5 + 0.5 * np.sin(xx * 0.02 + yy * 0.01 + rng.uniform(0, 6.28))
    scene = (0.45 * tex + 0.25 * fine + 0.30 * bands) ** 1.5
    # point lights (street lamps / windows)
    if lights:
        for _ in range(int(w * h / 9000)):
            cx, cy = rng.integers(0, w), rng.integers(0, h)
            amp = rng.uniform(0.4, 1.0)
            s = rng.uniform(2.0, 8.0)
            d2 = (xx - cx) ** 2 + (yy - cy) ** 2
            scene += amp * np.exp(-d2 / (2 * s * s))
    scene *= brightness
    return np.clip(scene, 0.0, 1.0)


SCENES = {
    # name: (mean brightness, noiseS, noiseO, lights)
    # noiseS = 1/(full-well in e-), noiseO = (read noise in e- / full-well)^2
    # day ~ ISO100 (FW 4000e, 2e- read), dusk ~ ISO800, night ~ ISO3200
    "day":        (0.25, 0.0003, 4e-6,  True),
    "dusk":       (0.08, 0.0020, 3e-5,  True),
    "night_city": (0.10, 0.0080, 2.6e-4, True),
    "night":      (0.04, 0.0080, 2.6e-4, True),
    "night_flat": (0.04, 0.0080, 2.6e-4, False),
}


def make_burst(scene, noise_s, noise_o, shift, seed):
    """Base + alter frame with ground-truth integer shift; returns (base, alter)."""
    rng = np.random.default_rng(seed)
    h, w = scene.shape
    noise_base = rng.standard_normal((h, w)).astype(np.float32) * np.sqrt(scene * noise_s + noise_o)
    noise_alt = rng.standard_normal((h, w)).astype(np.float32) * np.sqrt(scene * noise_s + noise_o)
    base = np.clip(scene + noise_base, 0.0, 1.0)
    shifted = np.roll(scene, shift, axis=(0, 1))
    alter = np.clip(shifted + noise_alt, 0.0, 1.0)
    return base, alter


def make_raw_burst(scene, noise_s, noise_o, shift, seed, white_level=1023, hot_pixels=0):
    """Full-res single-channel u16 raw pair (2x the half-res scene) with
    photon noise in counts, sensor quantization and optional stuck-white hot
    pixels (same positions in both frames, like a real sensor)."""
    rng = np.random.default_rng(seed)
    h2, w2 = scene.shape[0] * 2, scene.shape[1] * 2
    # replicate scene onto the CFA grid (gray scenes: all 4 phases identical)
    quad = np.repeat(np.repeat(scene, 2, axis=0), 2, axis=1)
    var_counts = quad * noise_s * white_level**2 + noise_o * white_level**2
    def frame(scn):
        n = rng.standard_normal(scn.shape).astype(np.float32) * np.sqrt(var_counts)
        raw = np.clip((scn * white_level + n), 0, white_level)
        return np.rint(raw).astype(np.uint16)
    base = frame(quad)
    alter = frame(np.roll(quad, (shift[0] * 2, shift[1] * 2), axis=(0, 1)))
    if hot_pixels > 0:
        ys = rng.integers(0, h2, hot_pixels)
        xs = rng.integers(0, w2, hot_pixels)
        base[ys, xs] = white_level
        alter[ys, xs] = white_level  # fixed pattern: same sites in both frames
    return base, alter


class NormalizeRunner:
    """Runs the app's normalize.glsl (raw u16 -> averaged rgba16f half-res)."""

    def __init__(self, ctx, defines, sigma=1.0):
        self.ctx = ctx
        self.sigma = sigma
        self.prog = ctx.compute_shader(load_normalize_shader(defines, local_size=8))
        self.gain = ctx.texture((1, 1), 4, dtype="f2")
        self.gain.write(np.ones((1, 1, 4), np.float16).tobytes())

    def run(self, raw_u16, white_level=1023.0, black_level=0.0):
        h, w = raw_u16.shape
        tex = self.ctx.texture((w, h), 1, dtype="u2")
        tex.write(raw_u16.tobytes())
        out = self.ctx.texture((w // 2, h // 2), 4, dtype="f2")
        p = self.prog
        tex.use(0)
        self.gain.use(1)
        out.bind_to_image(0, read=False, write=True)
        p["inTexture"].value = 0
        p["gainMap"].value = 1
        p["whiteLevel"].value = float(white_level)
        p["blackLevel"].value = (black_level,) * 4
        p["exposure"].value = 1.0
        self.setu("blurSigma", float(self.sigma))
        p.run((w // 2 + 7) // 8, (h // 2 + 7) // 8, 1)
        self.ctx.finish()
        data = np.frombuffer(out.read(), dtype=np.float16).reshape(h // 2, w // 2, 4)
        tex.release()
        out.release()
        return data.astype(np.float32).mean(axis=2)  # gray for the aligner

    def setu(self, name, value):
        try:
            self.prog[name].value = value
        except KeyError:
            pass  # uniform compiled out for this config


# ------------------------------------------------------------------- pyramid

Gauss52 = np.array([1, 4, 6, 4, 1], np.float32) / 16.0

def blur5(img, axis):
    pad = [(2, 2) if a == axis else (0, 0) for a in range(img.ndim)]
    p = np.pad(img, pad, mode="edge")
    out = np.zeros_like(img)
    n = img.shape[axis]
    for k in range(5):
        sl = [slice(None)] * img.ndim
        sl[axis] = slice(4 - k, 4 - k + n)
        out += Gauss52[k] * p[tuple(sl)]
    return out

def pyramid_levels(gray, levelcount):
    """rgba16f-style 4-channel levels like GLUtils.createPyramidStore."""
    lv = [np.repeat(gray[:, :, None], 4, axis=2).astype(np.float32)]
    for _ in range(levelcount - 1):
        g = blur5(blur5(lv[-1][:, :, 0], 0), 1)
        lv.append(np.repeat(g[::2, ::2, None], 4, axis=2).astype(np.float32))
    return lv


# --------------------------------------------------------------- GL pipeline

class AlignRunner:
    SAMPLERS = ("prevAlignment", "baseTexture", "alterTexture", "baseCurve", "alterCurve")

    def __init__(self, ctx, defines, raw_half, significancy=0.05, tile_al=16, prefilter_n=3.7417):
        self.ctx = ctx
        self.raw_half = raw_half
        self.significancy = significancy
        self.defines = dict(defines)
        self.tile_al = tile_al
        self.prefilter_n = prefilter_n
        self.prog = ctx.compute_shader(
            load_align_shader(defines, local_size=tile_al // 2))
        self.dummy = ctx.texture((1, 1), 4, dtype="f2")
        for s in self.SAMPLERS:
            self.setu(s, 0)

    def tex(self, arr):
        t = self.ctx.texture((arr.shape[1], arr.shape[0]), 4, dtype="f2")
        t.write(arr.astype(np.float16).tobytes())
        return t

    def setu(self, name, value):
        try:
            self.prog[name].value = value
        except KeyError:
            pass  # uniform compiled out for this config

    def run(self, base_levels, alter_levels, noise_s, noise_o, timing=None, max_levels=None):
        """Mirrors PyramidAlignment's coarse-to-fine loop. Mutates and returns
        the alter level textures (level i+1 receives alignment data).
        max_levels=n processes only the n finest levels (for experiments)."""
        base_t = [self.tex(l) for l in base_levels]
        alter_t = [self.tex(l) for l in alter_levels]
        p = self.prog
        w0, h0 = self.raw_half
        n = len(alter_t)
        start = n - 2 if max_levels is None else min(n - 2, max_levels - 2)
        for i in range(start, -1, -1):
            wi, hi = alter_t[i].size
            # noise scaling: sqrt(pixels averaged) per pyramid level, times
            # the prefilter's noise-reduction factor
            integral_norm = ((w0 * h0) / (alter_t[i + 1].size[0] * alter_t[i + 1].size[1])) ** 0.5 * self.prefilter_n
            first = 1 if i == n - 2 else 0
            base_t[i].use(1)
            alter_t[i].use(2)
            self.dummy.use(0)  # prevAlignment/baseCurve/alterCurve slots
            alter_t[i + 1].bind_to_image(0, read=False, write=True)
            self.setu("noiseS", noise_s)
            self.setu("noiseO", noise_o)
            self.setu("integralNorm", integral_norm)
            self.setu("significancy", self.significancy)
            self.setu("first", first)
            self.setu("rawHalf", tuple(self.raw_half))
            self.setu("exposure", 1.0)
            # sampler unit assignment (all dummies on 0 except inputs)
            for name, unit in (("baseTexture", 1), ("alterTexture", 2),
                               ("prevAlignment", 0), ("baseCurve", 0), ("alterCurve", 0)):
                self.setu(name, unit)
            if not first:
                alter_t[i + 2].use(0)
                self.setu("prevAlignment", 0)
            # Java: computeManual(gauss[i].w/(parameters.tile/2)+1) with parameters.tile = TILE_AL
            gx, gy = wi // (self.tile_al // 2) + 1, hi // (self.tile_al // 2) + 1
            t0 = time.perf_counter()
            p.run(gx, gy, 1)
            self.ctx.finish()
            dt = (time.perf_counter() - t0) * 1e3
            if timing is not None:
                timing.append((i, wi, hi, dt))
        # only the dispatch region (w/8+1 texels) is written; the rest of the
        # level texture is uninitialized and must not be evaluated
        valid = (min(alter_t[1].size[0], w0 // (self.tile_al // 2) + 1),
                 min(alter_t[1].size[1], h0 // (self.tile_al // 2) + 1))
        data = np.frombuffer(alter_t[1].read(), dtype=np.float16).reshape(
            alter_t[1].size[1], alter_t[1].size[0], 4).astype(np.float32)[:valid[1], :valid[0]]
        for t in base_t + alter_t:
            t.release()
        # decode alignmentToVec4: offset = floor + fract (in half-res px)
        return data[..., 0] * w0 + data[..., 2], data[..., 1] * h0 + data[..., 3]


# ----------------------------------------------------------------- benchmark

CONFIGS = {
    # old = shipped plain-SAD behaviour; others use the robust cost.
    # gate is k (sigmas) for the adaptive significance gate.
    "old":      dict(defines=dict(ROBUST=0, OFFSETS=5), gate=0.0, prefilter=0),
    "old_notrim": dict(defines=dict(ROBUST=0, OFFSETS=5), gate=0.0, prefilter=0, trimmed=0),
    "box":      dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9), gate=3.0, tile_al=16, prefilter=0),
    "gauto":    dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9), gate=3.0, tile_al=16),
    "box_t32":  dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9, TILE_AL=32), gate=2.0, tile_al=32, prefilter=0),
    "gauto_t32": dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9, TILE_AL=32), gate=2.0, tile_al=32),
    "g15_t32":  dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9, TILE_AL=32), gate=2.0, tile_al=32, sigma=1.5),
    "g10":      dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9), gate=3.0, tile_al=16, sigma=1.0),
    "g15":      dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9), gate=3.0, tile_al=16, sigma=1.5),
    "g20":      dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9), gate=3.0, tile_al=16, sigma=2.0),
    "robust9":  dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9), gate=3.0, tile_al=16),
    "l2_9":     dict(defines=dict(ROBUST=3, TRUNC=3.0, OFFSETS=9), gate=3.0, tile_al=16),
    "robust9_t32": dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9, TILE_AL=32), gate=3.0, tile_al=32),
    "robust9_t32_k2": dict(defines=dict(ROBUST=1, TRUNC=3.0, OFFSETS=9, TILE_AL=32), gate=2.0, tile_al=32),
    "l2_9_t32": dict(defines=dict(ROBUST=3, TRUNC=3.0, OFFSETS=9, TILE_AL=32), gate=3.0, tile_al=32),
}


def gaussian_prefactor(sigma):
    """1/sqrt(sum(w^2)) of the shader's separable 5-tap gaussian = the
    noise-reduction factor to feed integralNorm."""
    wx = np.exp(-(np.arange(5) - 2) ** 2 / (2 * sigma * sigma))
    wx /= wx.sum()
    return float(1.0 / np.sqrt((wx ** 2).sum() ** 2))


def auto_sigma(ns, no):
    """Java's noise-adaptive sigma: relative noise at signal 0.1."""
    rel = float(np.sqrt(0.1 * ns + no) / 0.1)
    return float(np.clip(0.9 + 1.1 * np.log2(rel / 0.08), 1.0, 2.0))


def evaluate(ox, oy, shift, border_frac=0.15):
    h, w = ox.shape
    bx, by = int(w * border_frac), int(h * border_frac)
    ex = ox[by:h - by, bx:w - bx] - shift[1]
    ey = oy[by:h - by, bx:w - bx] - shift[0]
    err = np.hypot(ex, ey)
    # scatter vs the median offset: a global constant bias (pyramid phase) is
    # benign for merging; tiles disagreeing with their neighbours are what
    # produces blocky artifacts.
    sx = ox[by:h - by, bx:w - bx] - np.median(ox[by:h - by, bx:w - bx])
    sy = oy[by:h - by, bx:w - bx] - np.median(oy[by:h - by, bx:w - bx])
    scat = np.hypot(sx, sy)
    return dict(median=float(np.median(err)), p90=float(np.percentile(err, 90)),
                bad2=float((err > 2).mean() * 100), bad4=float((err > 4).mean() * 100),
                scat_med=float(np.median(scat)), scat_p90=float(np.percentile(scat, 90)),
                scat_bad2=float((scat > 2).mean() * 100))


def bench(scene_name, config_name, size, shift, seed, reps, hot=0):
    """App-faithful pipeline: u16 raw burst -> actual normalize.glsl ->
    gaussian pyramid -> actual align.glsl."""
    import moderngl
    ctx = moderngl.create_context(standalone=True)
    w, h = size
    brightness, ns, no, lights = SCENES[scene_name]
    scene = make_scene(w, h, seed, brightness, lights)
    raw_b, raw_a = make_raw_burst(scene, ns, no, shift, seed + 2, hot_pixels=hot)
    cfg = CONFIGS[config_name]
    mode = cfg.get("prefilter", 1)
    sigma = cfg.get("sigma", 0.0)
    if sigma <= 0.01:
        sigma = auto_sigma(ns, no)
    prefilter_n = gaussian_prefactor(sigma) if mode == 1 else np.sqrt(14.0)
    nr = NormalizeRunner(ctx, dict(PREFILTER=mode), sigma=sigma)
    base = nr.run(raw_b)
    alter = nr.run(raw_a)
    levelcount = max(int(np.log(w) / np.log(2)) - 1, 2)
    base_lv = pyramid_levels(base, levelcount)
    alter_lv = pyramid_levels(alter, levelcount)
    runner = AlignRunner(ctx, cfg["defines"], (w, h), significancy=cfg["gate"],
                         tile_al=cfg.get("tile_al", 16), prefilter_n=prefilter_n)
    timing, result = [], None
    for r in range(reps):
        timing = []
        result = runner.run(base_lv, alter_lv, ns, no, timing)
    ox, oy = result
    metrics = evaluate(ox, oy, shift)
    total_ms = sum(t[3] for t in timing)
    ctx.release()
    return metrics, timing, total_ms


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scene", action="append", default=None, choices=list(SCENES))
    ap.add_argument("--config", action="append", default=None, choices=list(CONFIGS))
    ap.add_argument("--size", type=int, nargs=2, default=(640, 480))
    ap.add_argument("--shift", type=int, nargs=2, default=(6, -4), help="ground-truth shift in half-res px")
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--reps", type=int, default=3)
    ap.add_argument("--hot", type=int, default=0, help="inject N stuck-white hot pixels into the raw burst")
    ap.add_argument("--list-configs", action="store_true")
    args = ap.parse_args()
    if args.list_configs:
        for k, v in CONFIGS.items():
            print(f"{k:20s} {v}")
        return
    scenes = args.scene if args.scene else list(SCENES)
    configs = args.config if args.config else list(CONFIGS)
    shift = (args.shift[1], args.shift[0])  # (dy, dx) for np.roll axis order
    print(f"size={args.size[0]}x{args.size[1]} shift(dx,dy)={args.shift} seed={args.seed} reps={args.reps} hot={args.hot}")
    hdr = f"{'scene':<11}{'config':<20}{'|err|med':>9}{'|err|p90':>9}{'scat_med':>9}{'scat_p90':>9}{'scat%>2':>9}{'ms':>8}"
    print(hdr)
    print("-" * len(hdr))
    for sc in scenes:
        for cf in configs:
            m, timing, total = bench(sc, cf, tuple(args.size), shift, args.seed, args.reps, hot=args.hot)
            print(f"{sc:<11}{cf:<20}{m['median']:>9.2f}{m['p90']:>9.2f}"
                  f"{m['scat_med']:>9.2f}{m['scat_p90']:>9.2f}{m['scat_bad2']:>9.1f}{total:>8.1f}")
    print("\n(|err| = vs ground truth, scat = deviation from median offset = blocky-artifact indicator; half-res px)")


if __name__ == "__main__":
    main()
