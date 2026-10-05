
#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
// Aligned alter and running base on the same packed grid. The residual is
// their difference: mergeAlign's output is the alter, not the difference.
uniform highp sampler2D diffPacked;
uniform highp sampler2D basePacked;
// Refinement cell size in packed texels (finer than the alignment atlas).
uniform int srRefCell;
// Per refinement cell: .xy = the sub-pixel motion correction that best explains
// the residual as a translation (packed texels), .z = the residual RMS left
// after applying it, .w = the photometric scale that best matches the frame
// to the running base (applied at deposit time, srscatter).
layout(rgba16f, binding = 0) writeonly uniform highp image2D srRefOut;

float pick4(vec4 v, int ch) {
    return ch == 0 ? v.x : (ch == 1 ? v.y : (ch == 2 ? v.z : v.w));
}

// Phase-stable same-channel sample: the 2x2 packed-texel block is four sites
// of the same colour, two packed texels apart in the channel's own plane.
// The running base de-aliases as frames accumulate, so at the pixel scale it
// diverges from any single frame - a residual measured there grows with the
// frame count, the trust gate then rejects the frames whose detail the
// fusion is supposed to add, and the more frames, the more complete that
// rejection is (the count inversion: 8 frames ahead of the non-SR render,
// 38 behind). Structure - misregistration, deformation, motion - survives
// this average; the sub-pixel phase pattern does not.
float psamp(sampler2D tex, ivec2 q, int c, ivec2 dmax) {
    return 0.25 * (pick4(texelFetch(tex, clamp(q, ivec2(0), dmax), 0), c)
            + pick4(texelFetch(tex, clamp(q + ivec2(2, 0), ivec2(0), dmax), 0), c)
            + pick4(texelFetch(tex, clamp(q + ivec2(0, 2), ivec2(0), dmax), 0), c)
            + pick4(texelFetch(tex, clamp(q + ivec2(2, 2), ivec2(0), dmax), 0), c));
}

void main() {
    ivec2 cell = ivec2(gl_GlobalInvocationID.xy);
    ivec2 outSize = imageSize(srRefOut);
    if (cell.x >= outSize.x || cell.y >= outSize.y) return;
    ivec2 dmax = textureSize(diffPacked, 0) - ivec2(1);
    ivec2 c0 = cell * srRefCell;
    float m00 = 0.0;
    float m01 = 0.0;
    float m11 = 0.0;
    float vx = 0.0;
    float vy = 0.0;
    float rr = 0.0;
    // Photometric fit accumulators: a local brightness mismatch (OIS
    // vignette wobble, flicker, sensor nonlinearity) is not geometry, but a
    // translation-only fit reads it as unexplained residual and the trust
    // gate then rejects a frame whose detail is needed. The scale is fit
    // against the running base and folded into the translation solve below.
    float sBB = 0.0;
    float sBA = 0.0;
    float sBR = 0.0;
    float gxB = 0.0;
    float gyB = 0.0;
    // Subsample the cell: the motion field is smooth across it and every
    // second texel still gives 16 samples per channel. Gradients are central
    // differences of the same CFA channel (its neighbours sit one packed
    // texel away, i.e. 2 raw px), so g is per packed texel.
    // Fixed 4x4 sample pattern per channel, independent of the cell size, so a
    // finer grid keeps the same sample count and only trades cell area.
    int step = max(1, srRefCell / 4);
    for (int j = 0; j < srRefCell; j += step) {
        for (int i = 0; i < srRefCell; i += step) {
            ivec2 q = clamp(c0 + ivec2(i, j), ivec2(0), dmax);
            for (int c = 0; c < 4; c++) {
                float b = psamp(basePacked, q, c, dmax);
                float a = psamp(diffPacked, q, c, dmax);
                float gx = 0.5 * (psamp(basePacked, q + ivec2(1, 0), c, dmax)
                        - psamp(basePacked, q - ivec2(1, 0), c, dmax));
                float gy = 0.5 * (psamp(basePacked, q + ivec2(0, 1), c, dmax)
                        - psamp(basePacked, q - ivec2(0, 1), c, dmax));
                float r = a - b;
                m00 += gx * gx;
                m01 += gx * gy;
                m11 += gy * gy;
                vx += gx * r;
                vy += gy * r;
                rr += r * r;
                sBB += b * b;
                sBA += b * a;
                sBR += b * r;
                gxB += gx * b;
                gyB += gy * b;
            }
        }
    }
    // Photometric scale (bounded: gross mismatches are occlusions/motion, not
    // exposure), folded into the translation normal equations as
    // r' = r + (1 - gain) * base, so the residual that the confidence and the
    // trust gate see is the one left after both corrections.
    float gain = clamp(sBA / max(sBB, 1e-9), 0.9, 1.1);
    float og = 1.0 - gain;
    vx += og * gxB;
    vy += og * gyB;
    rr += og * og * sBB + 2.0 * og * sBR;
    vec2 delta = vec2(0.0);
    float det = m00 * m11 - m01 * m01;
    // Only correct when the gradients constrain both axes (a 1D edge leaves
    // the along-edge component unobservable) and when the residual is at
    // least partly explained by a translation; the explained fraction is the
    // confidence, so noise, aliasing and deformation are not "corrected".
    if (det > 0.1 * max(m00 * m11, 1e-12) && m00 + m11 > 1e-4) {
        float invDet = 1.0 / det;
        vec2 d = vec2(-(m11 * vx - m01 * vy) * invDet,
                -(m00 * vy - m01 * vx) * invDet);
        // d = -M^-1 v minimizes sum((r + d.g)^2), so the energy it explains is
        // -(v.d) (positive), and the explained FRACTION is its R^2. The
        // positive form (v.d) is the negative of that and clamps to 0, which
        // silently zeroed every correction (the refinement was a no-op and
        // the drizzle was stuck at the coarse atlas positions).
        float conf = clamp(-(vx * d.x + vy * d.y) / max(rr, 1e-12), 0.0, 1.0);
        // Squared confidence: noise-driven fits sit around 0.2-0.4 (their
        // "explained" fraction is mostly the fit's own freedom) while real
        // motion sits at 0.8+. Squaring suppresses the former sharply
        // (0.04-0.16) and keeps the latter (0.64+) - benched: the linear form
        // left noise warping the sampling lattice on static bursts.
        // Capture range scales with confidence: the atlas can leave up to
        // ~1 raw px of within-cell residual (rotation, rolling shutter) and
        // the old flat 0.5 packed px (+-0.25 raw px) could not reach it; a
        // confident fit is allowed up to 1.0 packed px while an uncertain one
        // keeps the noise-safe 0.5.
        float lim = mix(0.5, 1.0, smoothstep(0.5, 0.8, conf));
        delta = clamp(d, vec2(-lim), vec2(lim)) * conf * conf;
    }
    // .z = residual RMS left after the correction: what the translation model
    // cannot explain (deformation, moving subjects). The drizzle's trust gate
    // uses it in place of the pre-refinement residual, so a frame that is
    // merely misregistered - which the correction fixes - is not rejected.
    float ns = float((srRefCell / step) * (srRefCell / step) * 4);
    // Energy left after applying the (clamped, confidence-scaled) correction:
    // rr minus the explained part. The explained part is -(v.delta) (positive),
    // so it SUBTRACTS from rr; the previous "+ rr - (v.delta)" added it and
    // overstated the residual.
    float unexplained = max(rr + (vx * delta.x + vy * delta.y), 0.0);
    float resid = sqrt(unexplained / max(ns, 1.0));
    imageStore(srRefOut, cell, vec4(delta, resid, gain));
}
