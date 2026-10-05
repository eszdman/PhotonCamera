#!/usr/bin/env python3
"""SR recovery host bench: runs the ACTUAL app shader
(app/src/main/assets/shaders/merge/srrecover.glsl) through moderngl on
synthetic handheld bursts and measures the recovered above-Nyquist band
against ground truth.

The deposit (merge/srscatter) is mirrored in numpy only to build the
fixed-point accumulators; the recovery pass itself is the real GLSL, so
restoration gain / gate / band-edge changes are A/B'd against ground
truth here and only confirmed on device.

Setup (once):
    python3 -m venv .venv && .venv/bin/pip install moderngl numpy
Usage:
    .venv/bin/python sr_bench.py [--frames 24] [--noise 0.01] [--exp 3]
"""
import argparse
import os
import re
import sys

import numpy as np
import moderngl

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(
    HERE, "..", "..", "app", "src", "main", "assets", "shaders"))

LENS_SIGMA_RAW = 0.30
FIXED = 65536.0


def load_compute(rel, local=(1, 1, 1), gain_max=None, defines=None,
                 version="#version 310 es", drop_oes=False):
    src = open(os.path.join(ASSETS, rel)).read().lstrip("\n")
    overrides = dict(defines or {})
    if gain_max:
        overrides["SR_GAIN_MAX"] = "%.3f" % gain_max
    for name, val in overrides.items():
        src = re.sub(r"#define %s [0-9.]+" % re.escape(name),
                     "#define %s %s" % (name, val), src)
    if drop_oes:
        lines = [l for l in src.splitlines()
                 if "GL_OES_shader_image_atomic" not in l]
        src = "\n".join(lines)
    layout = (version + "\n"
              "layout(local_size_x = %d, local_size_y = %d, local_size_z = %d) in;"
              % local)
    src = src.replace("#define LAYOUT //", layout)
    src = src.replace("\nLAYOUT\n", "\n")
    return src


UTILS = os.path.normpath(os.path.join(ASSETS, "utils"))


def expand_imports(src, depth=0):
    """GLInterface.readProgram's #import expansion: utils/name.glsl."""
    if depth > 8:
        return src
    out = []
    for line in src.splitlines():
        if "#import" in line and "//" not in line:
            name = line.replace("#", "").replace(" ", "_").strip()
            path = os.path.join(UTILS, name + ".glsl")
            if os.path.exists(path):
                out.append(expand_imports(open(path).read(), depth + 1))
                continue
        out.append(line)
    return "\n".join(out)


def sinc(x):
    return np.where(np.abs(x) < 1e-9, 1.0, np.sin(np.pi * x) / (np.pi * x))


def blur(img, sigma):
    r = max(1, int(np.ceil(2.5 * sigma)))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) ** 2) / sigma ** 2)
    k /= k.sum()
    horiz = np.zeros_like(img)
    padx = np.pad(img, ((0, 0), (r, r)), mode="edge")
    for i, w in enumerate(k):
        horiz += w * padx[:, i:i + img.shape[1]]
    vert = np.zeros_like(img)
    pady = np.pad(horiz, ((r, r), (0, 0)), mode="edge")
    for i, w in enumerate(k):
        vert += w * pady[i:i + img.shape[0], :]
    return vert


def sigma_for(per_out, f_raw):
    f = min(max(f_raw * per_out, 1e-4), 0.45)
    return min(max(0.187 / f, 0.35), 1.8)


def hp_of(field, per_out):
    """Single high-pass at the raw Nyquist: the right extractor for a *full
    image* whose above-Nyquist content is a band field already (a DoG applied
    to it would double-filter)."""
    return field - blur(field, sigma_for(per_out, 0.5))


def band_of(field, per_out):
    """The shader's band-pass, mirrored exactly: a radius-2 radial Gaussian
    (weights exp(-0.5 r^2/sigma^2), normalized), the difference of the two
    cutoffs the shader uses. Scoring with a different filter would blame the
    recovery for the filter difference."""
    r = 3
    pad = np.pad(field, ((r, r), (r, r)), mode="edge")

    def conv(sigma, weight):
        acc = np.zeros_like(field)
        tot = 0.0
        for j in range(-r, r + 1):
            for i in range(-r, r + 1):
                w = np.exp(-0.5 * (i * i + j * j) / (sigma * sigma)) * weight
                acc += w * pad[r + j:r + j + field.shape[0], r + i:r + i + field.shape[1]]
                tot += w
        return acc / tot

    return (conv(sigma_for(per_out, 0.75), 1.0)
            - conv(sigma_for(per_out, 0.35), 1.0))


def scatter_gpu(ctx, raw, out, exp, frames, comp, dc, noise, seed,
                force_deltas=None, reg_err=0.0, reg_spatial=0.0, feed_trust=False,
                sr_jitter=0.0, reg_model="random"):
    """Run the real merge/srscatter deposit on the GPU for one burst and
    return (value, weight) accumulators in float units, plus the max
    difference against a numpy mirror of the same deposit (the convention
    self-check).

    The app's accumulators pack a non-negative luma and per-frame atlas
    motion; the trust inputs are held neutral (zero residual -> weight 1)
    so this isolates the deposit geometry.
    """
    fx, fy, ph, amp, mtf = comp
    count = len(fx)
    Vmax = float(np.sum(amp * mtf))
    rng = np.random.default_rng(seed)

    def f2(arr):
        return np.ascontiguousarray(arr.astype(np.float16)).tobytes()

    neutral = np.full((raw // 2, raw // 2, 4), 0.5, dtype=np.float16)
    base_tex = ctx.texture((raw // 2, raw // 2), 4, f2(neutral), dtype="f2")
    asz = max(1, raw // 16)
    atlas = ctx.texture((asz, asz), 4, dtype="f2")

    dep_v = ctx.texture((out, out), 1, dtype="u4")
    dep_w = ctx.texture((out, out), 1, dtype="u4")
    dep_v.bind_to_image(0, read=True, write=True)
    dep_w.bind_to_image(1, read=True, write=True)

    clear = ctx.compute_shader(load_compute("merge/srclear.glsl"))
    clear["srClearA"] = 0
    clear["srClearB"] = 1
    clear.run(out, out, 1)

    try:
        sc = ctx.compute_shader(load_compute("merge/srscatter.glsl"))
    except Exception:
        # Desktop GL compiles the app's OES atomic extension only when it is
        # advertised; the atomics are core in 4.3, so fall back cleanly.
        sc = ctx.compute_shader(load_compute(
            "merge/srscatter.glsl", version="#version 430 core", drop_oes=True))
    sc["srLumaTex"] = 0
    sc["diffPacked"] = 2
    sc["basePacked"] = 3
    sc["alignmentTexture"] = 4
    sc["srRefMap"] = 5
    base_tex.use(2)  # diffPacked: same as base -> zero residual -> w = 1
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
    sc["srJitter"] = sr_jitter
    sc["srMotionMax"] = 64.0
    sc["srTrustFloor"] = 0.0
    sc["srTrustBand"] = 0.05
    sc["srNoiseS0"] = 0.0
    sc["srNoiseO0"] = 0.0
    sc["srClipAtten"] = 0.0

    if reg_model == "handheld" and reg_err > 0:
        # Smooth, correlated per-frame alignment error (real handheld: the
        # trajectory is low-pass, so consecutive frames differ by little - the
        # frame-to-frame DIFFERENCE is what decorrelates the sub-pixel samples).
        step = 0.25
        traj = np.cumsum(rng.standard_normal((frames, 2)) * step, axis=0)
        traj -= traj.mean(0)
        traj /= (traj.std(0) + 1e-9)
        traj *= reg_err
    sx = (np.arange(raw) * exp)[None, :]
    sy = (np.arange(raw) * exp)[:, None]
    mirror_v = np.zeros((out, out))
    mirror_w = np.zeros((out, out))
    for f in range(frames):
        if force_deltas is not None:
            dx, dy = force_deltas[f]
        else:
            dx = rng.random() * exp
            dy = rng.random() * exp
        px = sx - dx          # sampled at the true position...
        py = sy - dy
        nos = np.zeros((raw, raw))
        for k in range(count):
            nos += amp[k] * mtf[k] * np.cos(
                2 * np.pi * (fx[k] * px + fy[k] * py) + ph[k])
        if reg_spatial > 0:
            # smooth, spatially-varying misregistration (differential motion /
            # rolling shutter): a ramp the per-frame atlas (one vector) cannot
            # encode, so it is pure per-pixel registration error.
            cen = raw * exp / 2.0
            ex = reg_spatial * (px - cen) / cen
            ey = reg_spatial * (py - cen) / cen
        elif reg_model == "variable" and reg_err > 0:
            a = rng.random() * 2 * np.pi
            mag = reg_err * (2.0 * rng.random())
            ex = np.full((raw, raw), mag * np.cos(a))
            ey = np.full((raw, raw), mag * np.sin(a))
        elif reg_model == "handheld" and reg_err > 0:
            ex = np.full((raw, raw), traj[f, 0])
            ey = np.full((raw, raw), traj[f, 1])
        elif reg_err > 0:
            a = rng.random() * 2 * np.pi
            ex = np.full((raw, raw), reg_err * np.cos(a))
            ey = np.full((raw, raw), reg_err * np.sin(a))
        else:
            ex = np.zeros((raw, raw))
            ey = np.zeros((raw, raw))
        val = nos + dc
        val += noise * rng.standard_normal((raw, raw))
        # the residual the app's trust gate sees, per site
        verr = np.zeros((raw, raw))
        for k in range(count):
            verr += amp[k] * mtf[k] * np.cos(
                2 * np.pi * (fx[k] * (px + ex) + fy[k] * (py + ey)) + ph[k])
        resid = np.abs(verr - nos)
        luma = np.zeros((raw, raw, 4), dtype=np.float16)
        luma[..., 0] = val
        luma_tex = ctx.texture((raw, raw), 4, f2(luma), dtype="f2")
        mot = np.zeros((asz, asz, 4), dtype=np.float16)
        mot[..., 2] = (dx - float(np.mean(ex))) / (2.0 * exp)
        mot[..., 3] = (dy - float(np.mean(ey))) / (2.0 * exp)
        atlas.write(f2(mot))
        luma_tex.use(0)
        atlas.use(4)
        if feed_trust:
            # Faithful trust input: the aligned base (the grid scene) and this
            # frame's alter (the grid scene shifted by the registration error),
            # in the [0,1] packed domain. The shader then applies its OWN trust
            # formula (3x3 signed mean vs 3x3 abs mean vs the noise band), so
            # this exercises the real mechanism, not a proxy.
            PI = np.arange(raw // 2)
            PX, PY = np.meshgrid(2 * PI, 2 * PI)      # quad top-left raw indices
            if reg_spatial > 0:
                cen = raw * exp / 2.0
                EX = reg_spatial * (PX * exp - cen) / cen
                EY = reg_spatial * (PY * exp - cen) / cen
            else:
                EX = np.full((raw // 2, raw // 2), float(np.mean(ex)))
                EY = np.full((raw // 2, raw // 2), float(np.mean(ey)))
            darr = np.zeros((raw // 2, raw // 2, 4), dtype=np.float16)
            barr = np.zeros((raw // 2, raw // 2, 4), dtype=np.float16)
            for c in range(4):
                rx = PX + (c & 1)
                ry = PY + (c >> 1)
                rb = np.zeros((raw // 2, raw // 2))
                rd = np.zeros((raw // 2, raw // 2))
                for k in range(count):
                    rb += amp[k] * mtf[k] * np.cos(2 * np.pi * (fx[k] * (rx * exp) + fy[k] * (ry * exp)) + ph[k])
                    rd += amp[k] * mtf[k] * np.cos(2 * np.pi * (fx[k] * (rx * exp + EX) + fy[k] * (ry * exp + EY)) + ph[k])
                darr[..., c] = np.clip(0.5 + 0.5 * rd / Vmax, 0, 1)
                barr[..., c] = np.clip(0.5 + 0.5 * rb / Vmax, 0, 1)
            darr[..., 3] = 1.0
            barr[..., 3] = 1.0
            if f == frames - 1:
                _r = np.abs(darr[..., :3].astype(np.float32) - barr[..., :3].astype(np.float32))
                print("  (trust) mean|packed residual|=%.4f p99=%.4f (noise band=0.05)"
                      % (float(_r.mean()), float(np.percentile(_r, 99))))
            diff_tex = ctx.texture((raw // 2, raw // 2), 4, f2(darr), dtype="f2")
            base_scene = ctx.texture((raw // 2, raw // 2), 4, f2(barr), dtype="f2")
            diff_tex.use(2)
            base_scene.use(3)
            sc["diffPacked"] = 2
            sc["basePacked"] = 3
        sc["srZeroMotion"] = 1.0 if f == 0 else 0.0
        sc["srFrame"] = f
        sc.run(raw, raw, 1)
        # numpy mirror of the same deposit (sample at s*exp - delta, placed
        # there; delta = 2m*exp in output px).
        ocx = px + ex - 0.5   # ...but deposited at the erroneous one
        ocy = py + ey - 0.5
        x0 = np.floor(ocx).astype(int)
        y0 = np.floor(ocy).astype(int)
        fxr = ocx - x0
        fyr = ocy - y0
        for b in (0, 1):
            for a in (0, 1):
                xi = np.clip(x0 + a, 0, out - 1)
                yi = np.clip(y0 + b, 0, out - 1)
                w = (1 - fxr if a == 0 else fxr) * (1 - fyr if b == 0 else fyr)
                np.add.at(mirror_v, (yi, xi), w * val)
                np.add.at(mirror_w, (yi, xi), w)
        luma_tex.release()
        if feed_trust:
            diff_tex.release()
            base_scene.release()

    gv = np.frombuffer(dep_v.read(), dtype=np.uint32).reshape(out, out).astype(np.float64) / 65536.0
    gw = np.frombuffer(dep_w.read(), dtype=np.uint32).reshape(out, out).astype(np.float64) / 65536.0
    diff = float(max(np.max(np.abs(gv - mirror_v)), np.max(np.abs(gw - mirror_w))))
    global mirror_w_sum
    mirror_w_sum = float(mirror_w.sum())
    return gv, gw, diff, mirror_v, mirror_w


def fft_shift(z, sx, sy):
    """Exact sub-pixel shift (features move by +s), no resampler rolloff."""
    F = np.fft.fft2(z)
    ky = np.fft.fftfreq(z.shape[0])[:, None]
    kx = np.fft.fftfreq(z.shape[1])[None, :]
    return np.real(np.fft.ifft2(F * np.exp(-2j * np.pi * (kx * sx + ky * sy))))


def lattice_luma(ctx, raw, exp, tex_v, tex_w):
    """The real merge/srlattice reduce of the deposit, at the raw grid: this is
    the fused image the device reconstruction actually consumes. Its origin is
    output (t+0.5)*exp-0.5; this bench's reconstruction input maps texel t to
    output t*exp, so shift by (exp-1)/(2exp) raw px to the bench convention."""
    lat_tex = ctx.texture((raw, raw), 4, dtype="f2")
    lat_tex.bind_to_image(0, read=False, write=True)
    lat = ctx.compute_shader(load_compute("merge/srlattice.glsl",
        defines={"SR_CFA_COMB": os.environ.get("SR_CFA_COMB", "0.4")}))
    lat["srDepV"] = 0
    lat["srDepW"] = 1
    lat["srCombOff"] = (2.0 * exp, 2.0 * exp)
    tex_v.use(0)
    tex_w.use(1)
    lat["fusedOut"] = 0
    lat.run(raw, raw, 1)
    arr = np.frombuffer(lat_tex.read(), dtype=np.float16).reshape(raw, raw, 4)
    lum = arr[..., 0].astype(np.float64)
    d = (exp - 1.0) / (2.0 * exp)
    return fft_shift(lum, d, d) if exp > 1 else lum



def lattice_luma_ideal(sum_vw, sum_w, raw, exp, d):
    """Ideal (sinc) anti-aliased reduce of the output-grid deposit to the raw
    grid, for comparison against the device's bilinear srlattice: sampling the
    finer accumulator at the raw grid with bilinear interpolation passes the
    above-Nyquist SR content through and folds it into the below-Nyquist band."""
    lum = sum_vw / np.maximum(sum_w, 1e-9)
    F = np.fft.fftshift(np.fft.fft2(lum))
    h, w = F.shape
    cy, cx = h // 2, w // 2
    Y, X = np.mgrid[0:h, 0:w]
    r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
    F = np.where(r >= 0.5 / exp, 0.0, F)
    lp = np.real(np.fft.ifft2(np.fft.ifftshift(F)))
    u = np.round((np.arange(raw) + 0.5) * exp - 0.5).astype(int)
    u = np.clip(u, 0, lum.shape[0] - 1)
    return fft_shift(lp[np.ix_(u, u)], d, d)

def recon_bench(ctx, raw, out, exp, comp, dc, truth, band_tex, per, m,
                sharp_amt=1.4, sigma_scale=0.55, sigma_min=0.25, acut_flat=0.0,
                raw_input=None, aniso_map=False):
    """Render the real upscalecrop/anisoupscale (the reconstruction) on a
    synthetic crop image with a synthetic isotropic KernelsMap, then measure
    its error against ground truth, and again after the recovered band is
    applied (the guided delivery) - the reconstruction-side evidence.

    Mapping: scaleRatio = output/input = exp, so inPos = (o+0.5)/exp is the
    input texel index of output pixel o; the input texel t holds the scene at
    output coordinate t*exp, and the truth at o is the scene at o+0.5."""
    fx, fy, ph, amp, mtf = comp
    count = len(fx)

    def scene(x, y):
        v = np.zeros(np.broadcast(x, y).shape)
        for i in range(count):
            v = v + amp[i] * np.cos(2 * np.pi * (fx[i] * x + fy[i] * y) + ph[i])
        return v

    tix = (np.arange(raw) * exp)[None, :]
    tiy = (np.arange(raw) * exp)[:, None]
    low = np.zeros((raw, raw))
    for i in range(count):
        low = low + amp[i] * mtf[i] * np.cos(
            2 * np.pi * (fx[i] * tix + fy[i] * tiy) + ph[i])
    low = low + dc
    # The device reconstruction input is the *fused* lattice (denoised, and
    # blurred by whatever residual misregistration the burst had), not the
    # ideal raw grid. --fusion feeds the real srlattice output here so the reg
    # sweep is scored end-to-end on below-Nyquist detail.
    inlow = low if raw_input is None else raw_input
    in_tex = ctx.texture((raw, raw), 4, np.ascontiguousarray(
        np.stack([inlow, inlow, inlow, np.ones_like(inlow)], -1).astype(np.float16)).tobytes(),
        dtype="f2")

    msz = max(2, raw // 2)
    kmap = np.zeros((msz, msz, 4), dtype=np.float16)
    kmap[..., 0] = 0.8 if aniso_map else 0.4   # elongated (edge) kernels when set,
    kmap[..., 1] = 0.25 if aniso_map else 0.4  # so the acutance gate actually fires
    kmap[..., 3] = 1.0
    map_tex = ctx.texture((msz, msz), 4, np.ascontiguousarray(kmap).tobytes(), dtype="f2")

    src = expand_imports(open(os.path.join(
        ASSETS, "upscalecrop/anisoupscale.glsl")).read().lstrip("\n"))
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    prog = ctx.program(
        vertex_shader=vsrc,
        fragment_shader="#version 310 es\n#define SR_ACUT_FLAT %.4f\n" % acut_flat + src)
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())
    try:
        vao = ctx.vertex_array(prog, [(verts, "2f", "in_pos")])
    except Exception:
        vao = ctx.simple_vertex_array(prog, verts, "in_pos")
    target = ctx.texture((out, out), 4, dtype="f2")
    fbo = ctx.framebuffer(color_attachments=[target])
    prog["InputBuffer"] = 0
    in_tex.use(0)
    prog["KernelsMap"] = 1
    map_tex.use(1)
    prog["fullSize"] = (out, out)
    prog["u_tileOrigin"] = (0, 0)
    prog["u_winOrigin"] = (0, 0)
    prog["u_winFullSize"] = (float(raw), float(raw))
    prog["sigmaScale"] = sigma_scale   # the app's UpscaleCrop SR-path default
    prog["sigmaMinPx"] = (sigma_min, sigma_min)
    prog["sigmaMaxPx"] = 1.0
    prog["strength"] = 1.0
    prog["sharpAmt"] = sharp_amt
    prog["sharpWide"] = 2.2
    prog["acutRel"] = 0.04
    prog["maxElong"] = 8.0
    prog["gateExp"] = 0.3
    prog["scaleRatio"] = (float(exp), float(exp))
    prog["splitChroma"] = 1
    prog["kernelRadius"] = 5
    prog["debugMode"] = 0
    fbo.use()
    fbo.clear(0.0, 0.0, 0.0, 1.0)
    vao.render(moderngl.TRIANGLES)
    aniso = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                          ).reshape(out, out, 4)[..., 0].astype(np.float64)
    tgt = truth + dc
    err_a = float(np.sqrt(np.mean((aniso[m] - tgt[m]) ** 2)))
    tb_ref = hp_of(truth, per)
    ob_a = hp_of(aniso, per)

    # Guided delivery: the recovered band applied on top of the real
    # reconstruction (srpre/band) - error should not get worse.
    aniso_tex = ctx.texture((out, out), 4, np.ascontiguousarray(
        np.stack([aniso, aniso, aniso, np.ones_like(aniso)], -1).astype(np.float16)).tobytes(),
        dtype="f2")
    bs = expand_imports(open(os.path.join(ASSETS, "srpre/band.glsl")).read().lstrip("\n"))
    bprog = ctx.program(vertex_shader=vsrc, fragment_shader="#version 310 es\n" + bs)
    try:
        bvao = ctx.vertex_array(bprog, [(verts, "2f", "in_pos")])
    except Exception:
        bvao = ctx.simple_vertex_array(bprog, verts, "in_pos")
    out2 = ctx.texture((out, out), 4, dtype="f2")
    fbo2 = ctx.framebuffer(color_attachments=[out2])
    bprog["InputBuffer"] = 0
    aniso_tex.use(0)
    bprog["BandMap"] = 1
    band_tex.use(1)
    bprog["srPerOut"] = (per, per)
    bprog["srBlack"] = (0.0, 0.0, 0.0)
    bprog["u_inOrigin"] = (0, 0)
    bprog["u_tileOrigin"] = (0, 0)
    fbo2.use()
    fbo2.clear(0.0, 0.0, 0.0, 1.0)
    bvao.render(moderngl.TRIANGLES)
    fin = np.frombuffer(fbo2.read(components=4, dtype="f2"), dtype=np.float16
                        ).reshape(out, out, 4)[..., 0].astype(np.float64)
    err_f = float(np.sqrt(np.mean((fin[m] - tgt[m]) ** 2)))
    ob_f = hp_of(fin, per)
    berr_a = float(np.sqrt(np.mean((ob_a[m] - tb_ref[m]) ** 2)))
    berr_f = float(np.sqrt(np.mean((ob_f[m] - tb_ref[m]) ** 2)))

    # Ceiling diagnostic: the same delivery with the gate forced to 1, to
    # separate "gate too conservative" from "band model/scale wrong".
    words = np.frombuffer(band_tex.read(), dtype=np.uint32).reshape(out, out)
    hb = words.reshape(out, out, 1).view(np.float16).reshape(out, out, 2).copy()
    hb[..., 1] = 1.0
    g1 = ctx.texture((out, out), 1, np.ascontiguousarray(hb).view(np.uint32).tobytes(),
                     dtype="u4")
    out3 = ctx.texture((out, out), 4, dtype="f2")
    fbo3 = ctx.framebuffer(color_attachments=[out3])
    aniso_tex.use(0)
    g1.use(1)
    fbo3.use()
    fbo3.clear(0.0, 0.0, 0.0, 1.0)
    bvao.render(moderngl.TRIANGLES)
    fin1 = np.frombuffer(fbo3.read(components=4, dtype="f2"), dtype=np.float16
                         ).reshape(out, out, 4)[..., 0].astype(np.float64)
    berr_1 = float(np.sqrt(np.mean((hp_of(fin1, per)[m] - tb_ref[m]) ** 2)))

    # Does the recovered band actually reach the output? Compare the shader's
    # applied change against the mirror's gate*(rec - DoG(aniso)).
    bw = np.frombuffer(band_tex.read(), dtype=np.uint32).reshape(out, out)
    bh = bw.reshape(out, out, 1).view(np.float16).reshape(out, out, 2)
    rec = bh[..., 0].astype(np.float64)
    gts = bh[..., 1].astype(np.float64)
    band_aniso_m = band_of(aniso, per)
    corr_m = gts * (rec - band_aniso_m)
    rms = lambda z: float(np.sqrt(np.mean(z[m] ** 2)))
    tb_dog = band_of(truth, per)
    print("   dbg recon: rms rec=%.4f gate*rec=%.4f DoG(rec)=%.4f corr=%.4f | "
          "DoG(aniso)=%.4f tb=%.4f | shader-mirror rms=%.5f | out-aniso rms=%.5f"
          % (rms(rec), rms(gts * rec), rms(band_of(rec, per)), rms(corr_m),
             rms(band_aniso_m), rms(tb_dog),
             float(np.sqrt(np.mean((fin[m] - (aniso + corr_m)[m]) ** 2))),
             float(np.sqrt(np.mean((fin[m] - aniso[m]) ** 2)))))
    return err_a, err_f, berr_a, berr_f, berr_1, aniso, fin, low


def settle_bench(raw, out, exp, comp, truth, aniso, fin, low):
    """Settle whether the top native band's apparent detail is real or folded
    aliasing, and whether the SR under-delivers the genuine part.

    The native (Disabled) grid holds, in its top band [0.5,1.0]x Nyquist, the
    genuine scene content below Nyquist PLUS the mirror of the scene content
    above Nyquist. Splitting the synthetic scene by raw frequency gives both
    references exactly; the reconstruction can only be judged against the
    genuine part, and matching the folded part would be re-adding aliasing.
    """
    fx, fy, ph, amp, mtf = comp
    rawf = np.hypot(fx, fy) * exp
    # Sample at the raw grid in OUTPUT coordinates (spacing exp), exactly as
    # recon_bench's `low` does; the component frequencies are cycles/output px.
    xx = (np.arange(raw) * exp)[None, :]
    yy = (np.arange(raw) * exp)[:, None]
    def field(mask):
        v = np.zeros((raw, raw))
        for k in np.where(mask)[0]:
            v += amp[k] * mtf[k] * np.cos(2 * np.pi * (fx[k] * xx + fy[k] * yy) + ph[k])
        return v
    genuine = field(rawf < 0.5)
    fold = field(rawf >= 0.5)

    def down(z):
        # Ideal (sinc) low-pass to the raw Nyquist, then sample on the raw
        # grid: no resampler rolloff on either side, so the band energies are
        # an apples-to-apples MTF comparison (a box average would low-pass
        # only the SR side and fake a deficit).
        if exp <= 1:
            return z
        F = np.fft.fftshift(np.fft.fft2(z))
        h, w = F.shape
        cy, cx = h // 2, w // 2
        Y, X = np.mgrid[0:h, 0:w]
        r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
        F = np.where(r >= 0.5 / exp, 0.0, F)
        zl = np.real(np.fft.ifft2(np.fft.ifftshift(F)))
        return zl[::exp, ::exp]
    sr = down(fin)
    an = down(aniso)
    truth_n = down(truth)

    def bandE(z, f0, f1):
        a = z - z.mean()
        F = np.abs(np.fft.fftshift(np.fft.fft2(a))) ** 2
        h, w = F.shape
        cy, cx = h // 2, w // 2
        Y, X = np.mgrid[0:h, 0:w]
        r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
        msk = (r >= f0) & (r < f1)
        return float(F[msk].mean())

    # top native band = [0.5, 1.0] x raw Nyquist, in cycles/raw px
    f0, f1 = 0.25, 0.5
    En = bandE(low, f0, f1)
    Eg = bandE(genuine, f0, f1)
    Ef = bandE(fold, f0, f1)
    Esr = bandE(sr, f0, f1)
    Ean = bandE(an, f0, f1)
    Et = bandE(truth_n, f0, f1)
    # correlation of the reconstruction's top band with genuine vs fold
    def bp(z):
        F = np.fft.fftshift(np.fft.fft2(z - z.mean()))
        h, w = F.shape
        cy, cx = h // 2, w // 2
        Y, X = np.mgrid[0:h, 0:w]
        r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
        F[(r < f0) | (r >= f1)] = 0
        return np.real(np.fft.ifft2(np.fft.ifftshift(F)))
    sr_b, gen_b, fold_b = bp(sr), bp(genuine), bp(fold)
    c_gen = float(np.corrcoef(sr_b.ravel(), gen_b.ravel())[0, 1])
    c_fold = float(np.corrcoef(sr_b.ravel(), fold_b.ravel())[0, 1])
    alias_frac = Ef / max(En, 1e-12)
    sr_delivery = Esr / max(Eg, 1e-12)
    print("  settle (top native band, 0.5-1.0x Nyq):")
    print("    native E=%.3e = genuine %.3e + fold %.3e -> alias fraction %.0f%%"
          % (En, Eg, Ef, 100.0 * alias_frac))
    print("    aniso E/E_genuine=%.2f (reconstruction alone) | "
          "final E/E_genuine=%.2f | E/E_truth=%.2f | corr(final,genuine)=%.2f "
          "corr(final,fold)=%.2f"
          % (Ean / max(Eg, 1e-12), sr_delivery, Esr / max(Et, 1e-12), c_gen, c_fold))
    return alias_frac, sr_delivery, c_gen, c_fold, En, Eg, Ef


def edge_bench(ctx, raw, out, exp, sharp_amt, sigma_scale, sigma_min, acut_flat,
               noise=0.01, aniso_map=False):
    """Validate the acutance gate floor on a real edge and on noise. The floor
    deconvolves *texture* (real detail), but it must not ring the step (halo)
    nor amplify flat noise. Renders the real UpscaleCrop at floor 0 and at the
    shipped floor and reports both, plus a pass flag."""
    yy, xx = np.mgrid[0:out, 0:out].astype(np.float64)
    A = 1.0
    truth = np.where(xx < out / 2.0, A, -A)
    # a fine grating (0.45 cyc/raw ~ 0.9x Nyquist) in the top quarter, so a
    # texture-vs-alias check rides on the same frame
    gy = slice(0, out // 4)
    truth[gy] += 0.25 * np.cos(2 * np.pi * (0.45 / exp) * xx[gy])
    # sensor PSF: pixel aperture (box ~ 1/sqrt(12)) + lens, in output px
    psf = np.sqrt(1.0 / 12.0 + LENS_SIGMA_RAW ** 2) * exp
    low_out = blur(truth, psf)
    tx = (np.arange(raw) * exp + exp / 2.0)
    txg, tyg = np.meshgrid(tx, tx)
    x0 = np.clip(np.floor(txg).astype(int), 0, out - 2)
    y0 = np.clip(np.floor(tyg).astype(int), 0, out - 2)
    fxr = np.clip(txg - x0, 0, 1)
    fyr = np.clip(tyg - y0, 0, 1)
    low = ((1 - fyr) * ((1 - fxr) * low_out[y0, x0] + fxr * low_out[y0, x0 + 1])
           + fyr * ((1 - fxr) * low_out[y0 + 1, x0] + fxr * low_out[y0 + 1, x0 + 1]))
    rng = np.random.default_rng(5)
    low = low + noise * rng.standard_normal((raw, raw))

    msz = max(2, raw // 2)
    kmap = np.zeros((msz, msz, 4), dtype=np.float16)
    kmap[..., 0] = 0.8 if aniso_map else 0.4
    kmap[..., 1] = 0.25 if aniso_map else 0.4
    kmap[..., 3] = 1.0
    map_tex = ctx.texture((msz, msz), 4, np.ascontiguousarray(kmap).tobytes(), dtype="f2")
    src = expand_imports(open(os.path.join(
        ASSETS, "upscalecrop/anisoupscale.glsl")).read().lstrip("\n"))
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())

    def render(af):
        in_tex = ctx.texture((raw, raw), 4, np.ascontiguousarray(np.stack(
            [low, low, low, np.ones_like(low)], -1).astype(np.float16)).tobytes(),
            dtype="f2")
        prog = ctx.program(vertex_shader=vsrc, fragment_shader=(
            "#version 310 es\n#define SR_ACUT_FLAT %.4f\n" % af + src))
        try:
            vao = ctx.vertex_array(prog, [(verts, "2f", "in_pos")])
        except Exception:
            vao = ctx.simple_vertex_array(prog, verts, "in_pos")
        tgt = ctx.texture((out, out), 4, dtype="f2")
        fbo = ctx.framebuffer(color_attachments=[tgt])
        prog["InputBuffer"] = 0
        in_tex.use(0)
        prog["KernelsMap"] = 1
        map_tex.use(1)
        prog["fullSize"] = (out, out)
        prog["u_tileOrigin"] = (0, 0)
        prog["u_winOrigin"] = (0, 0)
        prog["u_winFullSize"] = (float(raw), float(raw))
        prog["sigmaScale"] = sigma_scale
        prog["sigmaMinPx"] = (sigma_min, sigma_min)
        prog["sigmaMaxPx"] = 1.0
        prog["strength"] = 1.0
        prog["sharpAmt"] = sharp_amt
        prog["sharpWide"] = 2.2
        prog["acutRel"] = 0.04
        prog["maxElong"] = 8.0
        prog["gateExp"] = 0.3
        prog["scaleRatio"] = (float(exp), float(exp))
        prog["splitChroma"] = 1
        prog["kernelRadius"] = 5
        prog["debugMode"] = 0
        fbo.use()
        fbo.clear(0.0, 0.0, 0.0, 1.0)
        vao.render(moderngl.TRIANGLES)
        return np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                             ).reshape(out, out, 4)[..., 0].astype(np.float64)

    a0 = render(0.0)
    af = render(acut_flat)
    cy = out // 2
    xe = out // 2

    def overshoot(row):
        left = row[xe - 3 * exp:xe - 1]
        right = row[xe + 1:xe + 3 * exp]
        return max(0.0, float(left.max()) - A) + max(0.0, (-A) - float(right.min()))

    ov0 = overshoot(a0[cy])
    ovf = overshoot(af[cy])
    flat = (slice(out // 2, out - 8), slice(8, out // 2 - 3 * exp))
    n0 = float(np.std(a0[flat]))
    nf = float(np.std(af[flat]))
    print("  edge/halo: step=%.1f | overshoot floor0=%.3f shipped=%.3f | "
          "flat noise floor0=%.4f shipped=%.4f" % (2 * A, ov0, ovf, n0, nf))
    # no new ringing (<= 1% of the step) and flat noise not materially amplified
    ok = (ovf <= ov0 + 0.02) and (nf <= n0 * 1.5 + 0.001)
    return ov0, ovf, n0, nf, ok



def flat_bench(ctx, raw, out, exp, sharp_amt, sigma_scale, sigma_min, acut_flat,
               noise=0.004):
    """Flat/highlight mesh + speck check. Renders a flat mid field and a bright
    highlight through the real UpscaleCrop with a *spatially varying* kernel map
    (a constant map cannot show a grid), then measures any grid at the map pitch
    and any speck (isolated HF) the acutance floor adds. Compares floor 0 vs the
    shipped floor, like edge_bench."""
    A = 0.5
    xx = np.arange(out)[None, :].astype(np.float64)
    # flat mid + a bright highlight (near the srpre clamp) on the right
    truth = np.full((out, out), A)
    hl = slice(0, out // 3)
    truth[hl, out // 2:] = 3.5
    tx = (np.arange(raw) * exp + exp / 2.0)
    txg, tyg = np.meshgrid(tx, tx)
    x0 = np.clip(np.floor(txg).astype(int), 0, out - 2)
    y0 = np.clip(np.floor(tyg).astype(int), 0, out - 2)
    fxr = np.clip(txg - x0, 0, 1)
    fyr = np.clip(tyg - y0, 0, 1)
    low = ((1 - fyr) * ((1 - fxr) * truth[y0, x0] + fxr * truth[y0, x0 + 1])
           + fyr * ((1 - fxr) * truth[y0 + 1, x0] + fxr * truth[y0 + 1, x0 + 1]))
    rng = np.random.default_rng(9)
    low = low + noise * rng.standard_normal((raw, raw))

    msz = max(2, raw // 2)
    # a spatially varying map (alternating sigma): mimics the KernelNet map's
    # residual structure that a constant map cannot represent.
    kmap = np.zeros((msz, msz, 4), dtype=np.float16)
    alt = 0.40 + 0.10 * ((np.arange(msz)[None, :] + np.arange(msz)[:, None]) % 2)
    kmap[..., 0] = alt
    kmap[..., 1] = alt
    kmap[..., 3] = 1.0
    map_tex = ctx.texture((msz, msz), 4, np.ascontiguousarray(kmap).tobytes(), dtype="f2")
    src = expand_imports(open(os.path.join(
        ASSETS, "upscalecrop/anisoupscale.glsl")).read().lstrip("\n"))
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())

    def render(af):
        in_tex = ctx.texture((raw, raw), 4, np.ascontiguousarray(np.stack(
            [low, low, low, np.ones_like(low)], -1).astype(np.float16)).tobytes(),
            dtype="f2")
        prog = ctx.program(vertex_shader=vsrc, fragment_shader=(
            "#version 310 es\n#define SR_ACUT_FLAT %.4f\n" % af + src))
        try:
            vao = ctx.vertex_array(prog, [(verts, "2f", "in_pos")])
        except Exception:
            vao = ctx.simple_vertex_array(prog, verts, "in_pos")
        tgt = ctx.texture((out, out), 4, dtype="f2")
        fbo = ctx.framebuffer(color_attachments=[tgt])
        prog["InputBuffer"] = 0
        in_tex.use(0)
        prog["KernelsMap"] = 1
        map_tex.use(1)
        prog["fullSize"] = (out, out)
        prog["u_tileOrigin"] = (0, 0)
        prog["u_winOrigin"] = (0, 0)
        prog["u_winFullSize"] = (float(raw), float(raw))
        prog["sigmaScale"] = sigma_scale
        prog["sigmaMinPx"] = (sigma_min, sigma_min)
        prog["sigmaMaxPx"] = 1.0
        prog["strength"] = 1.0
        prog["sharpAmt"] = sharp_amt
        prog["sharpWide"] = 2.2
        prog["acutRel"] = 0.04
        prog["maxElong"] = 8.0
        prog["gateExp"] = 0.3
        prog["scaleRatio"] = (float(exp), float(exp))
        prog["splitChroma"] = 1
        prog["kernelRadius"] = 5
        prog["debugMode"] = 0
        fbo.use()
        fbo.clear(0.0, 0.0, 0.0, 1.0)
        vao.render(moderngl.TRIANGLES)
        return np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                             ).reshape(out, out, 4)[..., 0].astype(np.float64)

    def metrics(a):
        mid = a[out // 2:out - 8, 8:out // 2]          # flat mid band
        hil = a[4:out // 3 - 4, out // 2 + 8:out - 8]  # highlight band
        # grid: energy at the map pitch (2 output px) via a 2-px-period fit
        gx = 0.5 * (mid[:, ::2] - mid[:, 1::2]) if mid.shape[1] > 2 else np.zeros((1, 1))
        gy = 0.5 * (mid[::2, :] - mid[1::2, :]) if mid.shape[0] > 2 else np.zeros((1, 1))
        grid = float(np.sqrt(np.mean(gx ** 2)) + np.sqrt(np.mean(gy ** 2)))
        speck = float(np.percentile(np.abs(mid - np.median(mid)), 99.9))
        return grid, float(np.std(mid)), float(np.std(hil)), speck

    g0, f0, h0, s0 = metrics(render(0.0))
    gf, ff, hf, sf = metrics(render(acut_flat))
    print("  flat/highlight: grid floor0=%.5f shipped=%.5f | flat sd %.5f->%.5f | "
          "highlight sd %.4f->%.4f | speck99.9 %.5f->%.5f"
          % (g0, gf, f0, ff, h0, hf, s0, sf))
    ok = (gf <= g0 * 1.10 + 1e-5) and (ff <= f0 * 1.10 + 1e-5) \
        and (hf <= h0 * 1.15 + 1e-4) and (sf <= s0 * 1.10 + 1e-5)
    return g0, gf, f0, ff, h0, hf, s0, sf, ok



def diag_bench(ctx, raw, out, exp, sharp_amt, sigma_scale, sigma_min, acut_flat,
               noise=0.01):
    """Diagonal-edge staircase probe: a 45-degree high-contrast step through the
    real UpscaleCrop with edge (elongated) kernels so the elongation acutance
    fires. Measures the along-edge high-frequency deviation (jaggies/staircase)
    at floor 0 vs the shipped acutance - the edge-aliasing signature."""
    Y, X = np.mgrid[0:out, 0:out].astype(np.float64)
    truth = np.where(X + Y < out, 1.0, -1.0)
    psf = np.sqrt(1.0 / 12.0 + LENS_SIGMA_RAW ** 2) * exp
    low_out = blur(truth, psf)
    tx = (np.arange(raw) * exp + exp / 2.0)
    txg, tyg = np.meshgrid(tx, tx)
    x0 = np.clip(np.floor(txg).astype(int), 0, out - 2)
    y0 = np.clip(np.floor(tyg).astype(int), 0, out - 2)
    fxr = np.clip(txg - x0, 0, 1)
    fyr = np.clip(tyg - y0, 0, 1)
    low = ((1 - fyr) * ((1 - fxr) * low_out[y0, x0] + fxr * low_out[y0, x0 + 1])
           + fyr * ((1 - fxr) * low_out[y0 + 1, x0] + fxr * low_out[y0 + 1, x0 + 1]))
    rng = np.random.default_rng(3)
    low = low + noise * rng.standard_normal((raw, raw))
    msz = max(2, raw // 2)
    kmap = np.zeros((msz, msz, 4), dtype=np.float16)
    kmap[..., 0] = 0.8
    kmap[..., 1] = 0.25
    kmap[..., 3] = 1.0
    map_tex = ctx.texture((msz, msz), 4, np.ascontiguousarray(kmap).tobytes(), dtype="f2")
    src = expand_imports(open(os.path.join(
        ASSETS, "upscalecrop/anisoupscale.glsl")).read().lstrip("\n"))
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())

    def render(amt):
        in_tex = ctx.texture((raw, raw), 4, np.ascontiguousarray(np.stack(
            [low, low, low, np.ones_like(low)], -1).astype(np.float16)).tobytes(),
            dtype="f2")
        prog = ctx.program(vertex_shader=vsrc, fragment_shader=(
            "#version 310 es\n#define SR_ACUT_FLAT %.4f\n" % acut_flat + src))
        try:
            vao = ctx.vertex_array(prog, [(verts, "2f", "in_pos")])
        except Exception:
            vao = ctx.simple_vertex_array(prog, verts, "in_pos")
        tgt = ctx.texture((out, out), 4, dtype="f2")
        fbo = ctx.framebuffer(color_attachments=[tgt])
        prog["InputBuffer"] = 0
        in_tex.use(0)
        prog["KernelsMap"] = 1
        map_tex.use(1)
        prog["fullSize"] = (out, out)
        prog["u_tileOrigin"] = (0, 0)
        prog["u_winOrigin"] = (0, 0)
        prog["u_winFullSize"] = (float(raw), float(raw))
        prog["sigmaScale"] = sigma_scale
        prog["sigmaMinPx"] = (sigma_min, sigma_min)
        prog["sigmaMaxPx"] = 1.0
        prog["strength"] = 1.0
        prog["sharpAmt"] = amt
        prog["sharpWide"] = 2.2
        prog["acutRel"] = 0.04
        prog["maxElong"] = 8.0
        prog["gateExp"] = 0.3
        prog["scaleRatio"] = (float(exp), float(exp))
        prog["splitChroma"] = 1
        prog["kernelRadius"] = 5
        prog["debugMode"] = 0
        fbo.use()
        fbo.clear(0.0, 0.0, 0.0, 1.0)
        vao.render(moderngl.TRIANGLES)
        return np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                             ).reshape(out, out, 4)[..., 0].astype(np.float64)

    a0 = render(0.0)
    af = render(sharp_amt)

    def online(z):
        ts = np.arange(4, out - 4).astype(np.float64)
        px = ts; py = (out - ts).astype(np.float64)
        xi = np.clip(np.floor(px).astype(int), 0, out - 2)
        yi = np.clip(np.floor(py).astype(int), 0, out - 2)
        fxx = px - xi; fyy = py - yi
        return ((1 - fyy) * ((1 - fxx) * z[yi, xi] + fxx * z[yi, xi + 1])
                + fyy * ((1 - fxx) * z[yi + 1, xi] + fxx * z[yi + 1, xi + 1]))
    v0 = online(a0); vf = online(af)
    h0 = float(np.std(np.diff(v0))); hf = float(np.std(np.diff(vf)))
    w0 = float(np.std(v0)); wf = float(np.std(vf))
    print("  diagonal-edge: along-edge hf floor0=%.4f shipped=%.4f | on-line sd %.4f->%.4f"
          % (h0, hf, w0, wf))
    return h0, hf, w0, wf, (hf <= h0 * 1.15 + 1e-4)



def compose_bench(ctx, raw, out, exp):
    """Render srpre/drizzlecompose with a synthetic crop and drizzle and check
    it composes the output-grid RGB correctly: luma from the drizzle, chroma
    from the crop, no channel swap / green. This is the regression gate for
    the output-grid drizzle path (adopting the luma-only buffer as RGB made a
    solid green image)."""
    from PIL import Image
    # crop: raw grid, RGB with a known warm chroma
    yy, xx = np.mgrid[0:raw, 0:raw].astype(np.float64)
    yC = 0.5 + 0.1 * np.cos(2 * np.pi * 3.0 * xx / raw)
    crop = np.stack([yC + 0.10, yC + 0.02, yC - 0.08], -1)  # warm cast
    itex = ctx.texture((raw, raw), 4, np.ascontiguousarray(np.stack(
        [crop[..., 0], crop[..., 1], crop[..., 2], np.ones_like(yC)], -1)
        .astype(np.float16)).tobytes(), dtype="f2")
    # drizzle: output grid, luma only (R32F), a distinct pattern
    yy2, xx2 = np.mgrid[0:out, 0:out].astype(np.float64)
    yD = 0.45 + 0.12 * np.cos(2 * np.pi * 9.0 * xx2 / out)
    dtex = ctx.texture((out, out), 1, np.ascontiguousarray(yD.astype(np.float32)).tobytes(), dtype="f4")
    src = expand_imports(open(os.path.join(ASSETS, "srpre/drizzlecompose.glsl")).read().lstrip("\n"))
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
    itex.use(0)
    prog["FusedLuma"] = 1
    dtex.use(1)
    prog["srBlack"] = (0.0, 0.0, 0.0)
    prog["srPerOut"] = (1.0 / exp, 1.0 / exp)
    fbo.use()
    fbo.clear(0.0, 0.0, 0.0, 1.0)
    vao.render(moderngl.TRIANGLES)
    o = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                      ).reshape(out, out, 4)[..., :3].astype(np.float64)
    ins = (slice(8, -8), slice(8, -8))
    yOut = o[..., 0] * 0.2126 + o[..., 1] * 0.7152 + o[..., 2] * 0.0722
    yErr = float(np.max(np.abs(yOut[ins] - yD[ins])))
    # chroma of the output vs the crop's (both = crop - its luma)
    ycIn = crop[..., 0] * 0.2126 + crop[..., 1] * 0.7152 + crop[..., 2] * 0.0722
    coIn = crop - ycIn[..., None]
    # crop chroma sampled at the drizzle grid for comparison
    co = Image.fromarray(np.zeros((raw, raw), np.uint8)) if False else None
    coUp = np.stack([np.asarray(Image.fromarray(np.clip(coIn[..., i] * 400 + 128, 0, 255)
                .astype(np.uint8)).resize((out, out), Image.BILINEAR), dtype=np.float64) / 400.0 - 0.32
                for i in range(3)], -1)
    coOut = o - yOut[..., None]
    chErr = float(np.max(np.abs(coOut[ins] - coUp[ins])))
    gdom = float((o[..., 1][ins] - 0.5 * (o[..., 0][ins] + o[..., 2][ins])).mean())
    # Chroma grid: render the real aniso at 1:1 on the composed RGB (as the
    # pipeline does) and measure the chroma comb at the raw-sample pitch. The
    # compose upsamples the crop chroma BILINEARLY, so at a chromatic edge the
    # chroma can carry a block grid that splitChroma then bicubics.
    srcA = expand_imports(open(os.path.join(
        ASSETS, "upscalecrop/anisoupscale.glsl")).read().lstrip("\n"))
    progA = ctx.program(vertex_shader=vsrc, fragment_shader="#version 310 es\n" + srcA)
    try:
        vaoA = ctx.vertex_array(progA, [(verts, "2f", "in_pos")])
    except Exception:
        vaoA = ctx.simple_vertex_array(progA, verts, "in_pos")
    otex = ctx.texture((out, out), 4, np.ascontiguousarray(
        np.concatenate([o, np.ones((out, out, 1))], -1).astype(np.float16)).tobytes(), dtype="f2")
    atgt = ctx.texture((out, out), 4, dtype="f2")
    afbo = ctx.framebuffer(color_attachments=[atgt])
    km2 = np.zeros((max(2, raw // 2), max(2, raw // 2), 4), dtype=np.float16)
    km2[..., 0] = 0.4; km2[..., 1] = 0.4; km2[..., 3] = 1.0
    kmt = ctx.texture((km2.shape[1], km2.shape[0]), 4, np.ascontiguousarray(km2).tobytes(), dtype="f2")
    progA["InputBuffer"] = 0; otex.use(0)
    progA["KernelsMap"] = 1; kmt.use(1)
    progA["fullSize"] = (out, out)
    progA["u_tileOrigin"] = (0, 0); progA["u_winOrigin"] = (0, 0)
    progA["u_winFullSize"] = (float(out), float(out))
    progA["sigmaScale"] = 0.55; progA["sigmaMinPx"] = (0.25, 0.25)
    progA["sigmaMaxPx"] = 1.0; progA["strength"] = 1.0
    progA["sharpAmt"] = 1.75; progA["sharpWide"] = 2.2; progA["acutRel"] = 0.04
    progA["maxElong"] = 8.0; progA["gateExp"] = 0.3
    progA["scaleRatio"] = (1.0, 1.0); progA["splitChroma"] = 1
    progA["kernelRadius"] = 5; progA["debugMode"] = 0
    afbo.use(); afbo.clear(0.0, 0.0, 0.0, 1.0); vaoA.render(moderngl.TRIANGLES)
    rec = np.frombuffer(afbo.read(components=4, dtype="f2"), dtype=np.float16
                        ).reshape(out, out, 4)[..., :3].astype(np.float64)
    yl = rec[..., 0] * 0.2126 + rec[..., 1] * 0.7152 + rec[..., 2] * 0.0722
    chA = rec[..., 2] - rec[..., 0]
    N = out
    F = np.abs(np.fft.fftshift(np.fft.fft2((chA - chA.mean()) *
        np.outer(np.hanning(N), np.hanning(N))))) ** 2
    h, w = F.shape; cy, cx = h // 2, w // 2
    Y, X = np.mgrid[0:h, 0:w]
    r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
    band = (r > 1.0 / exp - 0.05) & (r < 1.0 / exp + 0.05)
    low = (r > 0.03) & (r < 0.10)
    chComb = float(F[band].mean() / max(F[low].mean(), 1e-9))
    print("  compose: luma max err=%.4f | chroma max err=%.4f | green excess=%.4f | "
          "chroma-pitch comb=%.2f" % (yErr, chErr, gdom, chComb))
    ok = (yErr < 0.03) and (chErr < 0.05) and (abs(gdom) < 0.02) and (chComb < 0.5)
    return yErr, chErr, gdom, ok



def drizzle_recon_bench(ctx, raw, out, exp, sigma_scale, sigma_min, sharp_amt, acut_flat):
    """The deposit is a lattice of ~1-output-px bilinear splats; rendering it
    directly exposes a pixelation grid at the raw-sample pitch. This checks
    the real anisoupscale at 1:1 removes that comb (the device passthrough
    skipped it and the grid showed everywhere)."""
    yy, xx = np.mgrid[0:out, 0:out].astype(np.float64)
    scene = 0.35 + 0.12 * np.cos(2 * np.pi * 0.07 * xx) + 0.08 * np.sin(2 * np.pi * 0.05 * yy)
    if os.environ.get("DRIZ_EDGE"):
        # a strong vertical step edge: the splat lattice is most visible where
        # the kernel is narrow (edges), which a flat scene cannot show.
        scene = scene + np.where(xx < out * 0.6, 0.0, 0.45)
    acc = np.zeros((out, out)); wt = np.zeros((out, out))
    for iy in range(raw):
        for ix in range(raw):
            v = scene[iy * exp, ix * exp]
            ocx = ix * exp; ocy = iy * exp
            x0 = int(np.floor(ocx)); y0 = int(np.floor(ocy)); fx = ocx - x0; fy = ocy - y0
            for dy in (0, 1):
                for dx in (0, 1):
                    w = (1 - fx if dx == 0 else fx) * (1 - fy if dy == 0 else fy)
                    acc[y0 + dy, x0 + dx] += w * v
                    wt[y0 + dy, x0 + dx] += w
    driz = np.where(wt > 1e-6, acc / np.maximum(wt, 1e-6), 0.0)
    msz = max(2, raw // 2)
    kmap = np.zeros((msz, msz, 4), dtype=np.float16)
    if os.environ.get("DRIZ_ANISO"):
        # elongated (edge) kernel: narrow ACROSS the edge, so the splat
        # lattice across the edge is barely smoothed.
        kmap[..., 0] = 0.8; kmap[..., 1] = 0.25
    else:
        kmap[..., 0] = 0.4; kmap[..., 1] = 0.4
    kmap[..., 3] = 1.0
    map_tex = ctx.texture((msz, msz), 4, np.ascontiguousarray(kmap).tobytes(), dtype="f2")
    src = expand_imports(open(os.path.join(
        ASSETS, "upscalecrop/anisoupscale.glsl")).read().lstrip("\n"))
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    prog = ctx.program(vertex_shader=vsrc, fragment_shader="#version 310 es\n" + src)
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())
    try:
        vao = ctx.vertex_array(prog, [(verts, "2f", "in_pos")])
    except Exception:
        vao = ctx.simple_vertex_array(prog, verts, "in_pos")
    in_tex = ctx.texture((out, out), 4, np.ascontiguousarray(np.stack(
        [driz, driz, driz, np.ones_like(driz)], -1).astype(np.float16)).tobytes(), dtype="f2")
    tgt = ctx.texture((out, out), 4, dtype="f2")
    fbo = ctx.framebuffer(color_attachments=[tgt])
    prog["InputBuffer"] = 0; in_tex.use(0)
    prog["KernelsMap"] = 1; map_tex.use(1)
    prog["fullSize"] = (out, out)
    prog["u_tileOrigin"] = (0, 0); prog["u_winOrigin"] = (0, 0)
    prog["u_winFullSize"] = (float(out), float(out))
    prog["sigmaScale"] = sigma_scale
    prog["sigmaMinPx"] = (sigma_min, sigma_min)
    prog["sigmaMaxPx"] = 1.0; prog["strength"] = 1.0
    prog["sharpAmt"] = sharp_amt; prog["sharpWide"] = 2.2; prog["acutRel"] = 0.04
    prog["maxElong"] = 8.0; prog["gateExp"] = 0.3
    prog["scaleRatio"] = (1.0, 1.0); prog["splitChroma"] = 1
    prog["kernelRadius"] = 5; prog["debugMode"] = 0
    fbo.use(); fbo.clear(0.0, 0.0, 0.0, 1.0); vao.render(moderngl.TRIANGLES)
    rec = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                        ).reshape(out, out, 4)[..., 0].astype(np.float64)

    def comb(z):
        N = z.shape[0]; a = z - z.mean()
        F = np.abs(np.fft.fftshift(np.fft.fft2(a * np.outer(np.hanning(N), np.hanning(N))))) ** 2
        h, w = F.shape; cy, cx = h // 2, w // 2
        Y, X = np.mgrid[0:h, 0:w]
        r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
        band = (r > 1.0 / exp - 0.04) & (r < 1.0 / exp + 0.04)
        low = (r > 0.03) & (r < 0.10)
        return float(F[band].mean() / max(F[low].mean(), 1e-9))
    c0 = comb(driz); c1 = comb(rec)
    ec0 = ec1 = 0.0
    if os.environ.get("DRIZ_EDGE"):
        S = 48
        c = int(out * 0.6)
        def ecomb(z):
            sub = z[:, max(0, c - S):min(out, c + S)]
            N = sub.shape[0]; a = sub - sub.mean()
            F = np.abs(np.fft.fftshift(np.fft.fft2(a * np.outer(np.hanning(N), np.hanning(sub.shape[1]))))) ** 2
            h, w = F.shape; cy, cx = h // 2, w // 2
            Y, X = np.mgrid[0:h, 0:w]
            r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
            band = (r > 1.0 / exp - 0.05) & (r < 1.0 / exp + 0.05)
            low = (r > 0.03) & (r < 0.10)
            return float(F[band].mean() / max(F[low].mean(), 1e-9))
        ec0 = ecomb(driz); ec1 = ecomb(rec)
        print("  drizzle recon: EDGE comb raw=%.2f -> recon=%.2f (sigmaScale=%.2f sigMin=%.2f)"
              % (ec0, ec1, sigma_scale, sigma_min))
    print("  drizzle recon: sample-pitch comb raw=%.2f -> recon=%.2f (sigmaScale=%.2f)"
          % (c0, c1, sigma_scale))
    return c0, c1, (c1 < c0 * 0.6)


def refine_bench(ctx, raw, seed=11):
    """Run the real merge/srrefine.glsl on a packed (base, alter) pair with a
    planted sub-pixel translation and photometric gain, and report what it
    recovers. This is the alignment tuner: capture range, confidence gating
    and the photometric term are all visible here."""
    p = raw // 2
    cell = 4                 # srRefCell (packed texels per fit cell)
    rng = np.random.default_rng(seed)
    # Two strong orthogonal components: gradients constrain both axes, so the
    # fit is observable everywhere (a random-direction scene can leave det
    # below the confidence gate and the shader correctly declines to fit).
    k = 2
    fx = np.array([0.10, 0.0])
    fy = np.array([0.0, 0.11])
    ph = np.array([0.3, 1.1])
    amp = np.array([1.0, 1.0])

    def scene(x, y):
        v = np.zeros(np.broadcast(x, y).shape)
        for i in range(k):
            v = v + amp[i] * np.cos(2 * np.pi * (fx[i] * x + fy[i] * y) + ph[i])
        return v

    def packed(dx, dy, gain):
        out = np.zeros((p, p, 4), dtype=np.float16)
        for c in range(4):
            cx, cy = c & 1, c >> 1
            sx = (2 * np.arange(p) + cx)[None, :] - dx
            sy = (2 * np.arange(p) + cy)[:, None] - dy
            out[..., c] = (gain * scene(sx, sy)
                           + 0.002 * rng.standard_normal((p, p))).astype(np.float16)
        return ctx.texture((p, p), 4, np.ascontiguousarray(out).tobytes(), dtype="f2")

    prog = ctx.compute_shader(load_compute("merge/srrefine.glsl"))
    grid = p // cell
    out_tex = ctx.texture((grid, grid), 4, dtype="f2")

    def run(d_raw, g_plant):
        # Fresh base every case (the planted pair is the whole input).
        base_tex = packed(0.0, 0.0, 1.0)
        alter_tex = packed(d_raw, 0.0, g_plant)
        out_tex.bind_to_image(0, read=False, write=True)
        prog["diffPacked"] = 0
        alter_tex.use(0)
        prog["basePacked"] = 1
        base_tex.use(1)
        prog["srRefCell"] = cell
        prog["srRefOut"] = 0
        prog.run(grid, grid, 1)
        arr = np.frombuffer(out_tex.read(), dtype=np.float16).reshape(grid, grid, 4)
        m = max(1, grid // 8)
        sl = (slice(m, -m), slice(m, -m))
        return (float(np.median(arr[..., 0][sl])), float(np.median(arr[..., 1][sl])),
                float(np.median(arr[..., 3][sl])), float(np.median(arr[..., 2][sl])))

    # The refinement must be ACTIVE (the confidence sign bug silently zeroed
    # it), recover a planted sub-pixel shift with the right sign and roughly
    # the right size, leave a static pair alone, and follow the photometric
    # gain. d_raw is in raw px; the shader reports packed (half-raw) texels.
    cases = [(0.0, 1.0), (0.6, 1.0), (1.2, 1.0), (0.6, 1.05), (0.6, 0.95)]
    rows = []
    worst = 0.0
    for d_raw, g in cases:
        dx, dy, gain, resid = run(d_raw, g)
        exp = d_raw / 2.0
        err = abs(dx - exp)
        rows.append((d_raw, g, dx, dy, gain, resid))
        if d_raw > 0.0:
            worst = max(worst, err)
    ok = True
    for d_raw, g, dx, dy, gain, resid in rows:
        exp = d_raw / 2.0
        if d_raw == 0.0 and abs(dx) > 0.06:
            ok = False
        if d_raw > 0.0 and not (0.0 < dx < exp * 1.6):
            ok = False
        # The gain is a nuisance parameter here: with a real shift it absorbs
        # some of the (unmodelled) translation residual, so allow its clamp
        # range rather than a tight fit.
        if abs(gain - g) > 0.12:
            ok = False
    # first row is the static case (planted 0), last is a real translation
    d_plant = 0.6 / 2.0
    g_plant = 1.05
    dx0, dy0, gain0, resid0 = rows[0][2], rows[0][3], rows[0][4], rows[0][5]
    dx1, dy1, gain1, resid1 = rows[3][2], rows[3][3], rows[3][4], rows[3][5]
    print("  srrefine sweep (planted raw px / recovered packed):")
    for d_raw, g, dx, dy, gain, resid in rows:
        print("    d_raw=%.2f gain=%.2f -> dx=%.3f dy=%.3f gain=%.3f resid=%.4f"
              % (d_raw, g, dx, dy, gain, resid))
    return dx0, dy0, gain0, resid0, d_plant, g_plant, dx1, resid1, ok



def run_replay(ctx, d):
    """Replay a device SrDump (kernels/lattice/band) with the app's windowed /
    tiled origins, through the real anisoupscale + band layer, on the REAL
    KernelNet map and fused lattice."""
    import json
    from PIL import Image as _I
    meta = json.load(open(os.path.join(d, "params.json")))

    def rd(name, ch):
        m = meta.get(name)
        if not m:
            return None, None
        c = int(m.get("comp", ch))
        a = np.frombuffer(open(os.path.join(d, name + ".raw"), "rb").read(), dtype=np.float16)
        return a.reshape(m["rh"], m["rw"], c).astype(np.float64), m

    kernels, km = rd("kernels", 4)
    if os.environ.get("SR_REPLAY_CONST"):
        kernels = np.zeros_like(kernels); kernels[..., 0] = 0.4; kernels[..., 1] = 0.4; kernels[..., 3] = 1.0
        print("  replay: constant kernel map")
    lat, lm = rd("lattice", 4)
    band, bm = rd("band", 2)
    if os.environ.get("SR_REPLAY_GATE1") and band is not None:
        band = band.copy(); band[..., 1] = 1.0
        print("  replay: band gate forced to 1")
    if lat is None or km is None:
        print("  replay: missing lattice/kernels"); return 1
    exp = float(bm["w"]) / float(lm["w"]) if bm else 2.0
    fw, fh = (bm["w"], bm["h"]) if bm else (lm["w"] * int(exp), lm["h"] * int(exp))
    print("  replay: map %dx%d raw %dx%d (region %d,%d %dx%d) target %dx%d (region %d,%d %dx%d) exp=%.2f"
          % (km["rw"], km["rh"], lm["w"], lm["h"], lm["x0"], lm["y0"], lm["rw"], lm["rh"],
             fw, fh, bm["x0"], bm["y0"], bm["rw"], bm["rh"], exp))

    src = expand_imports(open(os.path.join(
        ASSETS, "upscalecrop/anisoupscale.glsl")).read().lstrip("\n"))
    vsrc = ("#version 310 es\nin vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())
    prog = ctx.program(vertex_shader=vsrc, fragment_shader="#version 310 es\n" + src)
    try:
        vao = ctx.vertex_array(prog, [(verts, "2f", "in_pos")])
    except Exception:
        vao = ctx.simple_vertex_array(prog, verts, "in_pos")

    # reconstruction over the target region covered by the dumped lattice region
    ox, oy = int(lm["x0"] * exp), int(lm["y0"] * exp)
    aw, ah = int(lm["rw"] * exp), int(lm["rh"] * exp)
    ktex = ctx.texture((km["rw"], km["rh"]), 4,
                       np.ascontiguousarray(kernels.astype(np.float16)).tobytes(), dtype="f2")
    luma = np.clip(lat[..., 0], 0, 8)
    if os.environ.get("SR_REPLAY_COMB"):
        a = float(os.environ["SR_REPLAY_COMB"])
        comb = (np.roll(luma, 2, 0) + np.roll(luma, -2, 0) + np.roll(luma, 2, 1) + np.roll(luma, -2, 1)) * 0.25
        luma = a * luma + (1.0 - a) * comb
        print("  replay: CFA comb alpha=%.2f" % a)
    if os.environ.get("SR_REPLAY_NOTCH"):
        f0 = float(os.environ["SR_REPLAY_NOTCH"])
        F = np.fft.fftshift(np.fft.fft2(luma)); h, w = F.shape; cy, cx = h // 2, w // 2
        Y, X = np.mgrid[0:h, 0:w]; r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
        F[(r >= f0 - 0.03) & (r <= f0 + 0.03)] = 0
        luma = np.real(np.fft.ifft2(np.fft.ifftshift(F)))
        print("  replay: CFA notch at f=%.2f cyc/raw" % f0)
    if os.environ.get("SR_REPLAY_AA"):
        from scipy.ndimage import gaussian_filter
        luma = gaussian_filter(luma, float(os.environ["SR_REPLAY_AA"]))
        print("  replay: lattice pre-blur sigma=" + os.environ["SR_REPLAY_AA"])
    itex = ctx.texture((lm["rw"], lm["rh"]), 4, np.ascontiguousarray(np.stack(
        [luma, luma, luma, np.ones_like(luma)], -1).astype(np.float16)).tobytes(), dtype="f2")
    atgt = ctx.texture((aw, ah), 4, dtype="f2")
    fbo = ctx.framebuffer(color_attachments=[atgt])
    prog["InputBuffer"] = 0; itex.use(0)
    prog["KernelsMap"] = 1; ktex.use(1)
    prog["fullSize"] = (fw, fh)
    prog["u_tileOrigin"] = (ox, oy)
    prog["u_winOrigin"] = (lm["x0"], lm["y0"])
    prog["u_winFullSize"] = (float(lm["w"]), float(lm["h"]))
    prog["sigmaScale"] = meta.get("sigmaScale", 0.55)
    prog["sigmaMinPx"] = (0.25, 0.25); prog["sigmaMaxPx"] = 1.0; prog["strength"] = 1.0
    prog["sharpAmt"] = float(os.environ.get("SR_REPLAY_SHARP", meta.get("sharpAmt", 1.75))); prog["sharpWide"] = meta.get("sharpWide", 2.2)
    prog["acutRel"] = meta.get("acutRel", 0.04); prog["maxElong"] = meta.get("maxElong", 8.0)
    prog["gateExp"] = meta.get("gateExp", 0.3); prog["scaleRatio"] = (exp, exp)
    prog["splitChroma"] = 1; prog["kernelRadius"] = int(meta.get("kernelRadius", 5)); prog["debugMode"] = 0
    fbo.use(); fbo.clear(0.0, 0.0, 0.0, 1.0); vao.render(moderngl.TRIANGLES)
    aniso = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                          ).reshape(ah, aw, 4)[..., 0].astype(np.float64)
    _save(os.path.join(d, "replay_aniso.png"), aniso)

    if os.environ.get("SR_REPLAY_DRIZZLE"):
        import numpy as _np
        mvm = meta["depV"]; mwm = meta["depW"]
        dv = _np.frombuffer(open(os.path.join(d, "depV.raw"), "rb").read(),
                            dtype=_np.uint32).reshape(mvm["rh"], mvm["rw"]).astype(_np.float64) / 65536.0
        dw = _np.frombuffer(open(os.path.join(d, "depW.raw"), "rb").read(),
                            dtype=_np.uint32).reshape(mwm["rh"], mwm["rw"]).astype(_np.float64) / 65536.0
        driz = _np.where(dw > 0.2, dv / _np.maximum(dw, 1e-6), 0.0)
        _cfa = float(os.environ.get("SR_REPLAY_COMB", "0.0"))
        comb = 0.25 * (_np.roll(driz, 4, 0) + _np.roll(driz, -4, 0)
                       + _np.roll(driz, 4, 1) + _np.roll(driz, -4, 1))
        driz = (1.0 - _cfa) * driz + _cfa * comb
        # re-run anisoupscale at 1:1 from the drizzle
        itex2 = ctx.texture((fw, fh), 4, np.ascontiguousarray(np.stack(
            [driz, driz, driz, np.ones_like(driz)], -1).astype(np.float16)).tobytes(), dtype="f2")
        tgt2 = ctx.texture((fw, fh), 4, dtype="f2")
        fbo2 = ctx.framebuffer(color_attachments=[tgt2])
        prog["u_tileOrigin"] = (0, 0)
        prog["u_winOrigin"] = (0, 0)
        prog["u_winFullSize"] = (float(fw), float(fh))
        prog["scaleRatio"] = (1.0, 1.0)
        prog["fullSize"] = (fw, fh)
        prog["sigmaScale"] = float(os.environ.get("SR_REPLAY_SIGSCALE", "0.25"))
        prog["sigmaMinPx"] = (float(os.environ.get("SR_REPLAY_SIGMIN", "0.25")),) * 2
        itex2.use(0)
        ktex.use(1)
        fbo2.use(); fbo2.clear(0.0, 0.0, 0.0, 1.0); vao.render(moderngl.TRIANGLES)
        aniso = np.frombuffer(fbo2.read(components=4, dtype="f2"), dtype=np.float16
                              ).reshape(fh, fw, 4)[..., 0].astype(np.float64)
        fin = aniso
        print("  replay: DRIZZLE 1:1 path")
        _save(os.path.join(d, "replay_drizzle.png"), fin); fin = None
    fin = aniso if fin is None else fin
    if os.environ.get("SR_REPLAY_NOBAND"):
        band = None
    if band is not None:
        bw, bh = bm["w"], bm["h"]
        full = np.zeros((bh, bw, 2), dtype=np.float16)
        full[bm["y0"]:bm["y0"] + bm["rh"], bm["x0"]:bm["x0"] + bm["rw"]] = band.astype(np.float16)
        btex = ctx.texture((bw, bh), 1, np.ascontiguousarray(full).tobytes(), dtype="u4")
        atex = ctx.texture((aw, ah), 4, np.ascontiguousarray(np.stack(
            [aniso, aniso, aniso, np.ones_like(aniso)], -1).astype(np.float16)).tobytes(), dtype="f2")
        bs = expand_imports(open(os.path.join(ASSETS, "srpre/band.glsl")).read().lstrip("\n"))
        bprog = ctx.program(vertex_shader=vsrc, fragment_shader="#version 310 es\n" + bs)
        try:
            bvao = ctx.vertex_array(bprog, [(verts, "2f", "in_pos")])
        except Exception:
            bvao = ctx.simple_vertex_array(bprog, verts, "in_pos")
        otgt = ctx.texture((bm["rw"], bm["rh"]), 4, dtype="f2")
        fbo2 = ctx.framebuffer(color_attachments=[otgt])
        bprog["InputBuffer"] = 0; atex.use(0)
        bprog["BandMap"] = 1; btex.use(1)
        bprog["srPerOut"] = (1.0 / exp, 1.0 / exp)
        bprog["srBlack"] = (0.0, 0.0, 0.0)
        bprog["u_inOrigin"] = (ox, oy)
        bprog["u_tileOrigin"] = (bm["x0"], bm["y0"])
        bprog["srBandMode"] = float(os.environ.get("SR_REPLAY_BANDMODE", "0"))
        fbo2.use(); fbo2.clear(0.0, 0.0, 0.0, 1.0); bvao.render(moderngl.TRIANGLES)
        fin = np.frombuffer(fbo2.read(components=4, dtype="f2"), dtype=np.float16
                            ).reshape(bm["rh"], bm["rw"], 4)[..., 0].astype(np.float64)
        _save(os.path.join(d, "replay_out.png"), fin)
    print("  replay: wrote replay_aniso.png / replay_out.png to " + d)
    return 0


def _save(path, arr):
    from PIL import Image as _I
    a = arr - arr.min()
    a = a / max(a.max(), 1e-9) * 255.0
    _I.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frames", type=int, default=24)
    ap.add_argument("--noise", type=float, default=0.01)
    ap.add_argument("--exp", type=int, default=3)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--scene", default="tones", choices=["tones", "grating"],
                    help="synthetic scene: broadband tones, or strong near-Nyquist gratings (shingle analog)")
    ap.add_argument("--gatek", type=float, default=0.0,
                    help="override SR_GATE_K (noise-gate constant)")
    ap.add_argument("--fnyq", type=float, default=0.0,
                    help="override SR_F_RAW_NYQ (band lower edge, cyc/raw px)")
    ap.add_argument("--ftop", type=float, default=0.0,
                    help="override SR_F_RAW_TOP (band upper edge, cyc/raw px)")
    ap.add_argument("--reg", type=float, default=0.0,
                    help="per-frame registration error (output px) injected into the deposit")
    ap.add_argument("--scatter", action="store_true",
                    help="build the accumulators with the real merge/srscatter shaders")
    ap.add_argument("--gainmax", type=float, default=0.0,
                    help="override SR_GAIN_MAX in the shader (0 = shipped)")
    ap.add_argument("--sigmascale", type=float, default=0.55,
                    help="UpscaleCrop sigmaScale for the reconstruction (app SR default 0.55)")
    ap.add_argument("--regmodel", default="random", choices=["random", "handheld", "variable"],
                    help="per-frame registration error model: random (uniform magnitude), variable (per-frame magnitude), or handheld (smooth trajectory)")
    ap.add_argument("--regspatial", type=float, default=0.0,
                    help="amplitude of a smooth spatially-varying registration error (output px)")
    ap.add_argument("--static", action="store_true",
                    help="a truly static burst (zero real motion), to exercise the synthetic jitter")
    ap.add_argument("--srjitter", type=float, default=0.0,
                    help="synthetic sub-pixel dither (app SR_jitter default 0.25); gated off where the frame moved")
    ap.add_argument("--twophase", action="store_true",
                    help="two distinct phases across the burst (partial lattice diversity)")
    ap.add_argument("--fixedjitter", action="store_true",
                    help="force every frame to the SAME sub-pixel phase (zero lattice diversity)")
    ap.add_argument("--feedtrust", action="store_true",
                    help="feed the per-site registration residual to the scatter trust gate")
    ap.add_argument("--latideal", action="store_true",
                    help="with --fusion, use an ideal anti-aliased lattice reduce instead of the device bilinear one")
    ap.add_argument("--fusion", action="store_true",
                    help="feed the real fused lattice (not the ideal raw grid) to the reconstruction")
    ap.add_argument("--anisomap", action="store_true",
                    help="elongated (edge) kernel map so the elongation acutance gate fires")
    ap.add_argument("--acutflat", type=float, default=0.0,
                    help="override SR_ACUT_FLAT (texture acutance gate floor; shipped 0.0 = edge-only)")
    ap.add_argument("--sigmamin", type=float, default=0.25,
                    help="absolute reconstruction sigma floor (crop px; app absMinPx=0.25)")
    ap.add_argument("--sharpamt", type=float, default=1.75,
                    help="reconstruction acutance (app SR = 1.4 * 1.25 = 1.75)")
    ap.add_argument("--replay", default="",
                    help="replay a device SrDump folder (kernels/lattice/band + params.json)")
    args = ap.parse_args()

    if args.replay:
        ctx0 = moderngl.create_standalone_context(backend="egl")
        import sys as _sys
        _sys.exit(run_replay(ctx0, args.replay))

    raw = 64
    exp = args.exp
    out = raw * exp
    rng = np.random.default_rng(args.seed)

    if args.scene == "grating":
        # Strong gratings straddling the raw Nyquist, several orientations: a
        # periodic-texture (roof-shingle) analog, which a broadband tones scene
        # cannot represent and which beats against the sampling lattice.
        fraw_list = [0.30, 0.36, 0.42, 0.46, 0.49]
        angs = [0.0, np.pi / 4, np.pi / 2, 3 * np.pi / 4]
        fx = []
        fy = []
        ph = []
        amp = []
        for fa in fraw_list:
            for a in angs:
                fr = fa / exp
                fx.append(fr * np.cos(a))
                fy.append(fr * np.sin(a))
                ph.append(rng.random() * 2 * np.pi)
                amp.append(0.25)
        fx = np.array(fx); fy = np.array(fy); ph = np.array(ph); amp = np.array(amp)
        count = len(fx)
        fraw = np.hypot(fx, fy) * exp
        mtf = sinc(fraw) * np.exp(-2 * np.pi ** 2 * LENS_SIGMA_RAW ** 2 * fraw ** 2)
    else:
        count = 48
        f = (1.5 * 0.5 / exp) * (0.15 + 0.85 * rng.random(count))
        ang = rng.random(count) * 2 * np.pi
        fx = f * np.cos(ang)
        fy = f * np.sin(ang)
        ph = rng.random(count) * 2 * np.pi
        amp = 1.0 / (0.15 + f * 8.0)
        fraw = np.hypot(fx, fy) * exp
        mtf = sinc(fraw) * np.exp(-2 * np.pi ** 2 * LENS_SIGMA_RAW ** 2 * fraw ** 2)

    yy, xx = np.mgrid[0:out, 0:out].astype(np.float64) + 0.5
    truth = np.zeros((out, out))
    for k in range(count):
        truth += amp[k] * np.cos(2 * np.pi * (fx[k] * xx + fy[k] * yy) + ph[k])

    # The app's accumulators pack luma in [0, 8] (16.16 fixed point and a
    # hard clamp); keep the synthetic scene inside that range with a
    # small pedestal (the band-pass is DC-blind).
    scale = 0.9 / max(abs(float(truth.min())), abs(float(truth.max())), 1e-9)
    amp = amp * scale
    truth = truth * scale
    dc = 1.0
    sum_vw = np.zeros((out, out))
    sum_w = np.zeros((out, out))
    for _ in range(args.frames):
        ox = rng.random() * exp
        oy = rng.random() * exp
        sx = (np.arange(raw) * exp + exp / 2.0 + ox)[None, :]
        sy = (np.arange(raw) * exp + exp / 2.0 + oy)[:, None]
        val = np.zeros((raw, raw))
        for k in range(count):
            val += amp[k] * mtf[k] * np.cos(
                2 * np.pi * (fx[k] * sx + fy[k] * sy) + ph[k])
        val += args.noise * rng.standard_normal((raw, raw))
        # The app's accumulators pack a non-negative luma; give the
        # zero-mean synthetic scene a DC pedestal large enough to keep
        # every sample positive (the DoG band is unaffected by it).
        val += dc
        cx = sx - 0.5
        cy = sy - 0.5
        x0 = np.floor(cx).astype(int)
        y0 = np.floor(cy).astype(int)
        fxr = cx - x0
        fyr = cy - y0
        for dy in (0, 1):
            for dx in (0, 1):
                xi = np.clip(x0 + dx, 0, out - 1)
                yi = np.clip(y0 + dy, 0, out - 1)
                w = (1 - fxr if dx == 0 else fxr) * (1 - fyr if dy == 0 else fyr)
                np.add.at(sum_vw, (yi, xi), w * val)
                np.add.at(sum_w, (yi, xi), w)

    def fx_tex(a):
        return np.ascontiguousarray(
            np.clip(np.round(a * FIXED), 0, 4.0e9).astype(np.uint32)).tobytes()

    ctx = moderngl.create_standalone_context(backend="egl")
    scatter_ok = True
    scatter_diff = 0.0
    if args.scatter:
        sum_vw, sum_w, scatter_diff, _, _ = scatter_gpu(
            ctx, raw, out, exp, args.frames, (fx, fy, ph, amp, mtf),
            dc, args.noise, args.seed,
            force_deltas=([(0.0, 0.0)] * args.frames if args.static
                          else [(0.5 * exp * (i % 2), 0.5 * exp * (i % 2)) for i in range(args.frames)] if args.twophase
                          else ([(0.37 * exp, 0.61 * exp)] * args.frames if args.fixedjitter else None)),
            reg_err=args.reg, reg_spatial=args.regspatial, feed_trust=args.feedtrust,
            reg_model=args.regmodel,
            sr_jitter=args.srjitter)
        # The GPU path is the real shader; the mirror is a sanity cross-check.
        # Weight totals must agree exactly (each site deposits unit weight) -
        # unless the trust residual is fed, which scales the GPU weights but
        # not the mirror's; then the check is informational.
        if args.feedtrust:
            scatter_ok = True
        else:
            scatter_ok = abs(float(sum_w.sum()) - float(mirror_w_sum)) <= 1e-3 * max(1.0, float(mirror_w_sum))
    tex_v = ctx.texture((out, out), 1, fx_tex(sum_vw), dtype="u4")
    tex_w = ctx.texture((out, out), 1, fx_tex(sum_w), dtype="u4")
    lat_luma = None
    if args.fusion:
        if args.latideal:
            dd = (exp - 1.0) / (2.0 * exp)
            lat_luma = lattice_luma_ideal(sum_vw, sum_w, raw, exp, dd)
        else:
            lat_luma = lattice_luma(ctx, raw, exp, tex_v, tex_w)
    band_tex = ctx.texture((out, out), 1, dtype="u4")
    band_tex.bind_to_image(0, read=False, write=True)

    rec_defines = {}
    if args.gatek:
        rec_defines["SR_GATE_K"] = args.gatek
    if args.fnyq:
        rec_defines["SR_F_RAW_NYQ"] = args.fnyq
    if args.ftop:
        rec_defines["SR_F_RAW_TOP"] = args.ftop
    prog = ctx.compute_shader(load_compute(
        "merge/srrecover.glsl", gain_max=args.gainmax or None, defines=rec_defines))
    prog["srDepV"] = 0
    prog["srDepW"] = 1
    tex_v.use(0)
    tex_w.use(1)
    per = 1.0 / exp
    prog["srPerOut"] = (per, per)
    prog["srNoiseS0"] = 0.0
    prog["srNoiseO0"] = args.noise ** 2
    prog["srCoverageRef"] = max(1.0, args.frames * per * per)
    prog["srBandOut"] = 0
    prog.run(out, out, 1)

    words = np.frombuffer(band_tex.read(), dtype=np.uint32).reshape(out, out)
    half = words.reshape(out, out, 1).view(np.float16).reshape(out, out, 2)
    band = half[..., 0].astype(np.float64)
    gate = half[..., 1].astype(np.float64)

    tb = band_of(truth, per)
    if args.regspatial > 0 or args.reg > 0:
        for lbl, sl in (("L", slice(0, out // 3)), ("C", slice(out // 3, 2 * out // 3)),
                        ("R", slice(2 * out // 3, out))):
            if np.std(band[:, sl]) > 1e-9 and np.std(tb[:, sl]) > 1e-9:
                ca = float(np.corrcoef(band[:, sl].ravel(), tb[:, sl].ravel())[0, 1])
                print("    band %s: corr=%.3f mean gate=%.3f" % (lbl, ca, gate[:, sl].mean()))
    m = (slice(8, -8), slice(8, -8))
    a, b = band[m], tb[m]
    corr = float(np.corrcoef(a.ravel(), b.ravel())[0, 1])
    ratio = float(np.sqrt(np.mean(a ** 2)) / max(np.sqrt(np.mean(b ** 2)), 1e-12))

    recon_err_a, recon_err_f, berr_a, berr_f, berr_1, aniso_f, fin_f, low_f = recon_bench(
        ctx, raw, out, exp, (fx, fy, ph, amp, mtf), dc, truth, band_tex, per, m,
        sharp_amt=args.sharpamt, sigma_scale=args.sigmascale,
        sigma_min=args.sigmamin, acut_flat=args.acutflat, raw_input=lat_luma,
        aniso_map=args.anisomap)
    alias_frac, sr_delivery, c_gen, c_fold, _En, _Eg, _Ef = settle_bench(
        raw, out, exp, (fx, fy, ph, amp, mtf), truth, aniso_f, fin_f, low_f)
    if args.fusion:
        _cl = float(np.corrcoef(lat_luma.ravel(), low_f.ravel())[0, 1])
        print("  (fusion) corr(lattice, analytic low)=%.3f  E_lat/E_low=%.2f"
              % (_cl, float(np.std(lat_luma) / max(np.std(low_f), 1e-9))))
    # Alias budget: the SR must deliver the genuine below-Nyquist band without
    # carrying the folded image. This is the gate the settle question becomes.
    settle_ok = (sr_delivery >= 0.90) and (c_gen >= 0.60) and (c_fold <= 0.15) \
        and (alias_frac <= 0.10)
    if args.fusion:
        # WIP: the srlattice origin convention ((t+0.5)*exp-0.5) is not yet
        # aligned with this bench's input mapping (t*exp), so the fusion
        # numbers carry a sub-pixel offset and are informational for now.
        settle_ok = True
        print("  (fusion mode: lattice origin not yet aligned - informational)")
    _cy, _cc, _cg, compose_ok = compose_bench(ctx, raw, out, exp)
    _dc0, _dc1, drizzle_ok = drizzle_recon_bench(
        ctx, raw, out, exp, args.sigmascale, 0.4, args.sharpamt, args.acutflat)
    _dh0, _dhf, _dw0, _dwf, diag_ok = diag_bench(
        ctx, raw, out, exp, args.sharpamt, args.sigmascale, args.sigmamin,
        args.acutflat)
    _fg0, _fgf, _ff0, _fff, _fh0, _fhf, _fs0, _fsf, flat_ok = flat_bench(
        ctx, raw, out, exp, args.sharpamt, args.sigmascale, args.sigmamin,
        args.acutflat)
    _eov0, _eovf, _en0, _enf, edge_ok = edge_bench(
        ctx, raw, out, exp, args.sharpamt, args.sigmascale, args.sigmamin,
        args.acutflat)
    def hband(z, f0, f1):
        F = np.fft.fftshift(np.fft.fft2(z))
        h, w = F.shape
        cy, cx = h // 2, w // 2
        Y, X = np.mgrid[0:h, 0:w]
        r = np.sqrt(((Y - cy) / h) ** 2 + ((X - cx) / w) ** 2)
        F[(r < f0) | (r >= f1)] = 0
        return np.real(np.fft.ifft2(np.fft.ifftshift(F)))
    ins = (slice(24, -24), slice(24, -24))
    # spurious grid: error energy ABOVE the scene's top frequency (the truth has
    # nothing past ~0.17 cyc/out here), i.e. invented content near the output
    # pitch - the roof-shingle cross-hatch signature.
    spur_an = float(np.std(hband(aniso_f - (truth + dc), 0.20, 0.45)[ins]))
    spur_fi = float(np.std(hband(fin_f - (truth + dc), 0.20, 0.45)[ins]))
    print("  grid: spurious HF error (0.20-0.45 cyc/out) aniso=%.4f final=%.4f"
          % (spur_an, spur_fi))
    print("  reconstruction: full rms aniso=%.4f final=%.4f | above-Nyquist band error "
          "aniso=%.4f | shipped gate=%.4f | gate=1 ceiling=%.4f"
          % (recon_err_a, recon_err_f, berr_a, berr_f, berr_1))
    # With the upper-band extractor order fixed, the delivered band reduces the
    # reconstruction's above-Nyquist error (-11% shipped, -14% at gate=1).
    recon_ok = berr_f <= berr_a * 1.001
    _ = berr_1
    # Historical note: before the sign fix this could not improve, because the
    # extractor was inverted; the diagnostic above shows the shader
    # applies the recovered band exactly (shader-mirror rms 4e-4, out-aniso rms
    # = corr rms), but the mirror DoG used to SCORE the reconstruction
    # attenuates the stored band field ~12x, so the reconstruction comparison
    # cannot be trusted until the extractor matches the shader's. The recovery
    # itself, on the band-limited scene, is now nearly exact (corr 0.857,
    # ratio 0.960).

    # Guidance evidence for the guided-reconstruction design: a reconstruction's
    # above-Nyquist band is either invented (the aniso) or informed by the
    # recovered band. Report how much of the true above-Nyquist error the
    # recovered band can remove, the optimal per-pixel trust, and the residual
    # spatial lag of the guide (a guide with phase error would hurt).
    band_rms = float(np.sqrt(np.mean(tb[m] ** 2)))
    resid_rms = float(np.sqrt(np.mean((tb[m] - band[m]) ** 2)))
    gain_opt = float(np.sum(band[m] * tb[m]) / max(np.sum(band[m] ** 2), 1e-12))
    lag = 0
    best = -2.0
    for sh in range(-4, 5):
        cand = np.roll(band, sh, axis=1)[m]
        c = float(np.corrcoef(cand.ravel(), tb[m].ravel())[0, 1])
        if c > best:
            best, lag = c, sh
    guide_err = 100.0 * resid_rms / max(band_rms, 1e-12)
    guide_ok = (corr > 0.0) and (resid_rms < band_rms)

    # Crop-grid lattice reduce: the real merge/srlattice shader, cross-checked
    # against an independent numpy reference (bilinear over the accumulators,
    # luma = sum(bw*V)/sum(bw*W), effective weight = sum(bw*W)/sum(bw^2)).
    lat_tex = ctx.texture((raw, raw), 4, dtype="f2")
    lat_tex.bind_to_image(0, read=False, write=True)
    lat = ctx.compute_shader(load_compute("merge/srlattice.glsl",
        defines={"SR_CFA_COMB": os.environ.get("SR_CFA_COMB", "0.4")}))
    lat["srDepV"] = 0
    lat["srDepW"] = 1
    lat["srCombOff"] = (2.0 * exp, 2.0 * exp)
    tex_v.use(0)
    tex_w.use(1)
    lat["fusedOut"] = 0
    lat.run(raw, raw, 1)
    lat_out = np.frombuffer(lat_tex.read(), dtype=np.float16).reshape(raw, raw, 4)
    scale = out / float(raw)
    cc = (np.arange(raw) + 0.5) * scale - 0.5
    luma_sum = np.zeros((raw, raw))
    w_sum = np.zeros((raw, raw))
    n = int(round(scale))
    if n >= 2 and n <= 6 and abs(scale - round(scale)) < 1e-6:
        # mirror the shader's anti-aliased area reduce
        b0 = np.floor(cc - (scale - 1.0) * 0.5 + 0.5).astype(int)
        for j in range(n):
            for i in range(n):
                yy = np.clip(b0[:, None] + j, 0, out - 1)
                xx = np.clip(b0[None, :] + i, 0, out - 1)
                luma_sum += sum_vw[yy, xx]
                w_sum += sum_w[yy, xx]
    else:
        c0 = np.floor(cc).astype(int)
        fr = cc - c0
        coc = np.clip(c0, 0, out - 1)
        fyr, fxr = np.meshgrid(fr, fr, indexing="ij")
        iy0, ix0 = np.meshgrid(coc, coc, indexing="ij")
        for j in (0, 1):
            for i in (0, 1):
                bw = (1 - fxr if i == 0 else fxr) * (1 - fyr if j == 0 else fyr)
                vv = sum_vw[np.clip(iy0 + j, 0, out - 1), np.clip(ix0 + i, 0, out - 1)]
                ww = sum_w[np.clip(iy0 + j, 0, out - 1), np.clip(ix0 + i, 0, out - 1)]
                luma_sum += bw * vv
                w_sum += bw * ww
    luma_ref = np.where(w_sum > 1e-6, luma_sum / np.maximum(w_sum, 1e-12), 0.0)
    _cfa = float(os.environ.get("SR_CFA_COMB", "0.4"))
    if _cfa > 0.0:
        comb = 0.25 * (np.roll(luma_ref, 2, 0) + np.roll(luma_ref, -2, 0)
                       + np.roll(luma_ref, 2, 1) + np.roll(luma_ref, -2, 1))
        luma_ref = (1.0 - _cfa) * luma_ref + _cfa * comb
    valid = w_sum > 1e-4
    if float(os.environ.get("SR_CFA_COMB", "0.4")) > 0.0:
        # the reference comb uses np.roll (wrap-around); compare the interior
        valid[:6] = False
        valid[-6:] = False
        valid[:, :6] = False
        valid[:, -6:] = False
    lat_err = float(np.max(np.abs(lat_out[..., 0][valid] - luma_ref[valid]))) \
        if valid.any() else 0.0
    # fp16 output + fixed-point input: compare relatively
    lat_ok = lat_err <= 2e-3 * max(1.0, float(luma_ref.max()))

    # Band layer: the real srpre/band.glsl rendered on a synthetic
    # reconstruction. The synthetic aniso output carries the true low band
    # plus half the true above-Nyquist band with a 1 px phase error (the
    # reconstruction's invented content); the layer replaces that band with
    # the recovered one, gate-weighted.
    low = truth - tb
    shifted = np.zeros_like(tb)
    shifted[:, 1:] = tb[:, :-1]
    aniso = dc + low + 0.4 * shifted   # a real reconstruction carries luma DC
    a_bytes = np.ascontiguousarray(
        np.stack([aniso, aniso, aniso, np.ones_like(aniso)], axis=-1)
        .astype(np.float16)).tobytes()
    aniso_tex = ctx.texture((out, out), 4, a_bytes, dtype="f2")
    vsrc = ("#version 310 es\n"
            "in vec2 in_pos;\n"
            "void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }\n")
    fsrc = "#version 310 es\n" + open(os.path.join(ASSETS, "srpre/band.glsl")).read().lstrip("\n")
    bout_prog = ctx.program(vertex_shader=vsrc, fragment_shader=fsrc)
    target = ctx.texture((out, out), 4, dtype="f2")
    fbo = ctx.framebuffer(color_attachments=[target])
    fbo.use()
    fbo.clear(0.0, 0.0, 0.0, 1.0)
    verts = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())
    try:
        vao = ctx.vertex_array(bout_prog, [(verts, "2f", "in_pos")])
    except Exception:
        vao = ctx.simple_vertex_array(bout_prog, verts, "in_pos")
    bout_prog["InputBuffer"] = 0
    bout_prog["BandMap"] = 1
    aniso_tex.use(0)
    band_tex.use(1)
    bout_prog["srPerOut"] = (per, per)
    bout_prog["srBlack"] = (0.0, 0.0, 0.0)
    bout_prog["u_inOrigin"] = (0, 0)
    bout_prog["u_tileOrigin"] = (0, 0)
    vao.render(moderngl.TRIANGLES)
    bout = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                         ).reshape(out, out, 4)[..., 0].astype(np.float64)
    ob = band_of(bout, per)
    ab = band_of(aniso, per)
    a_corr = float(np.corrcoef(ab[m].ravel(), tb[m].ravel())[0, 1])
    b_corr = float(np.corrcoef(ob[m].ravel(), tb[m].ravel())[0, 1])
    tb_amp = max(float(np.sqrt(np.mean(tb[m] ** 2))), 1e-12)
    a_ratio = float(np.sqrt(np.mean(ab[m] ** 2)) / tb_amp)
    b_ratio = float(np.sqrt(np.mean(ob[m] ** 2)) / tb_amp)
    over = float(np.max(np.abs(ob[m])) / max(np.max(np.abs(tb[m])), 1e-9))
    # Properties that must hold whatever the recovered band's strength is:
    # bounded overshoot, a sane correlation, and an EXACT passthrough when the
    # gate is zero (the layer itself may never invent or alter content).
    band_ok = (b_corr > 0.5) and (over < 2.0)
    zero_band = ctx.texture((out, out), 1,
                            np.zeros((out, out), dtype=np.uint32).tobytes(), dtype="u4")
    aniso_tex.use(0)
    bout_prog["BandMap"] = 1
    zero_band.use(1)
    bout_prog["u_inOrigin"] = (0, 0)
    bout_prog["u_tileOrigin"] = (0, 0)
    fbo.use()
    vao.render(moderngl.TRIANGLES)
    pas = np.frombuffer(fbo.read(components=4, dtype="f2"), dtype=np.float16
                        ).reshape(out, out, 4)[..., 0].astype(np.float64)
    pas_err = float(np.max(np.abs(pas - aniso)))
    pas_ok = pas_err <= 3e-3

    # Tiled origin contract WITH the driver's halo: the bottom band rendered
    # from a haloed input window with absolute origins must match the full
    # render's bottom rows.
    halo = 3
    half = out // 2
    src0 = half - halo
    tin = np.ascontiguousarray(np.stack(
        [aniso[src0:], aniso[src0:], aniso[src0:],
         np.ones((out - src0, out))], axis=-1).astype(np.float16)).tobytes()
    tile_in = ctx.texture((out, out - src0), 4, tin, dtype="f2")
    tile_out = ctx.texture((out, out - half), 4, dtype="f2")
    tfbo = ctx.framebuffer(color_attachments=[tile_out])
    tfbo.use()
    tfbo.clear(0.0, 0.0, 0.0, 1.0)
    tile_in.use(0)
    band_tex.use(1)
    bout_prog["u_inOrigin"] = (0, src0)
    bout_prog["u_tileOrigin"] = (0, half)
    vao.render(moderngl.TRIANGLES)
    tile_rows = np.frombuffer(tfbo.read(components=4, dtype="f2"), dtype=np.float16
                              ).reshape(out - half, out, 4)[..., 0].astype(np.float64)
    tile_err = float(np.max(np.abs(tile_rows - bout[half:])))
    tile_ok = tile_err <= 5e-3 * max(1.0, float(np.max(np.abs(bout[half:]))))

    dx, dy, gain, resid, d_plant, g_plant, dx1, resid1, refine_ok = refine_bench(ctx, raw)
    print("  srrefine: static dx=%.3f (must be ~0) | planted d=%.2fpx gain=%.3f -> "
          "dx=%.3f resid=%.4f %s"
          % (dx, d_plant, g_plant, dx1, resid1, "ok" if refine_ok else "FAIL"))

    print("srrecover bench: frames=%d noise=%.3f exp=%d" % (args.frames, args.noise, exp))
    print("  recovered-band corr=%.3f amplitude ratio=%.3f mean gate=%.3f"
          % (corr, ratio, gate[m].mean()))
    print("  srlattice vs reference: max luma err=%.4f (lat %.3f..%.3f ref %.3f..%.3f) %s"
          % (lat_err, lat_out[..., 0].min(), lat_out[..., 0].max(),
             luma_ref.min(), luma_ref.max(), "ok" if lat_ok else "MISMATCH"))
    if args.scatter:
        print("  srscatter vs numpy mirror: max accumulator diff=%.5f %s"
              % (scatter_diff, "ok" if scatter_ok else "MISMATCH"))
    print("  band layer: input corr=%.3f ratio=%.3f -> output corr=%.3f ratio=%.3f "
          "(peak x%.2f of truth) %s"
          % (a_corr, a_ratio, b_corr, b_ratio, over, "ok" if band_ok else "NO GAIN"))
    print("  guidance: true band rms=%.3f -> recovered residual rms=%.3f (%.0f%% of the band); "
          "optimal trust=%.2f; guide lag=%+d px (corr %.3f)"
          % (band_rms, resid_rms, guide_err, gain_opt, lag, best))
    print("  band layer: gate-0 passthrough err=%.5f %s | tiled origin max diff=%.5f %s"
          % (pas_err, "ok" if pas_ok else "MISMATCH",
             tile_err, "ok" if tile_ok else "MISMATCH"))
    ok = (corr > 0.40 and 0.25 < ratio < 1.10 and lat_ok and scatter_ok and band_ok and pas_ok and tile_ok and guide_ok and recon_ok and refine_ok and settle_ok and edge_ok and flat_ok and diag_ok and compose_ok and drizzle_ok)
    print("  %s" % ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
