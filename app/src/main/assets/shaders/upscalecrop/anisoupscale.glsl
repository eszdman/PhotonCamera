precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D KernelsMap;
uniform ivec2 fullSize;
uniform float sigmaScale;
uniform vec2 sigmaMinPx;
uniform float sigmaMaxPx;
uniform float strength;
uniform float sharpAmt;
uniform float sharpWide;
uniform float maxElong;
uniform vec2 scaleRatio;
uniform int kernelRadius;
uniform int debugMode;
out vec4 Output;
#import interpolation

// Largest supported reconstruction window; the active taps are gated by
// kernelRadius at runtime so the tunable never forces a shader recompile.
#define KERN_R_MAX 5

// Precision-matrix coefficients (a = dy^2, b = dx*dy, c = dx^2) built from the
// KernelNet (s1, s2, rho) params converted into crop-pixel units. The map is
// emitted at half crop resolution and sampled with the output UV, so map
// sigmas are rescaled by the crop/map texel ratio (per axis, queried in
// shader so no extra uniform is needed); sigmaScale trims on top of that.
// The params come from the denoise-trained merge model: rho is clamped hard
// and det floored so near-extreme edge kernels cannot degenerate into
// knife-thin ridges, and the sigma elongation is capped via maxElong.
vec3 anisoCoeffs(vec2 uvPos, out vec2 sigmas) {
    vec4 p = texture(KernelsMap, uvPos);
    float s1 = max(p.x, 1e-4);
    float s2 = max(p.y, 1e-4);
    // Denoise-trained map: rho clamped to +-0.8 (det = 0.36, still far
    // above the floor; 0.9 leaves det = 0.19 and the kernel degenerates
    // into a ridge), det floored at 1e-2.
    float rho = clamp(p.z, -0.8, 0.8);
    vec2 mapSize = vec2(textureSize(KernelsMap, 0));
    vec2 cropSz = vec2(textureSize(InputBuffer, 0));
    s1 *= (cropSz.x / max(mapSize.x, 1.0)) * sigmaScale;
    s2 *= (cropSz.y / max(mapSize.y, 1.0)) * sigmaScale;
    s1 = clamp(s1, sigmaMinPx.x, sigmaMaxPx);
    s2 = clamp(s2, sigmaMinPx.y, sigmaMaxPx);
    if (s1 > s2 * maxElong) {
        s1 = s2 * maxElong;
    } else if (s2 > s1 * maxElong) {
        s2 = s1 * maxElong;
    }
    float det = max(1.0 - rho * rho, 1e-2);
    sigmas = vec2(s1, s2);
    return vec3(
        1.0 / (s1 * s1 * det),    // a: dy^2 coefficient
        -rho / (s1 * s2 * det),   // b: dx*dy coefficient
        1.0 / (s2 * s2 * det));   // c: dx^2 coefficient
}

// Locally anisotropic Gaussian reconstruction taps sit on integer crop pixels (ivec2
// base + integer offsets, sampled with the regular texture path) and each
// weight is evaluated at the EXACT fractional offset (tap - sample), so thin
// edge kernels transfer weight smoothly between taps. The second accumulator
// uses the same kernel stretched by sharpWide - its difference with the
// narrow one is the edge-aligned bandpass used by the unsharp term. Both are
// convex-combination normalized.
void accumulatePair(vec2 inPos, vec3 abc, vec3 abcWide, int radius, out vec4 accN, out vec4 accW) {
    ivec2 fl = ivec2(floor(inPos));
    vec2 fr = inPos - vec2(fl);
    vec4 an = vec4(0.0);
    vec4 aw = vec4(0.0);
    float zn = 0.0;
    float zw = 0.0;
    ivec2 cropSize = textureSize(InputBuffer, 0);
    for (int i = -KERN_R_MAX; i <= KERN_R_MAX; i++) {
        for (int j = -KERN_R_MAX; j <= KERN_R_MAX; j++) {
            if (abs(i) > radius || abs(j) > radius) continue;
            vec2 d = vec2(float(i), float(j)) - fr;
            float qn = abc.z * d.x * d.x + 2.0 * abc.y * d.x * d.y + abc.x * d.y * d.y;
            float qw = abcWide.z * d.x * d.x + 2.0 * abcWide.y * d.x * d.y + abcWide.x * d.y * d.y;
            //float win = tapWindow(i, j, radius);
            //float wn = exp(-qn) * win;
            //float ww = exp(-qw) * win;
            float wn = exp(-qn);
            float ww = exp(-qw);
            ivec2 tap = clamp(fl + ivec2(i, j), ivec2(0), cropSize - ivec2(1));
            vec4 s = texelFetch(InputBuffer, tap, 0);
            an += s * wn;
            aw += s * ww;
            zn += wn;
            zw += ww;
        }
    }
    accN = vec4(an.rgb, zn);
    accW = vec4(aw.rgb, zw);
}

void main() {
    // Spatial precision correct inPos creation
    vec2 inPos = gl_FragCoord.xy / scaleRatio;
    // Spatial precision correct UV creation
    vec2 uv = gl_FragCoord.xy / vec2(fullSize);
    if (debugMode == 1) {
        // Plain bilinear upscale of the input baseline.
        Output = texture(InputBuffer, uv);
        return;
    }
    vec2 sigmas;
    vec3 abc = anisoCoeffs(uv, sigmas);
    // Edge-confidence gate for the unsharp term: the used fraction of the
    // allowed elongation. Near-isotropic kernels (flats, smooth gradients)
    // get ~0 sharpening so noise and banding stay buried, while real edges
    // (elongation capped at maxElong) get the full sharpAmt.
    float elong = max(sigmas.x, sigmas.y) / max(min(sigmas.x, sigmas.y), 1e-4);
    float gate = clamp((elong - 1.0) / max(maxElong - 1.0, 1e-4), 0.0, 1.0);
    float sharpWideEff = min(sharpWide, (0.5 * float(kernelRadius)) / sigmaMaxPx);
    vec3 abcWide = abc / (sharpWideEff * sharpWideEff);
    vec4 accN;
    vec4 accW;
    accumulatePair(inPos, abc, abcWide, kernelRadius, accN, accW);
    vec4 aniso = accN.a > 1e-5
        ? accN / accN.a
        : texture(InputBuffer, uv);
    vec4 sharp = aniso;
    if (sharpAmt > 0.0 && gate > 0.0) {
        vec4 wide = accW.a > 1e-5 ? accW / accW.a : aniso;
        sharp = aniso + (sharpAmt * gate) * (aniso - wide);
    }
    vec4 bic = textureBicubicHardware(InputBuffer, uv);
    Output = mix(bic, sharp, clamp(strength, 0.0, 1.0));
}
