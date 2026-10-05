
#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
// Running consensus in/out (target Bayer grid, value and accumulated weight
// packed into one R32UI word; ping-ponged, same pattern as the SR detail
// accumulator).
layout(r32ui, binding = 0) readonly  uniform highp uimage2D srBayIn;
layout(r32ui, binding = 1) writeonly uniform highp uimage2D srBayOut;
// Packed RGBA quad frame for this drizzle iteration (alter, or the pristine
// base frame pre-loop).
uniform highp sampler2D alterPacked;
uniform highp sampler2D alignmentTexture;
// This frame's aligned residual (mergeAlign writes the aligned alter, so the
// gate forms alter - base) and the running base it is measured against.
uniform highp sampler2D diffPacked;
uniform highp sampler2D basePacked;
// Per-cell sub-pixel motion correction (Lucas-Kanade against the running
// base, packed texels) and its strength.
uniform highp sampler2D srRefMap;
uniform float srRefine;
// 1 = the alignment texture is FlowNet's dense low-res flow, not the cell
// atlas. See the motion block in main().
uniform int srFlowAlign;
// Per-frame atlas tile offset, alignment grid size, rawSize/2: identical
// values and convention to the mergeAlign call for this frame.
uniform ivec2 srShift;
uniform ivec2 srAlignSize;
uniform ivec2 srRawHalf;
// Packing shift (cfaShift): packed coord = (cropRaw + cfaShift) / 2.
uniform ivec2 srCfa;
// Full-frame raw px per output px (uniform factor in practice).
uniform vec2 srFullPerOut;
// Crop origin in full-frame raw px ((0,0) when uncropped).
uniform vec2 srOrigin;
// Per-frame exposure normalize (same value mergeAlign uses for this frame).
uniform float srExpose;
// Base frame's exposure normalize: fallback samples are rescaled by
// srExpose/srBaseExpose into the current frame's domain.
uniform float srBaseExpose;
// 1 = base frame: motion 0 (its atlas slot is meaningless; mergeAlign never
// runs for it, so the atlas must not be sampled), estimate uninitialized.
uniform float srZeroMotion;
uniform int srFirst;
// Fusion trust: minimum weight for a frame that disagrees coherently with the
// running estimate, and the low-frequency residual (normalized units) at
// which the weight halves; the band is widened by four sigma of the
// pre-inflation noise model so the merge denoise setting cannot modulate it.
uniform float srTrustFloor;
uniform float srTrustBand;
uniform float srNoiseS0;
uniform float srNoiseO0;
// Weight attenuation for samples at the raw ceiling: burst frames clip at
// different levels and a clipped sample carries no highlight detail.
uniform float srClipAtten;
// Bound on motion magnitude in packed px (same guard as the RGB drizzle).
uniform float srMotionMax;
#define SR_TILE 2
#define SR_TILE_AL 16

float pick4(vec4 v, int ch) {
    return ch == 0 ? v.x : (ch == 1 ? v.y : (ch == 2 ? v.z : v.w));
}

// Catmull-Rom-ish hardware bicubic (four linear taps), the same kernel as
// merge/srscatter. Benchmarked against the raw: with the bilinear gather the
// drizzled output carried only 0.68 of the native raw's fine band, because
// bilinear's response at the site lattice's band edge is ~0.4.
vec4 srCubicW(float x) {
    float x2 = x * x;
    float x3 = x2 * x;
    return vec4(-x3 + 3.0 * x2 - 3.0 * x + 1.0,
                 3.0 * x3 - 6.0 * x2 + 4.0,
                -3.0 * x3 + 3.0 * x2 + 3.0 * x + 1.0,
                 x3) / 6.0;
}

vec4 srBicubic(sampler2D s, vec2 uv) {
    vec2 texSize = vec2(textureSize(s, 0));
    vec2 tc = uv * texSize - 0.5;
    vec2 f = fract(tc);
    tc -= f;
    vec4 xc = srCubicW(f.x);
    vec4 yc = srCubicW(f.y);
    vec4 c = tc.xxyy + vec2(-0.5, 1.5).xyxy;
    vec4 sz = vec4(xc.xz + xc.yw, yc.xz + yc.yw);
    vec4 off = (c + vec4(xc.yw, yc.yw) / sz) / texSize.xxyy;
    vec4 s0 = texture(s, off.xz);
    vec4 s1 = texture(s, off.yz);
    vec4 s2 = texture(s, off.xw);
    vec4 s3 = texture(s, off.yw);
    float sx = sz.x / (sz.x + sz.y);
    float sy = sz.z / (sz.z + sz.w);
    vec4 res = mix(mix(s3, s2, sx), mix(s1, s0, sx), sy);
    vec4 bil = texture(s, uv);
    // High-contrast blend: Catmull-Rom's negative lobes overshoot at strong
    // transitions, and the resolve injects this band straight into the output
    // (bench: ~13 levels of dark undershoot around a bright pot that the
    // aniso's edge-preserving reconstruction does not have). A hard clamp
    // bounds per output pixel and generates banding, so instead blend toward
    // the bilinear - a convex combination of the samples, which cannot
    // overshoot - weighted smoothly by the taps' local range. Texture keeps
    // the bicubic; only strong transitions get the non-ringing form.
    vec4 lo = min(min(s0, s1), min(s2, s3));
    vec4 hi = max(max(s0, s1), max(s2, s3));
    vec4 e = smoothstep(vec4(0.10), vec4(0.30), hi - lo);
    return mix(bil, res, vec4(1.0) - e);
}

// Decoded motion (packed px) of one alignment cell, atlas-offset by srShift.
vec2 srCellMotion(ivec2 cell) {
    ivec2 am = textureSize(alignmentTexture, 0) - ivec2(1);
    vec4 a = texelFetch(alignmentTexture, clamp(cell + srShift, ivec2(0), am), 0);
    return a.xy * vec2(srRawHalf) + a.zw;
}

void main() {
    ivec2 o = ivec2(gl_GlobalInvocationID.xy);
    ivec2 outSize = imageSize(srBayOut);
    if (o.x >= outSize.x || o.y >= outSize.y) return;
    // Output-site CFA channel tagged by the OUTPUT parity (merge2o's rule
    // applied to the output grid).
    int ch = ((o.x + srCfa.x) & 1) + ((o.y + srCfa.y) & 1) * 2;
    // Full-frame raw span position of this site, then crop-relative.
    vec2 full = (vec2(o) + vec2(0.5)) * srFullPerOut;
    vec2 rel = full - srOrigin;
    // Continuous packed (texel index) coordinate of this site: channel c of
    // texel (i,j) holds raw site 2*(i,j) - cfaShift + (c&1, c>>1), and raw
    // span -> texel index drops the half-texel sample center.
    vec2 p = (rel + vec2(srCfa)) * 0.5 - vec2(0.25);
    // Local motion: bilinear interpolation of the atlas over the 2x2
    // neighbouring alignment cells (nearest-cell sampling left a
    // piecewise-constant field whose steps read as blocky local
    // misregistration). UNFLOORED: merge floors for denoise, the drizzle
    // keeps the sub-pixel residuals that super-resolution depends on.
    vec2 m = vec2(0.0);
    if (srZeroMotion < 0.5) {
        vec2 A = p / (float(SR_TILE_AL) / float(SR_TILE));
        if (srFlowAlign == 1) {
            // FlowNet: dense low-res flow instead of the cell atlas (see
            // srscatter; same stretch bake, same rawHalf-px units), bilinear
            // at this packed position keeps the continuous sub-pixel motion.
            vec2 flowUv = clamp((p + vec2(0.5)) / vec2(srRawHalf), vec2(0.0), vec2(1.0));
            m = texture(alignmentTexture, flowUv).xy;
        } else {
            ivec2 c0 = ivec2(floor(A));
            vec2 f = A - vec2(c0);
            ivec2 cmax = srAlignSize - ivec2(1);
            vec2 m00 = srCellMotion(clamp(c0, ivec2(0), cmax));
            vec2 m10 = srCellMotion(clamp(c0 + ivec2(1, 0), ivec2(0), cmax));
            vec2 m01 = srCellMotion(clamp(c0 + ivec2(0, 1), ivec2(0), cmax));
            vec2 m11 = srCellMotion(clamp(c0 + ivec2(1, 1), ivec2(0), cmax));
            m = mix(mix(m00, m10, f.x), mix(m01, m11, f.x), f.y);
        }
        m = clamp(m, vec2(-srMotionMax), vec2(srMotionMax));
        // Sub-pixel alignment refinement (bilinear over the same cell grid),
        // gated by the atlas motion: on a static burst the LK residual is
        // noise and applying it warps the lattice randomly (see merge/srscatter).
        m += srRefine * texture(srRefMap, clamp(
                (A + vec2(0.5)) / vec2(srAlignSize),
                vec2(0.0), vec2(1.0))).xy * clamp(2.0 * length(m), 0.0, 1.0);
    }
    // Point drizzle: deposit the nearest sample of this site's CFA channel at
    // its own lattice phase. A raw output wants exactly this - the mosaic's
    // aliasing is the raw data, and the converter's demosaic handles it; the
    // burst's sub-pixel diversity is what fills the finer output grid.
    vec2 phase = vec2(float(ch & 1), float(ch >> 1)) * 0.5;
    vec2 ps = vec2(textureSize(alterPacked, 0));
    // Sharpened-bicubic gather of the site's own channel: a proper resampling
    // of the raw lattice (a point deposit would hold each site across its
    // footprint, i.e. leave a mosaic-scale grid in the raw), sharp enough to
    // carry the site band's upper octave.
    // Plain bicubic (see merge/srscatter): the unsharp term imprinted ringing on
    // real data instead of detail.
    vec2 suv = clamp((p + m - phase + vec2(0.5)) / ps, vec2(0.0), vec2(1.0));
    float v = pick4(srBicubic(alterPacked, suv), ch);
    // Clamp runaway exposure normalization (insane layerMpy metadata would
    // otherwise blow finite samples to Inf downstream of every guard).
    float ex = min(srExpose, 32.0);
    if (isnan(v) || isinf(v)) {
        // Sparse poison (hot pixels, highlight arithmetic): substitute the
        // exposure-matched base frame sample at the same site. Still
        // non-finite (poisoned base) degrades to zero, which the trust gate
        // absorbs as a bounded pull instead of poison.
        vec4 bq = texture(basePacked, clamp((p - phase + vec2(0.5)) / ps, vec2(0.0), vec2(1.0)));
        float bv = pick4(bq, ch);
        float be = bv * (ex / max(srBaseExpose, 1e-6));
        v = (isnan(be) || isinf(be)) ? 0.0 : be;
    }
    vec2 acc = unpackHalf2x16(imageLoad(srBayIn, o).x);
    float nv;
    float wgt;
    float ve = v * ex;
    // Clipping: a sample at the raw ceiling carries no highlight detail, and
    // burst frames clip at different levels, so a clipped frame drags
    // highlight edges around. A uniformly clipped site is unaffected: the
    // weights cancel in the average.
    float clipAtten = 1.0 - srClipAtten * smoothstep(0.9, 1.0, clamp(v, 0.0, 1.0));
    if (srFirst != 0) {
        // Seed: non-finite base samples become black; later frames pull the
        // estimate toward real values through the trust gate below.
        nv = (isnan(ve) || isinf(ve)) ? 0.0 : ve;
        wgt = clipAtten;
    } else if (isnan(ve) || isinf(ve)) {
        // No information in this sample: keep the running estimate and its
        // accumulated weight untouched instead of poisoning them.
        nv = acc.x;
        wgt = acc.y;
    } else {
        // Weighted running mean; .y carries the accumulated weight.
        // Fusion trust: judge only *coherent* disagreement - a 3x3
        // packed-window signed mean of the true residual (diffPacked holds
        // the aligned alter, basePacked the reference) cancels the dipoles
        // sub-pixel sampling leaves at edges, while a misregistered frame or
        // a moving object still reads as a large coherent mean; the mean
        // magnitude keeps gross outliers from voting.
        float ref = acc.x;
        float W = max(acc.y, 1e-4);
        ivec2 dmax = textureSize(diffPacked, 0) - ivec2(1);
        ivec2 ip = ivec2(floor(p + vec2(0.5)));
        float rSum = 0.0;
        float rAbs = 0.0;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                ivec2 tp = clamp(ip + ivec2(i, j), ivec2(0), dmax);
                float r = pick4(texelFetch(diffPacked, tp, 0), ch)
                        - pick4(texelFetch(basePacked, tp, 0), ch);
                rSum += r;
                rAbs += abs(r);
            }
        }
        float rMean = rSum * (1.0 / 9.0);
        float rMag = rAbs * (1.0 / 9.0);
        float lum = max(pick4(texelFetch(diffPacked, clamp(ip, ivec2(0), dmax), 0), ch), 1e-4);
        float var0 = max(srNoiseS0 * lum + srNoiseO0, 1e-12);
        float band = max(srTrustBand, 4.0 * sqrt(var0));
        float lf = abs(rMean);
        float mag = rMag;
        float w = max(1.0 / (1.0 + (lf / band) * (lf / band)), srTrustFloor)
                / (1.0 + pow(mag / (16.0 * band), 4.0));
        w *= clipAtten;
        nv = (ref * W + ve * w) / (W + w);
        wgt = W + w;
    }
    imageStore(srBayOut, o, uvec4(packHalf2x16(vec2(nv, wgt))));
}
