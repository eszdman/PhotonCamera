#!/usr/bin/env python3
"""Synthetic multi-frame SR burst bench: the whole output-grid drizzle chain.

Unlike drizzle_recon_bench (which built a noiseless, dense, single-sample-per-
site drizzle and could only pass), this runs the device chain end to end on a
synthetic handheld burst:

    synthetic CFA/bayer frames (global motion, aperture MTF, noise)
      -> real merge/srluma per-site cross-channel luma     [raw grid]
      -> real merge/srscatter deposit                      [output grid]
      -> real merge/srlattice1 drizzle (holes / weights / CFA comb)
      -> real srpre/drizzlecompose (luma + crop chroma)
      -> real upscalecrop/anisoupscale at zoom 1
      -> metrics against the analytic ground truth

and reports where the "pixelated grid" the device shows with SR (and only with
SR) comes from: sparse deposit coverage, the CFA-phase luma mosaic, and a
reconstruction that does not know the local sample coverage.

Usage:
    python3 burst_bench.py --scene tones --frames 9 --exp 2
    python3 burst_bench.py --scene edge --frames 9 --cfa off
    python3 burst_bench.py --scene tones --frames 18 --splatbox 2
"""
import argparse
import os
import sys

import numpy as np
import moderngl

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
import sr_bench as SB  # noqa: E402  (load_compute/expand_imports/sinc)

ASSETS = SB.ASSETS


def f2(a):
    return np.ascontiguousarray(a.astype(np.float16)).tobytes()


# --------------------------------------------------------------------------
# synthetic sensor: scene -> packed CFA frames -> per-site luma
# --------------------------------------------------------------------------
def build_scene(args, rng):
    """Analytic scene components (cycles per OUTPUT px) and the sampling MTF."""
    if args.scene == "ramp":
        fx = np.array([0.030, 0.055, 0.013])
        fy = np.array([0.041, 0.019, 0.062])
        ph = rng.random(3) * 2 * np.pi
        amp = np.array([0.22, 0.14, 0.10])
    elif args.scene == "tones":
        count = 48
        f = 0.5 * (0.15 + 0.85 * rng.random(count))
        ang = rng.random(count) * 2 * np.pi
        fx = f * np.cos(ang)
        fy = f * np.sin(ang)
        ph = rng.random(count) * 2 * np.pi
        amp = 1.0 / (0.15 + f * 8.0)
    elif args.scene == "edge":
        count = 24
        f = 0.5 * (0.15 + 0.85 * rng.random(count))
        ang = rng.random(count) * 2 * np.pi
        fx = f * np.cos(ang)
        fy = f * np.sin(ang)
        ph = rng.random(count) * 2 * np.pi
        amp = 0.5 / (0.15 + f * 8.0)
    elif args.scene == "diag":
        # Pure straight diagonal edge: the scene for the staircase / rise-width
        # metrics (no tones - a level wobble would move the crossing).
        fx = np.array([])
        fy = np.array([])
        ph = np.array([])
        amp = np.array([])
    elif args.scene == "grad":
        # Smooth linear gradient (plus a trace of texture): the scene for the
        # banding / gradient-pixelation metrics.
        fx = np.array([0.018])
        fy = np.array([0.026])
        ph = rng.random(1) * 2 * np.pi
        amp = np.array([0.02])
    elif args.scene == "photo":
        # Real texture from the dump (loaded in main); no analytic components.
        fx = np.array([])
        fy = np.array([])
        ph = np.array([])
        amp = np.array([])
    else:
        raise SystemExit("unknown scene " + args.scene)
    fraw = np.hypot(fx, fy) * args.exp
    mtf = SB.sinc(fraw) * np.exp(-2 * np.pi ** 2 * SB.LENS_SIGMA_RAW ** 2 * fraw ** 2)
    scale = 0.70 / max(float(np.abs(amp).sum()), 1e-9)
    return fx, fy, ph, amp * scale, mtf


def struct_field(args, x, y):
    if args.scene not in ("edge", "diag"):
        return 0.0
    w = 0.5 * args.exp
    return 0.25 * np.tanh((x + 0.6 * y - 0.9 * args.raw * args.exp) / w)


def ramp_field(args, x, y):
    if args.scene != "grad":
        return 0.0
    return 0.30 * (x + 0.7 * y) / (args.raw * args.exp)


def load_photo(args):
    """Real-texture ground truth: a centered crop of the dump's fused lattice,
    one sample per output pixel, normalized. Synthetic tones hide lattice
    beats; real texture does not."""
    import json
    dump = os.path.normpath(os.path.join(HERE, "..", "..", "srsamples", "srdump"))
    meta = json.load(open(os.path.join(dump, "params.json")))
    m = meta["lattice"]
    a = np.frombuffer(open(os.path.join(dump, "lattice.raw"), "rb").read(),
                      dtype=np.float16).reshape(m["rh"], m["rw"], m["comp"])[..., 0]
    n = args.raw * args.exp
    y0 = max(0, m["rh"] // 2 - n // 2)
    x0 = max(0, m["rw"] // 2 - n // 2)
    p = a[y0:y0 + n, x0:x0 + n].astype(np.float64)
    if p.shape[0] < n or p.shape[1] < n:
        p = np.pad(p, ((0, max(0, n - p.shape[0])), (0, max(0, n - p.shape[1]))), mode="edge")
    p = p - p.mean()
    p = p / max(float(p.std()), 1e-9) * 0.35 + 1.2
    return p


def photo_field(args, x, y):
    p = getattr(args, "_photo", None)
    if p is None:
        return 0.0
    xc = np.clip(x - 0.5, 0.0, p.shape[1] - 1.001)
    yc = np.clip(y - 0.5, 0.0, p.shape[0] - 1.001)
    x0 = np.floor(xc).astype(int)
    y0 = np.floor(yc).astype(int)
    fx = xc - x0
    fy = yc - y0
    return (p[y0, x0] * (1 - fx) * (1 - fy) + p[y0, x0 + 1] * fx * (1 - fy)
            + p[y0 + 1, x0] * (1 - fx) * fy + p[y0 + 1, x0 + 1] * fx * fy)


def sample_scene(fx, fy, ph, amp, mtf, x, y, args):
    if args.scene == "photo":
        return photo_field(args, x, y)
    v = np.zeros(np.broadcast(x, y).shape)
    for k in range(len(fx)):
        v = v + amp[k] * mtf[k] * np.cos(2 * np.pi * (fx[k] * x + fy[k] * y) + ph[k])
    return v + struct_field(args, x, y) + ramp_field(args, x, y) + getattr(args, "dc", 1.2)


def scene_luma(fx, fy, ph, amp, x, y, args):
    if args.scene == "photo":
        return photo_field(args, x, y)
    v = np.zeros(np.broadcast(x, y).shape)
    for k in range(len(fx)):
        v = v + amp[k] * np.cos(2 * np.pi * (fx[k] * x + fy[k] * y) + ph[k])
    return v + struct_field(args, x, y) + ramp_field(args, x, y) + getattr(args, "dc", 1.2)


def make_packed(args, fx, fy, ph, amp, mtf, raw, dx, dy, rng, gain=1.0):
    """One frame's packed CFA quads at its warped raw-site positions."""
    i = np.arange(raw)
    X, Y = np.meshgrid(i * args.exp - dx, i * args.exp - dy)
    L = sample_scene(fx, fy, ph, amp, mtf, X, Y, args) * gain
    if args.chroma > 0:
        C = args.chroma * np.cos(2 * np.pi * (0.021 * X + 0.014 * Y) + 0.7)
    else:
        C = 0.0
    R = L + 0.4 * C
    G = L + 0.1 * C
    B = L - 0.5 * C
    ch = (np.arange(raw)[None, :] & 1) + (np.arange(raw)[:, None] & 1) * 2
    V = np.where(ch == 0, R, np.where(ch == 1, G, np.where(ch == 2, G, B)))
    if args.noise > 0:
        V = V + args.noise * rng.standard_normal((raw, raw))
    packed = np.empty((raw // 2, raw // 2, 4))
    packed[..., 0] = V[0::2, 0::2]
    packed[..., 1] = V[0::2, 1::2]
    packed[..., 2] = V[1::2, 0::2]
    packed[..., 3] = V[1::2, 1::2]
    return packed


def run_srluma(ctx, raw, packed, corrcap=0.0, nocap=False, luma8=False):
    """Real merge/srluma on a packed RGGB quad frame -> per-site luma/own.

    Prototypes: corrcap adds a Weber cap on ownCorr; nocap removes the shipped
    0.12 cap; luma8 uses an 8-point trimmed-mean same-colour estimate."""
    src = SB.load_compute("merge/srluma.glsl", local=(8, 8, 1))
    if nocap:
        cap_block = ("    {\n"
                     "        float corrCap = 0.12 * max(own / max(ownWP, 1e-4), 1e-4);\n"
                     "        ownCorr = corrCap * tanh(ownCorr / corrCap);\n"
                     "    }")
        if cap_block not in src:
            raise SystemExit("srluma nocap anchor not found")
        src = src.replace(cap_block, "")
    if luma8:
        # Prototype: 8-point same-colour estimate (cardinals + diagonals, all
        # same CFA parity) with a trimmed mean (drop one min and one max).
        old_loop = """    vec4 sOwnV = vec4(0.0);
    for (int k = 0; k < 4; k++) {
        ivec2 d = k == 0 ? ivec2(1, 0) : k == 1 ? ivec2(-1, 0)
                : k == 2 ? ivec2(0, 1) : ivec2(0, -1);
        sOwnV[k] = pick4(texelFetch(alterPacked, clamp(i0 + d, ivec2(0), texMax), 0), ch);
    }"""
        new_loop = """    float sOwn = 0.0;
    float sMin = 1e9;
    float sMax = -1e9;
    for (int k = 0; k < 8; k++) {
        ivec2 d = k == 0 ? ivec2(1, 0) : k == 1 ? ivec2(-1, 0)
                : k == 2 ? ivec2(0, 1) : k == 3 ? ivec2(0, -1)
                : k == 4 ? ivec2(1, 1) : k == 5 ? ivec2(1, -1)
                : k == 6 ? ivec2(-1, 1) : ivec2(-1, -1);
        float v = pick4(texelFetch(alterPacked, clamp(i0 + d, ivec2(0), texMax), 0), ch);
        sOwn += v;
        sMin = min(sMin, v);
        sMax = max(sMax, v);
    }
    vec4 sOwnV = vec4((sOwn - sMin - sMax) * (1.0 / 6.0), 0.0, 0.0, 0.0);"""
        if old_loop not in src:
            raise SystemExit("srluma luma8 loop anchor not found")
        src = src.replace(old_loop, new_loop)
        old_med = ("    float sOwnMed = 0.5 * (max(min(sOwnV.x, sOwnV.y), min(sOwnV.z, sOwnV.w))\n"
                   "            + min(max(sOwnV.x, sOwnV.y), max(sOwnV.z, sOwnV.w)));")
        if old_med not in src:
            raise SystemExit("srluma luma8 median anchor not found")
        src = src.replace(old_med, "    float sOwnMed = sOwnV.x;")
    if corrcap > 0:
        old = "    float ownCorr = (own - sOwnMed) / max(ownWP, 1e-4);"
        new = (old + "\n    float corrCap = %.4f * max(own / max(ownWP, 1e-4), 1e-4);\n"
               "    ownCorr = corrCap * tanh(ownCorr / corrCap);" % corrcap)
        if old not in src:
            raise SystemExit("srluma corrcap anchor not found")
        src = src.replace(old, new)
    ptex = ctx.texture((raw // 2, raw // 2), 4, f2(packed), dtype="f2")
    out = ctx.texture((raw, raw), 4, dtype="f2")
    out.bind_to_image(0, read=False, write=True)
    prog = ctx.compute_shader(src)
    prog["alterPacked"] = 0
    ptex.use(0)
    prog["srCfa"] = (0, 0)
    prog["srRw"] = (1.0, 0.0, 0.0, 0.0)
    prog["srGw"] = (0.0, 1.0, 1.0, 0.0)
    prog["srBw"] = (0.0, 0.0, 0.0, 1.0)
    prog["srWhitePoint"] = (1.0, 1.0, 1.0)
    prog["srLumaOut"] = 0
    prog.run(raw, raw, 1)
    arr = np.frombuffer(out.read(), dtype=np.float16).reshape(raw, raw, 4).astype(np.float64)
    ptex.release()
    out.release()
    return arr


def crop_from_packed(raw, packed):
    """Bilinear demosaic of a packed RGGB quad frame (the compose's chroma)."""
    L = np.empty((raw, raw))
    L[0::2, 0::2] = packed[..., 0]
    L[0::2, 1::2] = packed[..., 1]
    L[1::2, 0::2] = packed[..., 2]
    L[1::2, 1::2] = packed[..., 3]
    R = np.where(np.indices((raw, raw))[0] % 2 == 0, 0, 0).astype(float)
    return L


def demosaic_bilinear(raw, packed):
    """Crude bilinear demosaic: own channel where present, else the mean of the
    four same-channel neighbours (2 raw px away). Enough for chroma/holes."""
    ch = (np.arange(raw)[None, :] & 1) + (np.arange(raw)[:, None] & 1) * 2
    V = np.empty((raw, raw))
    V[0::2, 0::2] = packed[..., 0]
    V[0::2, 1::2] = packed[..., 1]
    V[1::2, 0::2] = packed[..., 2]
    V[1::2, 1::2] = packed[..., 3]
    rgb = np.zeros((raw, raw, 3))
    for c, sel in enumerate([ch == 0, (ch == 1) | (ch == 2), ch == 3]):
        own = np.where(sel, V, 0.0)
        cnt = sel.astype(float)
        acc = own.copy()
        nacc = cnt.copy()
        for d in ((0, 2), (0, -2), (2, 0), (-2, 0)):
            acc += np.roll(np.roll(own, d[0], 0), d[1], 1)
            nacc += np.roll(np.roll(cnt, d[0], 0), d[1], 1)
        rgb[..., c] = np.where(nacc > 0, acc / np.maximum(nacc, 1e-9), V)
    return rgb


# --------------------------------------------------------------------------
# real deposit (per-frame luma texture, motion warp, atomics)
# --------------------------------------------------------------------------
def resize_bilinear(a, n):
    """Bilinear resize of a square array to n x n (numpy only)."""
    m = a.shape[0]
    u = np.linspace(0.0, m - 1.0, n)
    i0 = np.floor(u).astype(int)
    i1 = np.clip(i0 + 1, 0, m - 1)
    fr = u - i0
    rows = a[i0] * (1.0 - fr)[:, None] + a[i1] * fr[:, None]
    return rows[:, i0] * (1.0 - fr)[None, :] + rows[:, i1] * fr[None, :]


def make_err_cells(args, asz, rng, f):
    """Residual alignment error per frame, in output px, on the app's per-cell
    (SR_TILE_AL = 16 raw px) grid. Models what the atlas/refine cannot absorb:
    a smooth misregistration field plus a parallax/rolling ramp."""
    if args.alignerr <= 0 or f == 0:
        return np.zeros((asz, asz, 2))
    base = np.stack([resize_bilinear(
        rng.standard_normal((max(2, asz // 2), max(2, asz // 2))), asz) for _ in range(2)], -1)
    base /= max(float(np.abs(base).max()), 1e-9)
    ramp = np.zeros((asz, asz, 2))
    if args.errfield in ("ramp", "both"):
        yy, xx = np.mgrid[0:asz, 0:asz] / max(asz - 1, 1)
        ramp[..., 0] = yy - 0.5
        ramp[..., 1] = 0.5 - xx
    e = np.zeros((asz, asz, 2))
    if args.errfield in ("random", "both"):
        e += base
    if args.errfield in ("ramp", "both"):
        e += 2.0 * ramp
    return e * args.alignerr


def make_trust(args, fx, fy, ph, amp, mtf, raw, err_cells):
    """Packed (diff, base) quad textures for the trust gate: the aligned alter's
    content vs the base, separated by the residual error field. This is what
    srscatter's own 3x3 signed-mean gate reads, so the REAL formula decides
    which sites contribute."""
    if err_cells is None:
        return None
    i = np.arange(raw)
    X, Y = np.meshgrid(i * args.exp, i * args.exp)
    base = sample_scene(fx, fy, ph, amp, mtf, X, Y, args)
    eq = np.stack([resize_bilinear(err_cells[..., c], raw // 2) for c in range(2)], -1)
    ex = np.kron(eq[..., 0], np.ones((2, 2)))
    ey = np.kron(eq[..., 1], np.ones((2, 2)))
    diff = sample_scene(fx, fy, ph, amp, mtf, X + ex, Y + ey, args)

    def pack(V):
        p = np.empty((raw // 2, raw // 2, 4))
        p[..., 0] = V[0::2, 0::2]
        p[..., 1] = V[0::2, 1::2]
        p[..., 2] = V[1::2, 0::2]
        p[..., 3] = V[1::2, 1::2]
        return p
    return pack(diff), pack(base)


def deposit_frames(ctx, raw, out, exp, lumas, deltas, trust=None, trust_p=None,
                   err_cells=None):
    """Run the real merge/srscatter deposit per frame.

    trust: per-frame (diffPacked, basePacked) RGBA16F quads at raw/2, or None
    (neutral gate). err_cells: per-frame (asz,asz,2) residual alignment error
    in output px, added to the atlas motion so the deposit lands at the
    misregistered position the gate is judging."""
    neutral = np.full((raw // 2, raw // 2, 4), 0.5, dtype=np.float16)
    base_tex = ctx.texture((raw // 2, raw // 2), 4, f2(neutral), dtype="f2")
    asz = max(1, raw // 16)
    atlas = ctx.texture((asz, asz), 4, dtype="f2")
    depv = ctx.texture((out, out), 1, dtype="u4")
    depw = ctx.texture((out, out), 1, dtype="u4")
    depv.bind_to_image(0, read=True, write=True)
    depw.bind_to_image(1, read=True, write=True)
    clear = ctx.compute_shader(SB.load_compute("merge/srclear.glsl"))
    clear["srClearA"] = 0
    clear["srClearB"] = 1
    clear.run(out, out, 1)
    try:
        sc = ctx.compute_shader(SB.load_compute("merge/srscatter.glsl"))
    except Exception:
        sc = ctx.compute_shader(SB.load_compute(
            "merge/srscatter.glsl", version="#version 430 core", drop_oes=True))
    sc["srLumaTex"] = 0
    sc["diffPacked"] = 2
    sc["basePacked"] = 3
    sc["alignmentTexture"] = 4
    sc["srRefMap"] = 5
    base_tex.use(2)
    base_tex.use(3)
    base_tex.use(5)
    sc["srDepV"] = 0
    sc["srDepW"] = 1
    sc["srRefine"] = 0.0
    sc["srFlowAlign"] = 0
    sc["srShift"] = (0, 0)
    sc["srAlignSize"] = (asz, asz)
    sc["srRawHalf"] = (raw // 2, raw // 2)
    sc["srCfa"] = (0, 0)
    sc["srFullPerOut"] = (1.0 / exp, 1.0 / exp)
    sc["srOrigin"] = (0.0, 0.0)
    sc["srExpose"] = 1.0
    sc["srJitter"] = 0.0
    sc["srMotionMax"] = 64.0
    tp = trust_p or {}
    sc["srTrustFloor"] = tp.get("floor", 0.0)
    sc["srTrustBand"] = tp.get("band", 0.05)
    sc["srNoiseS0"] = tp.get("s0", 0.0)
    sc["srNoiseO0"] = tp.get("o0", 0.0)
    sc["srClipAtten"] = tp.get("clip", 0.0)
    mot = np.zeros((asz, asz, 4), dtype=np.float16)
    for f in range(len(lumas)):
        dx, dy = deltas[f]
        ex = np.zeros((asz, asz)) if err_cells is None else err_cells[f][..., 0]
        ey = np.zeros((asz, asz)) if err_cells is None else err_cells[f][..., 1]
        mot[..., 2] = (dx - ex) / (2.0 * exp)
        mot[..., 3] = (dy - ey) / (2.0 * exp)
        atlas.write(f2(mot))
        ltex = ctx.texture((raw, raw), 4, f2(lumas[f]), dtype="f2")
        ltex.use(0)
        atlas.use(4)
        extra = []
        if trust is not None and f > 0 and trust[f] is not None:
            dtex = ctx.texture((raw // 2, raw // 2), 4, f2(trust[f][0]), dtype="f2")
            btex = ctx.texture((raw // 2, raw // 2), 4, f2(trust[f][1]), dtype="f2")
            dtex.use(2)
            btex.use(3)
            extra = [dtex, btex]
        sc["srZeroMotion"] = 1.0 if f == 0 else 0.0
        sc["srFrame"] = f
        sc.run(raw, raw, 1)
        ltex.release()
        for t in extra:
            t.release()
    dv = np.frombuffer(depv.read(), dtype=np.uint32).reshape(out, out).astype(np.float64) / SB.FIXED
    dw = np.frombuffer(depw.read(), dtype=np.uint32).reshape(out, out).astype(np.float64) / SB.FIXED
    for t in (base_tex, atlas, depv, depw):
        t.release()
    return dv, dw


# --------------------------------------------------------------------------
# real drizzle / compose / reconstruction
# --------------------------------------------------------------------------
def blur_droplet(a, kind, amount):
    """Convolve an accumulator with a separable box/Gaussian without scipy."""
    if amount <= 0:
        return a
    if kind == "box":
        n = max(1, int(round(amount)))
        k = np.ones(n, dtype=np.float64) / n
    else:
        r = max(1, int(np.ceil(3.0 * amount / 2.0)))
        x = np.arange(-r, r + 1)
        k = np.exp(-0.5 * (x / (amount / 2.0)) ** 2)
        k /= k.sum()
    n = len(k)
    pad = np.pad(a, ((n, n), (n, n)))
    out = np.zeros_like(a)
    for i, wv in enumerate(k):
        out += wv * pad[i:i + a.shape[0], n:n + a.shape[1]]
    tmp = np.zeros_like(a)
    pad2 = np.pad(out, ((0, 0), (n, n)))
    for i, wv in enumerate(k):
        tmp += wv * pad2[:, i:i + a.shape[1]]
    return tmp


def quant(a):
    return np.clip(np.round(a * SB.FIXED), 0, 4.0e9).astype(np.uint32)


def run_drizzle(ctx, out, exp, depv, depw, comb):
    tex_v = ctx.texture((out, out), 1, quant(depv).tobytes(), dtype="u4")
    tex_w = ctx.texture((out, out), 1, quant(depw).tobytes(), dtype="u4")
    dtex = ctx.texture((out, out), 1, dtype="f4")
    dtex.bind_to_image(0, read=False, write=True)
    prog = ctx.compute_shader(SB.load_compute(
        "merge/srlattice1.glsl", defines={"SR_CFA_COMB": "%.4f" % comb}))
    prog["srDepV"] = 0
    prog["srDepW"] = 1
    prog["fusedOut"] = 0
    tex_v.use(0)
    tex_w.use(1)
    try:
        prog["srCombOff"] = (2.0 * exp, 2.0 * exp)
    except KeyError:
        pass
    prog.run(out, out, 1)
    arr = np.frombuffer(dtex.read(), dtype=np.float32).reshape(out, out).astype(np.float64)
    for t in (tex_v, tex_w, dtex):
        t.release()
    return arr


def run_compose(ctx, drizzle, crop, exp):
    out = drizzle.shape[0]
    raw = crop.shape[0]
    ftex = ctx.texture((out, out), 1, np.ascontiguousarray(
        drizzle.astype(np.float32)).tobytes(), dtype="f4")
    ctex = ctx.texture((raw, raw), 4, f2(np.stack(
        [crop[..., 0], crop[..., 1], crop[..., 2], np.ones_like(crop[..., 0])], -1)), dtype="f2")
    src = SB.expand_imports(open(os.path.join(
        ASSETS, "srpre/drizzlecompose.glsl")).read().lstrip("\n"))
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    prog = ctx.program(vertex_shader=vsrc, fragment_shader="#version 310 es\n" + src)
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())
    try:
        vao = ctx.vertex_array(prog, [(verts, "2f", "in_pos")])
    except Exception:
        vao = ctx.simple_vertex_array(prog, verts, "in_pos")
    tgt = ctx.texture((out, out), 4, dtype="f2")
    fbo = ctx.framebuffer(color_attachments=[tgt])
    prog["InputBuffer"] = 0
    ctex.use(0)
    prog["FusedLuma"] = 1
    ftex.use(1)
    prog["srBlack"] = (0.0, 0.0, 0.0)
    prog["srPerOut"] = (1.0 / exp, 1.0 / exp)
    fbo.use()
    fbo.clear(0.0, 0.0, 0.0, 1.0)
    vao.render(moderngl.TRIANGLES)
    o = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                      ).reshape(out, out, 4)[..., :3].astype(np.float64)
    for t in (ftex, ctex, tgt):
        t.release()
    return o


def run_band(ctx, out, exp, depv, depw, aniso, args):
    """The device's SR band layer at the output grid, through the REAL
    shaders: merge/srrecover on the deposit + srpre/band.glsl with
    srBandMode = 0 (replace; raw-grid behavior) or 1 (deconv; drizzle path).

    mode=replace re-injects the deposit residue on the drizzle path (proved:
    5.6x the artifact on a scene with no HF content). mode=deconv keeps the
    recovery's gate and gain but applies them to the reconstruction's own
    band: aniso + gate*(gain-1)*DoG(aniso)."""
    tex_v = ctx.texture((out, out), 1, quant(depv).tobytes(), dtype="u4")
    tex_w = ctx.texture((out, out), 1, quant(depw).tobytes(), dtype="u4")
    btex = ctx.texture((out, out), 1, dtype="u4")
    btex.bind_to_image(0, read=False, write=True)
    prog = ctx.compute_shader(SB.load_compute(
        "merge/srrecover.glsl", gain_max=args.gainmax,
        defines={"SR_GATE_K": "%.4f" % args.gatek,
                 "SR_F_RAW_NYQ": "%.4f" % args.fnyq,
                 "SR_F_RAW_TOP": "%.4f" % args.ftop}))
    prog["srDepV"] = 0
    prog["srDepW"] = 1
    tex_v.use(0)
    tex_w.use(1)
    prog["srPerOut"] = (1.0 / exp, 1.0 / exp)
    prog["srNoiseS0"] = args.noises0
    prog["srNoiseO0"] = args.noiseo0 if args.noiseo0 >= 0 else args.noise ** 2
    prog["srCoverageRef"] = max(1.0, args.frames / float(exp * exp))
    prog["srBandOut"] = 0
    prog.run(out, out, 1)
    bw = np.frombuffer(btex.read(), dtype=np.uint32).reshape(out, out)
    bh = bw.reshape(out, out, 1).view(np.float16).reshape(out, out, 2)
    band = bh[..., 0].astype(np.float64)
    gate = bh[..., 1].astype(np.float64)
    src = SB.expand_imports(open(os.path.join(ASSETS, "srpre/band.glsl")).read().lstrip("\n"))
    # Mirror the recovery's knobs into the band shader (both must agree).
    src = src.replace("#define SR_F_RAW_NYQ 0.35", "#define SR_F_RAW_NYQ %.4f" % args.fnyq)
    src = src.replace("#define SR_F_RAW_TOP 0.75", "#define SR_F_RAW_TOP %.4f" % args.ftop)
    src = src.replace("#define SR_GAIN_MAX 2.2", "#define SR_GAIN_MAX %.4f" % args.gainmax)
    if args.gsplit > 0.0:
        # Override the shipped frequency split (1.0 = uniform gain).
        src = src.replace("#define SR_BAND_SPLIT 0.3",
                          "#define SR_BAND_SPLIT %.4f" % args.gsplit)
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    bprog = ctx.program(vertex_shader=vsrc, fragment_shader="#version 310 es\n" + src)
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())
    try:
        bvao = ctx.vertex_array(bprog, [(verts, "2f", "in_pos")])
    except Exception:
        bvao = ctx.simple_vertex_array(bprog, verts, "in_pos")
    itex = ctx.texture((out, out), 4, f2(np.stack(
        [aniso, aniso, aniso, np.ones_like(aniso)], -1)), dtype="f2")
    otgt = ctx.texture((out, out), 4, dtype="f2")
    fbo = ctx.framebuffer(color_attachments=[otgt])
    bprog["InputBuffer"] = 0
    itex.use(0)
    bprog["BandMap"] = 1
    btex.use(1)
    bprog["srPerOut"] = (1.0 / exp, 1.0 / exp)
    bprog["srBlack"] = (0.0, 0.0, 0.0)
    bprog["u_inOrigin"] = (0, 0)
    bprog["u_tileOrigin"] = (0, 0)
    bprog["srBandMode"] = 1.0 if args.bandmode == "deconv" else 0.0
    fbo.use()
    fbo.clear(0.0, 0.0, 0.0, 1.0)
    bvao.render(moderngl.TRIANGLES)
    fin = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                        ).reshape(out, out, 4)[..., 0].astype(np.float64)
    for t in (tex_v, tex_w, btex, itex, otgt):
        t.release()
    return fin, band, gate


def build_kmap(args, msz, kmap_vals):
    kmap = np.zeros((msz, msz, 4), dtype=np.float16)
    if args.kmap == "real":
        # The dump's real KernelNet map (raw/4, same physical texel pitch as
        # the synthetic msz grid): a centered crop keeps the real statistics.
        import json
        dump = os.path.normpath(os.path.join(HERE, "..", "..", "srsamples", "srdump"))
        meta = json.load(open(os.path.join(dump, "params.json")))
        m = meta["kernels"]
        a = np.frombuffer(open(os.path.join(dump, "kernels.raw"), "rb").read(),
                          dtype=np.float16).reshape(m["rh"], m["rw"], m["comp"])
        y0 = (m["rh"] - msz) // 2
        x0 = (m["rw"] - msz) // 2
        kmap[..., :] = a[y0:y0 + msz, x0:x0 + msz, :].astype(np.float16)
        print("      real map crop: s1 %.3f..%.3f s2 %.3f..%.3f rho %.2f..%.2f"
              % (float(kmap[..., 0].min()), float(kmap[..., 0].max()),
                 float(kmap[..., 1].min()), float(kmap[..., 1].max()),
                 float(kmap[..., 2].min()), float(kmap[..., 2].max())))
    elif args.kmap == "blocky":
        r = np.random.default_rng(3)
        kv = r.uniform(0.05, 0.5, (msz, msz))
        kmap[..., 0] = kv
        kmap[..., 1] = kv
    elif args.kmap == "blockedge":
        yy, xx = np.mgrid[0:msz, 0:msz]
        alt = ((xx + yy) & 1).astype(np.float16)
        kmap[..., 0] = 0.6 * alt + 0.05
        kmap[..., 1] = 0.6 * (1 - alt) + 0.05
    else:
        kmap[..., 0] = kmap_vals[0]
        kmap[..., 1] = kmap_vals[1]
    kmap[..., 3] = 1.0
    return kmap


def run_disabled_kern(ctx, crop, raw, out, exp, args, kmap_vals):
    """The real Disabled path: the KernelNet anisotropic reconstruction at
    zoom=exp on the raw demosaiced crop (legacy geometry), same real map, the
    un-boosted acutance. The fair reference for edge/rise/delivery gates."""
    src = SB.expand_imports(open(os.path.join(
        ASSETS, "upscalecrop/anisoupscale.glsl")).read().lstrip("\n"))
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    prog = ctx.program(vertex_shader=vsrc, fragment_shader="#version 310 es\n" + src)
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())
    try:
        vao = ctx.vertex_array(prog, [(verts, "2f", "in_pos")])
    except Exception:
        vao = ctx.simple_vertex_array(prog, verts, "in_pos")
    msz = max(2, raw // 4)
    kmap = build_kmap(args, msz, kmap_vals)
    mtex = ctx.texture((msz, msz), 4, f2(kmap), dtype="f2")
    itex = ctx.texture((raw, raw), 4, f2(np.stack(
        [crop[..., 0], crop[..., 1], crop[..., 2], np.ones((raw, raw))], -1)), dtype="f2")
    tgt = ctx.texture((out, out), 4, dtype="f2")
    fbo = ctx.framebuffer(color_attachments=[tgt])
    prog["InputBuffer"] = 0
    itex.use(0)
    prog["KernelsMap"] = 1
    mtex.use(1)
    prog["fullSize"] = (out, out)
    prog["u_tileOrigin"] = (0, 0)
    prog["u_winOrigin"] = (0, 0)
    prog["u_winFullSize"] = (float(raw), float(raw))
    prog["sigmaScale"] = 0.55                       # device Disabled value
    zoom = raw / float(out)
    prog["sigmaMinPx"] = (max(0.25, 0.4 * zoom),) * 2
    prog["sigmaMaxPx"] = 1.0
    prog["strength"] = 1.0
    prog["sharpAmt"] = 1.4                          # device Disabled value
    prog["sharpWide"] = 2.2
    prog["acutRel"] = 0.04
    prog["maxElong"] = 8.0
    prog["gateExp"] = 0.3
    prog["srElongCap"] = 1.0
    prog["scaleRatio"] = (float(exp), float(exp))
    prog["splitChroma"] = 1
    prog["kernelRadius"] = 5
    prog["debugMode"] = 0
    fbo.use()
    fbo.clear(0.0, 0.0, 0.0, 1.0)
    vao.render(moderngl.TRIANGLES)
    dis = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                        ).reshape(out, out, 4)[..., 0].astype(np.float64)
    for t in (itex, mtex, tgt):
        t.release()
    return dis


def run_recon(ctx, composed, out, raw, args, kmap_vals, alpha=None):
    src = SB.expand_imports(open(os.path.join(
        ASSETS, "upscalecrop/anisoupscale.glsl")).read().lstrip("\n"))
    if args.covadapt > 0.0:
        # Prototype of the coverage-adaptive kernel: the composed alpha carries
        # the deposit coverage, and the reconstruction kernel widens where the
        # real sample count is low (the device would carry w through the
        # drizzle and expose it the same way).
        old = "    vec2 sigmas;\n    vec3 abc = anisoCoeffs(uv, sigmas);"
        new = ("    vec2 sigmas;\n    vec3 abc = anisoCoeffs(uv, sigmas);\n"
               "    float covA = clamp(texture(InputBuffer, uvWin).a, 0.0, 1.0);\n"
               "    float sBoost = mix(%.3f, 1.0, covA);\n"
               "    abc /= (sBoost * sBoost);\n"
               "    sigmas *= sBoost;" % args.covadapt)
        if old not in src:
            raise SystemExit("covadapt anchor not found")
        src = src.replace(old, new)
    if args.elongcap <= 0.0:
        args.elongcap = 1.0
    if args.acutrelneg > 0.0:
        # Prototype: asymmetric Weber cap - the dark-side undershoot (the
        # visible halo) gets the smaller cap, the bright side keeps acutRel.
        old = "        vec3 cap = max(vec3(acutRel) * aniso.rgb, vec3(1e-4));"
        new = ("        vec3 cap = max(mix(vec3(%.4f), vec3(acutRel), "
               "step(vec3(0.0), d.rgb)) * aniso.rgb, vec3(1e-4));" % args.acutrelneg)
        if old not in src:
            raise SystemExit("acutrelneg anchor not found")
        src = src.replace(old, new)
    if args.covsharp > 0.0:
        # Prototype: coverage-aware deconvolution. The deposit's averaging PSF
        # widens with coverage (deposit MTF area 1.037 at 9f -> 0.667 at 40f),
        # so narrow the reconstruction kernel where coverage is high; low
        # coverage keeps the base sigma. covA = composed alpha (normalized w).
        old = "    vec2 sigmas;\n    vec3 abc = anisoCoeffs(uv, sigmas);"
        new = (old + "\n    float covA = clamp(texture(InputBuffer, uvWin).a, 0.0, 1.0);\n"
               "    float sBoost = mix(1.0, %.3f, covA);\n"
               "    abc /= (sBoost * sBoost);\n"
               "    sigmas *= sBoost;" % args.covsharp)
        if old not in src:
            raise SystemExit("covsharp anchor not found")
        src = src.replace(old, new)
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    prog = ctx.program(vertex_shader=vsrc, fragment_shader=(
        "#version 310 es\n#define SR_ACUT_FLAT %.4f\n" % args.acutflat + src))
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())
    try:
        vao = ctx.vertex_array(prog, [(verts, "2f", "in_pos")])
    except Exception:
        vao = ctx.simple_vertex_array(prog, verts, "in_pos")
    # Device geometry: the composed input is at the target grid; the KernelNet
    # map is at raw/4. cropSz/mapSize * sigmaScale then clamps to sigmaMaxPx.
    msz = max(2, raw // 4)
    kmap = build_kmap(args, msz, kmap_vals)
    mtex = ctx.texture((msz, msz), 4, f2(kmap), dtype="f2")
    a = np.ones((out, out)) if alpha is None else alpha
    itex = ctx.texture((out, out), 4, f2(np.stack(
        [composed[..., 0], composed[..., 1], composed[..., 2], a], -1)), dtype="f2")
    tgt = ctx.texture((out, out), 4, dtype="f2")
    fbo = ctx.framebuffer(color_attachments=[tgt])
    prog["InputBuffer"] = 0
    itex.use(0)
    prog["KernelsMap"] = 1
    mtex.use(1)
    prog["fullSize"] = (out, out)
    prog["u_tileOrigin"] = (0, 0)
    prog["u_winOrigin"] = (0, 0)
    prog["u_winFullSize"] = (float(out), float(out))
    prog["sigmaScale"] = args.sigmascale * args.srmult
    prog["sigmaMinPx"] = (args.sigmamin, args.sigmamin)
    prog["sigmaMaxPx"] = args.sigmamax
    prog["strength"] = 1.0
    prog["sharpAmt"] = args.sharpamt
    prog["sharpWide"] = args.sharpwide
    prog["acutRel"] = args.acutrel
    prog["maxElong"] = 8.0
    prog["gateExp"] = args.gateexp
    prog["srElongCap"] = args.elongcap
    prog["scaleRatio"] = (1.0, 1.0)
    prog["splitChroma"] = 1
    prog["kernelRadius"] = 5
    prog["debugMode"] = 0
    fbo.use()
    fbo.clear(0.0, 0.0, 0.0, 1.0)
    vao.render(moderngl.TRIANGLES)
    rec = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                        ).reshape(out, out, 4)[..., 0].astype(np.float64)
    eff = tuple(float(np.clip(v * (out / msz) * args.sigmascale * args.srmult,
                              args.sigmamin, args.sigmamax)) for v in kmap_vals)
    for t in (itex, mtex, tgt):
        t.release()
    return rec, eff


# --------------------------------------------------------------------------
# geometry / metrics
# --------------------------------------------------------------------------
def fft_shift(z, sx, sy):
    F = np.fft.fft2(z)
    ky = np.fft.fftfreq(z.shape[0])[:, None]
    kx = np.fft.fftfreq(z.shape[1])[None, :]
    return np.real(np.fft.ifft2(F * np.exp(-2j * np.pi * (kx * sx + ky * sy))))


def fit_shift(rec, ref, span=2.5, step=0.125):
    best = (None, 0.0, 0.0)
    for sy in np.arange(-span, span + 1e-9, step):
        for sx in np.arange(-span, span + 1e-9, step):
            sh = fft_shift(rec, sx, sy)
            e = float(np.mean((sh - ref) ** 2))
            if best[0] is None or e < best[0]:
                best = (e, float(sx), float(sy))
    _, sx, sy = best
    return fft_shift(rec, sx, sy), sx, sy


def fft_band(z, f0, f1):
    a = z - z.mean()
    h0, w0 = a.shape
    win = np.outer(np.hanning(h0), np.hanning(w0))
    F = np.fft.fftshift(np.fft.fft2(a * win))
    h, w = F.shape
    cy, cx = h // 2, w // 2
    Y, X = np.mgrid[0:h, 0:w]
    r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
    F[(r < f0) | (r >= f1)] = 0
    return np.real(np.fft.ifft2(np.fft.ifftshift(F)))


def hp_rms(z, f0=0.30, f1=0.49):
    return float(np.sqrt(np.mean(fft_band(z, f0, f1) ** 2)))


def bilin(img, xc, yc):
    x = xc - 0.5
    y = yc - 0.5
    x0 = int(np.floor(x))
    y0 = int(np.floor(y))
    fx = x - x0
    fy = y - y0
    x0 = int(np.clip(x0, 0, img.shape[1] - 2))
    y0 = int(np.clip(y0, 0, img.shape[0] - 2))
    return (img[y0, x0] * (1 - fx) * (1 - fy) + img[y0, x0 + 1] * fx * (1 - fy)
            + img[y0 + 1, x0] * (1 - fx) * fy + img[y0 + 1, x0 + 1] * fx * fy)


def edge_metrics(img, out, exp, nx=1.0, ny=0.6, c_frac=0.9):
    """Sub-pixel edge position per row and the 10-90% rise width across the
    straight diagonal edge x + 0.6y = c_frac*out. Staircase = RMS deviation of
    the measured position from its best-fit line (offset/slope removed): a
    clean anti-aliased edge is ~0, jaggies show at the sample pitch."""
    c = c_frac * out
    ys = np.arange(8, out - 8)
    mid = 0.5 * (np.percentile(img, 5) + np.percentile(img, 95))
    pos = []
    for y in ys:
        xe = (c - ny * (y + 0.5)) / nx
        x0 = max(0, int(np.floor(xe)) - 12)
        x1 = min(out - 1, int(np.floor(xe)) + 13)
        if x1 <= x0 + 2:
            pos.append(np.nan)
            continue
        prof = img[y, x0:x1 + 1]
        s = np.sign(prof - mid)
        idx = np.where(np.diff(s) != 0)[0]
        if len(idx) == 0:
            pos.append(np.nan)
            continue
        i = idx[0]
        v0, v1 = prof[i], prof[i + 1]
        pos.append(x0 + i + (mid - v0) / (v1 - v0 + 1e-12))
    pos = np.array(pos)
    good = ~np.isnan(pos)
    pos = pos[good]
    ys = ys[good].astype(float)
    if len(pos) < 16:
        return np.nan, np.nan
    A = np.vstack([ys, np.ones_like(ys)]).T
    coef, *_ = np.linalg.lstsq(A, pos, rcond=None)
    stair = float(np.sqrt(np.mean((pos - A @ coef) ** 2)))
    xs = None
    profs = []
    for y in ys[::4].astype(int):
        xe = (c - ny * (y + 0.5)) / nx
        xs = np.arange(xe - 6.0, xe + 6.0 + 1e-9, 0.5)
        profs.append([bilin(img, xv, y + 0.5) for xv in xs])
    P = np.mean(profs, 0)
    lo = float(np.mean(P[:3]))
    hi = float(np.mean(P[-3:]))
    Q = (P - lo) / (hi - lo + 1e-12)
    over_b = float(max(Q.max() - 1.0, 0.0))     # bright-side overshoot
    over_d = float(max(-Q.min(), 0.0))          # dark-side undershoot (halo)
    over = max(over_b, over_d)
    P = (P - P.min()) / (P.max() - P.min() + 1e-12)

    def cross(level):
        for i in range(len(P) - 1):
            if (P[i] - level) * (P[i + 1] - level) <= 0:
                return xs[i] + (level - P[i]) / (P[i + 1] - P[i] + 1e-12) * (xs[i + 1] - xs[i])
        return np.nan
    rise = float(abs(cross(0.9) - cross(0.1)))
    return stair, rise, over, over_b, over_d


def align(rec, ref):
    return fit_shift(rec, ref)


def mtf_curve_of(img, ref):
    a = img - img.mean()
    b = ref - ref.mean()
    win = np.outer(np.hanning(a.shape[0]), np.hanning(a.shape[1]))
    A = np.fft.fftshift(np.fft.fft2(a * win))
    B = np.fft.fftshift(np.fft.fft2(b * win))
    num = np.real(A * np.conj(B))
    den = np.abs(B) ** 2
    h, w = A.shape
    cy, cx = h // 2, w // 2
    Y, X = np.mgrid[0:h, 0:w]
    r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
    edges = np.linspace(0.0, 0.5, 26)
    mtf = []
    for i in range(len(edges) - 1):
        msk = (r >= edges[i]) & (r < edges[i + 1])
        mtf.append(float(num[msk].sum() / max(den[msk].sum(), 1e-12)))
    return 0.5 * (edges[:-1] + edges[1:]), np.array(mtf)


def mtf_metrics_of(img, ref):
    """Robust detail metrics vs the reference: the smoothed curve's HIGHEST
    frequency at or above 0.5 (the first-crossing variant is bistable on a
    non-monotonic curve - the SR recovers a real band above the raw Nyquist -
    and the fixed-point deposit atomics add run-to-run jitter), plus the
    coherent MTF area over [0.05, 0.45]."""
    centers, mtf = mtf_curve_of(img, ref)
    k = np.array([0.25, 0.5, 0.25])
    sm = np.convolve(mtf, k, mode="same")
    f50 = float(centers[0])
    for i in range(len(sm) - 1, 0, -1):
        if sm[i] >= 0.5:
            if sm[i - 1] < 0.5:
                f50 = float(centers[i - 1] + (0.5 - sm[i - 1]) *
                            (centers[i] - centers[i - 1]) / (sm[i] - sm[i - 1] + 1e-12))
            else:
                f50 = float(centers[i])
            break
    msk = (centers >= 0.05) & (centers <= 0.45)
    area = float(sm[msk].mean())
    return f50, area


def report(tag, rec, truth, depw, composed, crop, out, exp, args, truth_ap=None,
           dis_img=None):
    m = (slice(8, -8), slice(8, -8))
    rec_a, sx, sy = align(rec, truth)
    resid = rec_a - truth
    art_hf = float(np.sqrt(np.mean(fft_band(resid, 0.30, 0.49)[m] ** 2)))
    rec_hf = float(np.sqrt(np.mean(fft_band(rec_a, 0.30, 0.49)[m] ** 2)))
    tr_hf = float(np.sqrt(np.mean(fft_band(truth, 0.30, 0.49)[m] ** 2)))
    res_rms = float(np.sqrt(np.mean(resid[m] ** 2)))
    # deposit-only image (compose of the raw drizzle, no reconstruction)
    comp_a, _, _ = align(composed[..., 0], truth)
    dep_hf = float(np.sqrt(np.mean(fft_band(comp_a - truth, 0.30, 0.49)[m] ** 2)))
    mtf_ref0 = truth_ap if truth_ap is not None else truth
    dep50, dep_area = mtf_metrics_of(comp_a, mtf_ref0)
    # Disabled reference: the real KernelNet legacy path when provided, else a
    # plain bicubic of the raw crop (the old, unfairly soft proxy).
    from PIL import Image
    if dis_img is None:
        dis = np.asarray(Image.fromarray(crop[..., 0].astype(np.float32)).resize(
            (out, out), Image.BICUBIC), dtype=np.float64)
    else:
        dis = dis_img
    dis_a, _, _ = align(dis, truth)
    dis_res = dis_a - truth
    dis_hf = float(np.sqrt(np.mean(fft_band(dis_res, 0.30, 0.49)[m] ** 2)))
    mtf_ref = truth_ap if truth_ap is not None else truth
    mtf50_sr, mtf_area_sr = mtf_metrics_of(rec_a, mtf_ref)
    mtf50_dis, mtf_area_dis = mtf_metrics_of(dis_a, mtf_ref)
    if getattr(args, "mtfcurve", False):
        cs, ms = mtf_curve_of(rec_a, mtf_ref)
        cd, md = mtf_curve_of(dis_a, mtf_ref)
        print("      MTF curve (cyc/out): " + " ".join(
            "%.2f:%.2f/%.2f" % (cs[i], ms[i], md[i]) for i in range(len(cs))))
    # Grid isolation: what SR changed relative to an aligned Disabled. Global
    # truth-referenced metrics are dominated by missing content, so they hide a
    # lattice; this difference shows it directly.
    d = rec_a - dis_a
    d_lat = float(np.sqrt(np.mean(fft_band(d, 0.42, 0.499)[m] ** 2)))
    d_mid = float(np.sqrt(np.mean(fft_band(d, 0.25, 0.499)[m] ** 2)))
    fmap = 1.0 / (4.0 * exp)          # the raw/4 KernelNet map pitch
    d_map = float(np.sqrt(np.mean(fft_band(d, fmap - 0.02, fmap + 0.02)[m] ** 2)))
    dd = d[m]
    den = max(float(np.mean(dd ** 2)), 1e-12)
    pixac = 0.0
    for (dy, dx) in ((0, exp), (exp, 0), (exp, exp)):
        pixac += float(np.mean(dd * np.roll(np.roll(d, dy, 0), dx, 1)[m])) / den
    pixac /= 3.0
    w = depw
    meanw = float(w[m].mean())
    zero = float((w < 1e-6).mean())
    frac10 = float((w[m] < 1.0).mean())
    frac20 = float((w[m] < 2.0).mean())
    hp = np.abs(fft_band(resid, 0.30, 0.49))
    art_top = float(np.sqrt(np.mean(fft_band(resid, 0.42, 0.499)[m] ** 2)))
    qs = np.quantile(w[m], [0.25, 0.5, 0.75])
    q = np.digitize(w, qs)
    by = [float(np.sqrt(np.mean(hp[m][q[m] == k] ** 2))) if (q[m] == k).any() else 0.0
          for k in range(4)]
    print("  [%s] resid=%.4f | artifactHF=%.4f | recHF=%.4f truthHF=%.4f disHF=%.4f "
          "| drizzleHF=%.4f | artTop(0.42-0.5)=%.4f | shift=(%+.2f,%+.2f)"
          % (tag, res_rms, art_hf, rec_hf, tr_hf, dis_hf, dep_hf, art_top, sx, sy))
    print("      coverage meanW=%.3f zero=%.4f p<1=%.4f p<2=%.4f | artifact by cov "
          "quartile [%.4f %.4f %.4f %.4f]"
          % (meanw, zero, frac10, frac20, by[0], by[1], by[2], by[3]))
    print("      grid |SR-Disabled|: lat(0.42-0.5)=%.4f mid=%.4f map(%.3f)=%.4f "
          "sample-pitch ac=%+.2f | MTF50 SR=%.3f Dis=%.3f area SR=%.3f Dis=%.3f"
          % (d_lat, d_mid, fmap, d_map, pixac, mtf50_sr, mtf50_dis,
             mtf_area_sr, mtf_area_dis))
    print("      MTF deposit (drizzle only): 50=%.3f area=%.3f" % (dep50, dep_area))
    edge_m = None
    grad_m = None
    below_m = None
    if args.scene in ("edge", "diag"):
        st_sr, ri_sr, ov_sr, ob_sr, od_sr = edge_metrics(rec_a, out, exp)
        st_dis, ri_dis, ov_dis, ob_dis, od_dis = edge_metrics(dis_a, out, exp)
        st_tr, ri_tr, ov_tr, ob_tr, od_tr = edge_metrics(truth, out, exp)
        print("      edge: staircase SR=%.3f Dis=%.3f truth=%.3f px | "
              "rise10-90 SR=%.2f Dis=%.2f truth=%.2f px | overshoot SR=%.1f%% "
              "(bright %.1f dark %.1f) Dis=%.1f%% truth=%.1f%%"
              % (st_sr, st_dis, st_tr, ri_sr, ri_dis, ri_tr,
                 100.0 * ov_sr, 100.0 * ob_sr, 100.0 * od_sr,
                 100.0 * ov_dis, 100.0 * ov_tr))
        edge_m = dict(stair=st_sr, rise=ri_sr, over=ov_sr, over_b=ob_sr,
                      over_d=od_sr, stair_dis=st_dis, rise_dis=ri_dis,
                      over_dis=ov_dis)
    if args.scene == "grad":
        # Banding: periodic structure of the HIGH-PASSED residual along the
        # gradient direction (the smooth MTF-deficit residual would otherwise
        # dominate any autocorrelation). SR vs Disabled.
        def band_ac(img):
            rr = (img - truth)[m]
            rhp = fft_band(rr, 0.20, 0.499)
            rhp = rhp - rhp.mean()
            den = float(np.mean(rhp ** 2)) + 1e-12
            acs = []
            for L in range(2, 9):
                sx = int(round(L * 0.819))
                sy = int(round(L * 0.573))
                if sx == 0 and sy == 0:
                    continue
                sh = np.roll(np.roll(rhp, sy, 0), sx, 1)
                acs.append(abs(float(np.mean(rhp * (sh - sh.mean()))) / den))
            return (max(acs) if acs else 0.0), float(np.sqrt(np.mean(rhp ** 2)))
        ac_sr, hf_sr = band_ac(rec_a)
        ac_dis, hf_dis = band_ac(dis_a)
        print("      gradient: banding autocorr SR=%.2f (hf=%.4f) Dis=%.2f (hf=%.4f) "
              "| resid rms=%.4f" % (ac_sr, hf_sr, ac_dis, hf_dis, res_rms))
        grad_m = dict(banding=ac_sr, hf=hf_sr, banding_dis=ac_dis, hf_dis=hf_dis)
    del_ratio = del_dis = None
    c_sr = c_lo = c_hi = c_dis = None
    if truth_ap is not None:
        # Fair delivery: the samples are aperture-integrated, so the ideal
        # reconstruction is the aperture-filtered scene, not the pure scene.
        ref = (slice(8, -8), slice(8, -8))
        f0, f1 = 0.25, 0.499
        e_rec = float(np.sqrt(np.mean(fft_band(rec_a, f0, f1)[ref] ** 2)))
        e_tap = float(np.sqrt(np.mean(fft_band(truth_ap, f0, f1)[ref] ** 2)))
        e_dis = float(np.sqrt(np.mean(fft_band(dis_a, f0, f1)[ref] ** 2)))
        if e_tap > 1e-5:
            del_ratio = e_rec / e_tap
            del_dis = e_dis / e_tap
            err_ap = float(np.sqrt(np.mean(fft_band(
                rec_a - truth_ap, f0, f1)[ref] ** 2)))
            err_ap_dis = float(np.sqrt(np.mean(fft_band(
                dis_a - truth_ap, f0, f1)[ref] ** 2)))
            bs = fft_band(rec_a, f0, f1)[ref]
            bd = fft_band(dis_a, f0, f1)[ref]
            ba = fft_band(truth_ap, f0, f1)[ref]
            c_sr = float(np.corrcoef(bs.ravel(), ba.ravel())[0, 1])
            c_dis = float(np.corrcoef(bd.ravel(), ba.ravel())[0, 1])
            # Sub-bands: the aperture MTF is much better at 0.25-0.32 than at
            # the top, so this separates "physics" from "the deposit band is
            # moire".
            def subc(lo, hi):
                x = fft_band(rec_a, lo, hi)[ref]
                y = fft_band(dis_a, lo, hi)[ref]
                t = fft_band(truth_ap, lo, hi)[ref]
                return (float(np.corrcoef(x.ravel(), t.ravel())[0, 1]),
                        float(np.corrcoef(y.ravel(), t.ravel())[0, 1]))
            c_sr_lo, c_dis_lo = subc(0.25, 0.32)
            c_sr_hi, c_dis_hi = subc(0.32, 0.499)
            # Below the raw Nyquist (0.25 cyc/out at 2x): genuine, recoverable
            # content the reconstruction may be under-delivering.
            def subb(lo, hi):
                x = fft_band(rec_a, lo, hi)[ref]
                y = fft_band(dis_a, lo, hi)[ref]
                t = fft_band(truth_ap, lo, hi)[ref]
                return (float(np.corrcoef(x.ravel(), t.ravel())[0, 1]),
                        float(np.sqrt(np.mean(x ** 2)) / max(np.sqrt(np.mean(t ** 2)), 1e-9)),
                        float(np.corrcoef(y.ravel(), t.ravel())[0, 1]),
                        float(np.sqrt(np.mean(y ** 2)) / max(np.sqrt(np.mean(t ** 2)), 1e-9)))
            cb_sr, ab_sr, cb_dis, ab_dis = subb(0.15, 0.25)
            below_m = dict(amp=ab_sr, corr=cb_sr, amp_dis=ab_dis, corr_dis=cb_dis)
            print("      below-Nyq [0.15,0.25]: SR amp=%.2fx corr=%.2f | Disabled amp=%.2fx corr=%.2f"
                  % (ab_sr, cb_sr, ab_dis, cb_dis))
            print("      delivery [0.25,0.5]: SR=%.2fx aperture-truth (corr %.2f; "
                  "0.25-0.32 %.2f, 0.32-0.5 %.2f) | Disabled=%.2fx (corr %.2f; "
                  "%.2f, %.2f) | band err=%.4f (Disabled %.4f)"
                  % (del_ratio, c_sr, c_sr_lo, c_sr_hi, del_dis,
                     c_dis, c_dis_lo, c_dis_hi, err_ap, err_ap_dis))
    return dict(res=res_rms, art=art_hf, rec=rec_hf, tr=tr_hf, dis=dis_hf,
                dep=dep_hf, top=art_top, meanw=meanw, frac10=frac10, zero=zero,
                deliver=del_ratio, deliver_dis=del_dis, d_lat=d_lat, d_map=d_map,
                pixac=pixac, d_mid=d_mid, mtf50_sr=mtf50_sr, mtf50_dis=mtf50_dis,
                mtf_area_sr=mtf_area_sr, mtf_area_dis=mtf_area_dis,
                edge=edge_m, grad=grad_m, below=below_m, c_sr=c_sr, c_lo=c_lo,
                c_hi=c_hi, c_dis=c_dis)


def save_png(path, a):
    from PIL import Image
    a = a - a.min()
    a = a / max(a.max(), 1e-9) * 255.0
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(path)


# --------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", type=int, default=64)
    ap.add_argument("--exp", type=int, default=2)
    ap.add_argument("--frames", type=int, default=9)
    ap.add_argument("--noise", type=float, default=0.0)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--scene", default="tones",
                    choices=["ramp", "tones", "edge", "diag", "grad", "photo"])
    ap.add_argument("--cfa", default="on", choices=["on", "off"],
                    help="on: real merge/srluma per-site luma; off: ideal samples")
    ap.add_argument("--chroma", type=float, default=0.0,
                    help="scene chroma amplitude (0 = gray, still gets CFA phase error)")
    ap.add_argument("--jitter", default="uniform",
                    choices=["uniform", "handheld", "fixed", "clusters"])
    ap.add_argument("--splatbox", type=float, default=0.0,
                    help="widen the deposit droplet with an output-px box (0 = shipped)")
    ap.add_argument("--splatgauss", type=float, default=0.0,
                    help="widen the deposit droplet with a Gaussian of this output-px sigma")
    ap.add_argument("--comb", type=float, default=0.0,
                    help="SR_CFA_COMB define for srlattice1 (drizzle ships 0; "
                         "the raw-grid fallback keeps 0.4)")
    ap.add_argument("--kmap", default="const",
                    choices=["const", "edge", "small", "blocky", "blockedge", "real"])
    ap.add_argument("--alignerr", type=float, default=0.0,
                    help="residual per-frame alignment error amplitude (output px); "
                         "feeds the real trust gate and the deposit position")
    ap.add_argument("--errfield", default="random", choices=["random", "ramp", "both"])
    ap.add_argument("--trustfloor", type=float, default=0.0)
    ap.add_argument("--trustband", type=float, default=0.05)
    ap.add_argument("--covadapt", type=float, default=0.0,
                    help="prototype: widen the reconstruction kernel by this factor "
                         "where the composed alpha (deposit coverage) is 0")
    ap.add_argument("--covsharp", type=float, default=0.0,
                    help="prototype: narrow the reconstruction kernel to this "
                         "factor where coverage is 1 (deconvolve the dense deposit)")
    ap.add_argument("--corrcap", type=float, default=0.0,
                    help="prototype: Weber cap on srluma's cross-channel correction "
                         "as a fraction of the site level (0 = shipped, unbounded)")
    ap.add_argument("--nocap", action="store_true",
                    help="prototype: remove the shipped 0.12 srluma cap")
    ap.add_argument("--luma8", action="store_true",
                    help="prototype: 8-point trimmed-mean same-colour estimate "
                         "(cardinals + diagonals)")
    ap.add_argument("--elongcap", type=float, default=1.4,
                    help="srElongCap: major kernel axis cap multiplier "
                         "(device SR 1.4, legacy 1.0)")
    ap.add_argument("--gainerr", type=float, default=0.0,
                    help="per-frame photometric mismatch (fraction); the fused field "
                         "becomes a mosaic of differently scaled frames")
    ap.add_argument("--clipatten", type=float, default=0.0,
                    help="srscatter clip weight attenuation (app default 0.7)")
    ap.add_argument("--dc", type=float, default=1.2,
                    help="scene pedestal; lower it so values cross the 0.9-1.0 "
                         "clip threshold inside the frame (clip attenuation)")
    ap.add_argument("--sigmascale", type=float, default=0.55)
    ap.add_argument("--srmult", type=float, default=0.45,
                    help="SR-only sigma scale multiplier (device SR_SIGMA_MULT; "
                         "1.0 = the legacy kernel on the SR path too)")
    ap.add_argument("--sigmamin", type=float, default=0.4)
    ap.add_argument("--sigmamax", type=float, default=1.0)
    ap.add_argument("--sharpamt", type=float, default=1.75)
    ap.add_argument("--sharpwide", type=float, default=2.2,
                    help="acutance wide-pass sigma multiplier (shipped 2.2)")
    ap.add_argument("--acutrel", type=float, default=0.04,
                    help="acutance Weber cap as a fraction of the local level "
                         "(shipped 0.04)")
    ap.add_argument("--acutrelneg", type=float, default=0.0,
                    help="prototype: separate cap for the dark-side (negative) "
                         "acutance term; 0 = symmetric")
    ap.add_argument("--gateexp", type=float, default=0.3,
                    help="acutance gate exponent (shipped 0.3)")
    ap.add_argument("--acutflat", type=float, default=0.0,
                    help="SR_ACUT_FLAT: global floor on the acutance gate (0 ships)")
    ap.add_argument("--band", default="on", choices=["on", "off"],
                    help="on: run the device's SR band layer (the drizzle path "
                         "uses srBandMode=deconv; replace is the raw-grid mode)")
    ap.add_argument("--bandmode", default="deconv", choices=["replace", "deconv"],
                    help="replace: shipped gate*(rec-DoG(aniso)); deconv: "
                         "gate*(gain-1)*DoG(aniso) on the smooth reconstruction")
    ap.add_argument("--noises0", type=float, default=1.3771199e-4)
    ap.add_argument("--noiseo0", type=float, default=6.798222e-5)
    ap.add_argument("--gainmax", type=float, default=2.2,
                    help="SR_GAIN_MAX: band restoration gain cap (shipped 2.2)")
    ap.add_argument("--gatek", type=float, default=0.5,
                    help="SR_GATE_K: noise gate constant (shipped 0.5)")
    ap.add_argument("--fnyq", type=float, default=0.35,
                    help="SR_F_RAW_NYQ: band lower edge (shipped 0.35)")
    ap.add_argument("--ftop", type=float, default=0.75,
                    help="SR_F_RAW_TOP: band upper edge (shipped 0.75)")
    ap.add_argument("--gsplit", type=float, default=0.0,
                    help="override SR_BAND_SPLIT (shipped 0.3; 1.0 = uniform)")
    ap.add_argument("--disabled", default="kern", choices=["kern", "bicubic"],
                    help="Disabled reference: kern = real KernelNet legacy path "
                         "on the crop; bicubic = the old soft proxy")
    ap.add_argument("--gate", default="on", choices=["on", "off"])
    ap.add_argument("--mtfcurve", action="store_true",
                    help="print the per-bin MTF curve (SR/Disabled) for photo-like scenes")
    ap.add_argument("--metrics", default="",
                    help="write the machine-readable metrics dict to this JSON path")
    ap.add_argument("--save", default="")
    args = ap.parse_args()

    rng = np.random.default_rng(args.seed)
    raw = args.raw
    exp = args.exp
    out = raw * exp
    if args.scene == "photo":
        args._photo = load_photo(args)
    fx, fy, ph, amp, mtf = build_scene(args, rng)

    if args.jitter == "uniform":
        dxs = rng.uniform(0, exp, args.frames)
        dys = rng.uniform(0, exp, args.frames)
    elif args.jitter == "handheld":
        traj = np.cumsum(rng.standard_normal((args.frames, 2)) * 0.15 * exp, axis=0)
        traj -= traj.mean(0)
        dxs = 0.5 * exp + traj[:, 0]
        dys = 0.5 * exp + traj[:, 1]
    elif args.jitter == "fixed":
        dxs = np.full(args.frames, 0.37 * exp)
        dys = np.full(args.frames, 0.61 * exp)
    else:
        phs = np.array([(0.11, 0.19), (0.53, 0.41), (0.77, 0.83)]) * exp
        dxs = np.array([phs[f % 3, 0] for f in range(args.frames)])
        dys = np.array([phs[f % 3, 1] for f in range(args.frames)])
    dxs[0] = 0.0
    dys[0] = 0.0                       # the base frame defines the grid
    deltas = [(float(dxs[f]), float(dys[f])) for f in range(args.frames)]
    asz = max(1, raw // 16)
    err_cells = [make_err_cells(args, asz, rng, f) for f in range(args.frames)]
    trust = None
    if args.alignerr > 0:
        trust = [None] + [make_trust(args, fx, fy, ph, amp, mtf, raw, err_cells[f])
                          for f in range(1, args.frames)]

    ctx = moderngl.create_standalone_context(backend="egl")

    # frames -> per-site luma (real srluma when --cfa on)
    gains = np.ones(args.frames)
    if args.gainerr > 0:
        gains[1:] = 1.0 + args.gainerr * rng.standard_normal(args.frames - 1)
    lumas = []
    packed0 = None
    for f in range(args.frames):
        p = make_packed(args, fx, fy, ph, amp, mtf, raw, dxs[f], dys[f], rng,
                        gain=float(gains[f]))
        if f == 0:
            packed0 = p
        if args.cfa == "on":
            lum = run_srluma(ctx, raw, p, corrcap=args.corrcap,
                             nocap=args.nocap, luma8=args.luma8)
            lumas.append(lum)
        else:
            L = np.empty((raw, raw))
            L[0::2, 0::2] = p[..., 0]
            L[0::2, 1::2] = p[..., 1]
            L[1::2, 0::2] = p[..., 2]
            L[1::2, 1::2] = p[..., 3]
            l4 = np.zeros((raw, raw, 4))
            l4[..., 0] = L
            l4[..., 1] = L
            l4[..., 3] = 1.0
            lumas.append(l4)

    depv, depw = deposit_frames(
        ctx, raw, out, exp, lumas, deltas, trust=trust,
        trust_p={"floor": args.trustfloor, "band": args.trustband,
                 "s0": args.noises0, "o0": args.noiseo0,
                 "clip": args.clipatten})
    if args.splatbox > 1.0:
        depv = blur_droplet(depv, "box", args.splatbox)
        depw = blur_droplet(depw, "box", args.splatbox)
    elif args.splatgauss > 0.0:
        depv = blur_droplet(depv, "gauss", args.splatgauss)
        depw = blur_droplet(depw, "gauss", args.splatgauss)

    drizzle = run_drizzle(ctx, out, exp, depv, depw, args.comb)
    crop = demosaic_bilinear(raw, packed0)
    composed = run_compose(ctx, drizzle, crop, exp)
    kmap_vals = {"const": (0.4, 0.4), "edge": (0.8, 0.25), "small": (0.1, 0.1)}.get(
        args.kmap, (0.4, 0.4))
    alpha = None
    if args.covadapt > 0.0 or args.covsharp > 0.0:
        cov = np.clip(depw / max(args.frames / float(exp * exp), 1e-9), 0.0, 1.0)
        alpha = blur_droplet(cov, "gauss", 2.0)
    rec, eff = run_recon(ctx, composed, out, raw, args, kmap_vals, alpha=alpha)
    dis_kern = None
    if args.disabled == "kern":
        dis_kern = run_disabled_kern(ctx, crop, raw, out, exp, args, kmap_vals)

    X_out, Y_out = np.meshgrid(np.arange(out) + 0.5, np.arange(out) + 0.5)
    truth = scene_luma(fx, fy, ph, amp, X_out, Y_out, args)
    truth_ap = sample_scene(fx, fy, ph, amp, mtf, X_out, Y_out, args)
    print("burst_bench: %s exp=%d frames=%d noise=%.3f cfa=%s chroma=%.2f "
          "jitter=%s comb=%.2f droplet(box=%.1f gauss=%.2f) kmap=%s alignerr=%.2f/%s "
          "trustfloor=%.2f covadapt=%.2f -> eff sigma=(%.2f,%.2f)"
          % (args.scene, exp, args.frames, args.noise, args.cfa, args.chroma,
             args.jitter, args.comb, args.splatbox, args.splatgauss, args.kmap,
             args.alignerr, args.errfield, args.trustfloor, args.covadapt,
             eff[0], eff[1]))
    st = report("recon", rec, truth, depw, composed, crop, out, exp, args, truth_ap,
                dis_img=dis_kern)
    st2 = None
    if args.band == "on":
        fin, band, gate = run_band(ctx, out, exp, depv, depw, rec, args)
        print("  band layer: gate mean=%.3f p90=%.3f | band rms=%.4f"
              % (gate.mean(), np.quantile(gate, 0.9), float(np.sqrt(np.mean(band ** 2)))))
        st2 = report("band", fin, truth, depw, composed, crop, out, exp, args, truth_ap,
                     dis_img=dis_kern)
        if args.save:
            save_png(args.save + "_bandrec.png", fin)
    # Regression gates. The drizzle reconstruction must not carry a lattice
    # artifact (ramp scene: the truth has no HF content, so all HF in the
    # output is invented) and must never be worse than Disabled there.
    ok = True
    gates = []
    final_st = st2 if st2 is not None else st
    if args.gate and args.scene == "ramp":
        gates.append(("artifactHF <= Disabled", final_st["art"] <= final_st["dis"] + 1e-4))
        gates.append(("artTop(0.42-0.5) <= 0.0025", final_st["top"] <= 2.5e-3))
    if args.gate and st2 is not None and args.bandmode == "replace":
        # Probe: the raw-deposit replacement must show the grid it causes.
        gates.append(("replace probe shows the grid", st2["art"] > st["dis"]))
    for name, good in gates:
        print("  gate: %-40s %s" % (name, "ok" if good else "FAIL"))
        ok = ok and good
    if gates:
        print("  %s" % ("PASS" if ok else "FAIL"))
    if args.metrics:
        import json as _json
        with open(args.metrics, "w") as _f:
            _json.dump(dict(scene=args.scene, exp=exp, frames=args.frames,
                            noise=args.noise, seed=args.seed, kmap=args.kmap,
                            elongcap=args.elongcap, gsplit=args.gsplit,
                            final=final_st,
                            gates={n: bool(g) for n, g in gates}), _f,
                       indent=1, sort_keys=True)
    if args.save:
        save_png(args.save + "_composed.png", composed[..., 0])
        save_png(args.save + "_rec.png", rec)
        save_png(args.save + "_truth.png", truth)
        resid = rec - truth
        save_png(args.save + "_resid.png", resid * 6.0 + 0.5)
        save_png(args.save + "_depw.png", np.sqrt(depw))
        save_png(args.save + "_luma0.png", lumas[0][..., 0])
        print("  saved %s_*.png" % args.save)
    return 0


if __name__ == "__main__":
    sys.exit(main())
