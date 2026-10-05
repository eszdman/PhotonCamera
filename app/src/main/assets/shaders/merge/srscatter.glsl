#extension GL_OES_shader_image_atomic : require

#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp uimage2D;
// This frame's per-site cross-channel luma (.r luma, .g own site value).
uniform highp sampler2D srLumaTex;
// This frame's aligned residual against the running base and the base itself
// (trust), the alignment field, and the sub-pixel refinement map.
uniform highp sampler2D diffPacked;
uniform highp sampler2D basePacked;
uniform highp sampler2D alignmentTexture;
uniform highp sampler2D srRefMap;
uniform float srRefine;
uniform int srFlowAlign;
uniform ivec2 srShift;
uniform ivec2 srAlignSize;
uniform ivec2 srRawHalf;
uniform ivec2 srCfa;
// Full-frame raw px per output px per axis, and the crop origin.
uniform vec2 srFullPerOut;
uniform vec2 srOrigin;
uniform float srExpose;
uniform float srZeroMotion;
uniform float srJitter;
uniform int srFrame;
uniform float srMotionMax;
uniform float srTrustFloor;
uniform float srTrustBand;
uniform float srNoiseS0;
uniform float srNoiseO0;
uniform float srClipAtten;
// Deposit accumulators (16.16 fixed point, in-place atomic accumulation):
// srDepV = sum(w * luma), srDepW = sum(w). Every raw site of every frame is
// deposited at its own warped output position - a true scatter, so the union
// of the burst's sub-pixel sampling lattice survives (a per-frame
// interpolation would band-limit each frame back to the raw lattice and
// destroy exactly the above-Nyquist content super-resolution recovers).
layout(r32ui, binding = 0) coherent uniform highp uimage2D srDepV;
layout(r32ui, binding = 1) coherent uniform highp uimage2D srDepW;
#define SR_FIXED 65536.0
#define SR_TILE 2
#define SR_TILE_AL 16
#define SR_PHI2 1.324717957244746

vec2 srCellMotion(ivec2 cell) {
    ivec2 am = textureSize(alignmentTexture, 0) - ivec2(1);
    vec4 a = texelFetch(alignmentTexture, clamp(cell + srShift, ivec2(0), am), 0);
    return a.xy * vec2(srRawHalf) + a.zw;
}

void main() {
    ivec2 site = ivec2(gl_GlobalInvocationID.xy);
    ivec2 lsize = textureSize(srLumaTex, 0);
    if (site.x >= lsize.x || site.y >= lsize.y) return;
    // Packed coordinate of this site, for the motion and trust lookups.
    vec2 p = (vec2(site) + vec2(srCfa)) * 0.5 - vec2(0.25);
    vec2 m = vec2(0.0);
    vec4 refv = vec4(0.0);
    float w = 1.0;
    // Per-cell photometric scale fitted by the refinement against the running
    // base (see merge/srrefine): a local brightness mismatch is not geometry,
    // and left uncorrected it inflates the residual the trust gate sees.
    float photoGain = 1.0;
    if (srZeroMotion < 0.5) {
        vec2 A = p / (float(SR_TILE_AL) / float(SR_TILE));
        if (srFlowAlign == 1) {
            // FlowNet: dense low-res flow, bilinearly sampled at this site.
            vec2 flowUv = clamp((p + vec2(0.5)) / vec2(srRawHalf), vec2(0.0), vec2(1.0));
            m = texture(alignmentTexture, flowUv).xy;
        } else {
            // Atlas: bilinear interpolation of the per-cell motion.
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
        // Sub-pixel refinement, gated by actual atlas motion (a static burst's
        // LK fit is noise and must not warp the sampling lattice).
        refv = texture(srRefMap, clamp(
                (A + vec2(0.5)) / vec2(srAlignSize), vec2(0.0), vec2(1.0)));
        // .w is only meaningful when the refinement actually ran (otherwise
        // srRefMap falls back to the packed base).
        photoGain = srRefine > 0.5 ? clamp(refv.w, 0.9, 1.1) : 1.0;
        float corrLoc = 0.0;
        {
            vec2 ruv = clamp((A + vec2(0.5)) / vec2(srAlignSize), vec2(0.0), vec2(1.0));
            vec2 rstep = vec2(1.0) / vec2(textureSize(srRefMap, 0));
            vec2 nbr = 0.25 * (
                    texture(srRefMap, clamp(ruv + vec2(rstep.x, 0.0), vec2(0.0), vec2(1.0))).xy
                    + texture(srRefMap, clamp(ruv - vec2(rstep.x, 0.0), vec2(0.0), vec2(1.0))).xy
                    + texture(srRefMap, clamp(ruv + vec2(0.0, rstep.y), vec2(0.0), vec2(1.0))).xy
                    + texture(srRefMap, clamp(ruv - vec2(0.0, rstep.y), vec2(0.0), vec2(1.0))).xy);
            corrLoc = length(refv.xy - nbr);
        }
        m += srRefine * refv.xy * clamp(2.0 * length(m), 0.0, 1.0);
        // Fusion trust: a 3x3 signed mean of the true residual (aligned alter
        // minus the running base) cancels the dipoles sub-pixel sampling
        // leaves at edges, while misregistration or a moving object reads as
        // a large coherent mean. A correction beyond half a raw pixel that
        // also diverges from its neighbours is a moving subject warped into
        // the base, judged by the pre-refine residual instead.
        ivec2 dmax = textureSize(diffPacked, 0) - ivec2(1);
        ivec2 ip = ivec2(floor(p + vec2(0.5)));
        vec3 rSum = vec3(0.0);
        vec3 rAbs = vec3(0.0);
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                ivec2 tp = clamp(ip + ivec2(i, j), ivec2(0), dmax);
                vec3 r = texelFetch(diffPacked, tp, 0).rgb - texelFetch(basePacked, tp, 0).rgb;
                rSum += r;
                rAbs += abs(r);
            }
        }
        vec3 rMean = rSum * (1.0 / 9.0);
        vec3 rMag = rAbs * (1.0 / 9.0);
        float lum = max(dot(texelFetch(diffPacked, clamp(ip, ivec2(0), dmax), 0).rgb,
                vec3(1.0 / 3.0)), 1e-4);
        float var0 = max(srNoiseS0 * lum + srNoiseO0, 1e-12);
        float band = max(srTrustBand, 4.0 * sqrt(var0));
        float preRes = dot(abs(rMean), vec3(1.0 / 3.0));
        float motionness = step(0.25, length(refv.xy)) * smoothstep(0.1, 0.3, corrLoc);
        float lf = srRefine > 0.5 ? mix(refv.z, preRes, motionness) : preRes;
        float mag = dot(rMag, vec3(1.0 / 3.0));
        w = max(1.0 / (1.0 + (lf / band) * (lf / band)), srTrustFloor)
                / (1.0 + pow(mag / (16.0 * band), 4.0));
    }
    // Synthesized frame-global jitter for static bursts: none where the frame
    // already moved (the real dither is there), full where it is static
    // relative to the base.
    vec2 jit = vec2(0.0);
    if (srJitter > 0.0) {
        vec2 g = fract(vec2(float(srFrame)) / vec2(SR_PHI2 * SR_PHI2, SR_PHI2)) - vec2(0.5);
        jit = g * (srJitter * clamp(1.0 - 2.0 * length(2.0 * m), 0.0, 1.0));
    }
    vec4 l = texelFetch(srLumaTex, site, 0);
    float ex = min(srExpose, 32.0);
    float v = l.r * ex * photoGain;
    // Clipping: a sample at the raw ceiling carries no highlight detail, and
    // burst frames clip at different levels, so a clipped sample must not
    // vote as strongly (a uniformly clipped site is unaffected).
    w *= 1.0 - srClipAtten * smoothstep(0.9, 1.0, clamp(l.g, 0.0, 1.0));
    // The gather at output `rel` reads alter content at `rel + 2m + jitter`;
    // inverting that, this alter site belongs at `site - 2m - jitter`.
    vec2 relOut = vec2(site) - 2.0 * m - jit;
    vec2 full = relOut + srOrigin;
    vec2 oc = full / srFullPerOut - vec2(0.5);
    // 16.16 fixed-point words. Luma is clamped to a sane range so the sum
    // cannot overflow the 32-bit accumulator (16 bits of fraction, 40 frames
    // of ~8.0 max).
    float wfix = clamp(w, 0.0, 1.0);
    float vfix = clamp(v, 0.0, 8.0);
    uint cv = uint(clamp(vfix * wfix * SR_FIXED + 0.5, 0.0, 4.0e9));
    uint cw = uint(clamp(wfix * SR_FIXED + 0.5, 0.0, 4.0e9));
    // Bilinear splat: a deposit kernel of ~1 raw px at any expansion, the
    // narrowest sampled kernel the sensor's pixel aperture supports. The
    // recovery pass deconvolves its known response.
    ivec2 dsz = imageSize(srDepV) - ivec2(1);
    ivec2 o0 = ivec2(floor(oc));
    vec2 f = oc - vec2(o0);
    for (int j = 0; j <= 1; j++) {
        for (int i = 0; i <= 1; i++) {
            float bw = (i == 0 ? 1.0 - f.x : f.x) * (j == 0 ? 1.0 - f.y : f.y);
            if (bw <= 0.0) continue;
            ivec2 t = clamp(o0 + ivec2(i, j), ivec2(0), dsz);
            imageAtomicAdd(srDepV, t, uint(bw * float(cv) + 0.5));
            imageAtomicAdd(srDepW, t, uint(bw * float(cw) + 0.5));
        }
    }
}
