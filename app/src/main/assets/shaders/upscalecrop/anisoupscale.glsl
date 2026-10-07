precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D KernelsMap;
uniform ivec2 fullSize;
// Tiled rendering origin (output coords of this tile's row 0). (0,0) on the
// legacy path: identical.
uniform ivec2 u_tileOrigin;
// Tiled input-window origin (input coords of the bound window's row 0) and
// the full input size for filter-UV remap and sigma rescaling. Zeros and full
// size on the legacy path: identical.
uniform ivec2 u_winOrigin;
uniform vec2 u_winFullSize;
uniform float sigmaScale;
uniform vec2 sigmaMinPx;
uniform float sigmaMaxPx;
uniform float strength;
uniform float sharpAmt;
uniform float sharpWide;
// Soft cap on the acutance term as a fraction of the local level (Weber-like):
// see the halo-suppression comment at the term itself.
uniform float acutRel;
// Floor on the acutance gate. The gate below is elongation-based, so flats and
// isotropic texture (foliage, concrete) get none of the unsharp and their
// below-Nyquist sharpness is whatever the convex reconstruction kernel passes:
// the bench (settle check) measures that at 0.62-0.90 of the genuine band and
// *saturating* as the kernel sharpens, i.e. the SR reads softer than Disabled
// at equal coverage in exactly those areas. A small floor lets the same
// Weber-capped, zero-mean acutance deconvolve texture too. This is real detail:
// the base is a convex mix and the added term is bounded (tanh) and zero-mean,
// so it cannot alias - and the bench confirms it (genuine delivery 0.81 -> 0.99,
// corr with the folded image unchanged at 0.09-0.10, full-image rms improves).
// Bench: a non-zero floor amplifies the kernel-map grid and noise in flats
// and highlights (flat/highlight check: grid +29%, highlight structure +450%,
// speck p99.9 +26% at 0.20) - the "tiny color specks / small highlight grids"
// signature. It is no longer needed for texture: the recovered band's lower
// edge now extends below the raw Nyquist (SR_F_RAW_NYQ 0.35), which delivers
// the near-Nyquist texture the floor was compensating for (settle genuine
// delivery is already >= 1.0 at floor 0). Default 0 = edge-only acutance.
#ifndef SR_ACUT_FLAT
#define SR_ACUT_FLAT 0.0
#endif
uniform float maxElong;
uniform float gateExp;
uniform vec2 scaleRatio;
uniform int splitChroma;
uniform int kernelRadius;
uniform int debugMode;
// Major-axis extension factor: 1 = both axes clamp at sigmaMaxPx (legacy);
// >1 on the SR output-grid path lets the KernelNet elongation survive the cap.
uniform float srElongCap;
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
    // Single tap, deliberately: smoothing this field (a 3x3 tent over the
    // quarter-resolution map) bleeds the edge kernels' short-axis sigma up
    // toward the neighbours' isotropic values and measurably softens every
    // band. The mesh beat it removed is handled in the SR path instead, by
    // replacing the aniso's upper band with the fused luma's.
    vec4 p = texture(KernelsMap, uvPos);
    float s1 = max(p.x, 1e-4);
    float s2 = max(p.y, 1e-4);
    // Denoise-trained map: rho clamped to +-0.8 (det = 0.36, still far
    // above the floor; 0.9 leaves det = 0.19 and the kernel degenerates
    // into a ridge), det floored at 1e-2.
    float rho = clamp(p.z, -0.8, 0.8);
    vec2 mapSize = vec2(textureSize(KernelsMap, 0));
    // Full crop size (not the bound window) keeps map-texel to crop-pixel
    // rescaling identical with windowed inputs; equal to textureSize on the
    // legacy path.
    vec2 cropSz = u_winFullSize;
    s1 *= (cropSz.x / max(mapSize.x, 1.0)) * sigmaScale;
    s2 *= (cropSz.y / max(mapSize.y, 1.0)) * sigmaScale;
    // Minor axis: clamp to the sigma cap (sharpness-critical). Major axis: on
    // the SR output-grid path srElongCap > 1 lets it extend past the cap so
    // the KernelNet elongation survives - otherwise both axes pin at the cap,
    // elongation becomes 1 and the edge acutance gate never fires (bench:
    // srElongCap 1.4 restores below-Nyquist delivery 0.94x -> 1.25x at corr
    // 0.95, the 0.32-0.5 correlation 0.18 -> 0.27, and the edge rise 2.97 ->
    // 2.77 px). Legacy = 1 = unchanged.
    float lo = min(s1, s2);
    float hi = max(s1, s2);
    float hiCap = sigmaMaxPx * max(srElongCap, 1.0);
    lo = clamp(lo, sigmaMinPx.x, sigmaMaxPx);
    hi = clamp(hi, sigmaMinPx.y, hiCap);
    if (hi > lo * maxElong) {
        hi = lo * maxElong;
    }
    hi = min(hi, hiCap);
    bool s1maj = s1 >= s2;
    s1 = s1maj ? hi : lo;
    s2 = s1maj ? lo : hi;
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
            // Window-relative fetch: taps are absolute input coords, shifted
            // into the bound window (identity shift on the legacy path).
            ivec2 tap = clamp(fl + ivec2(i, j) - u_winOrigin, ivec2(0), cropSize - ivec2(1));
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
    vec2 inPos = (gl_FragCoord.xy + vec2(u_tileOrigin)) / scaleRatio;
    // Spatial precision correct UV creation
    vec2 uv = (gl_FragCoord.xy + vec2(u_tileOrigin)) / vec2(fullSize);
    // Window-relative filter UV: identical to uv on the legacy path
    // (u_winFullSize/cropSize is exactly 1.0 and the origin shift exactly 0.0
    // there, both exact in fp), correctly remapped onto a bound window here.
    vec2 uvWin = uv * (u_winFullSize / vec2(textureSize(InputBuffer, 0)))
            - vec2(u_winOrigin) / vec2(textureSize(InputBuffer, 0));
    if (debugMode == 1) {
        // Plain bilinear upscale of the input baseline.
        Output = texture(InputBuffer, uvWin);
        return;
    }
    vec2 sigmas;
    vec3 abc = anisoCoeffs(uv, sigmas);
    // Edge-confidence gate for the unsharp term: the used fraction of the
    // allowed elongation, reshaped by gateExp (< 1 steepens so medium edges
    // also sharpen; 1.0 is linear). Near-isotropic kernels (flats, smooth
    // gradients) stay ~0 so noise and banding stay buried, while real edges
    // (elongation capped at maxElong) get the full sharpAmt.
    float elong = max(sigmas.x, sigmas.y) / max(min(sigmas.x, sigmas.y), 1e-4);
    float gate = pow(clamp((elong - 1.0) / max(maxElong - 1.0, 1e-4), 0.0, 1.0), max(gateExp, 1e-3));
    gate = max(gate, clamp(SR_ACUT_FLAT, 0.0, 1.0));
    float sharpWideEff = min(sharpWide, (0.5 * float(kernelRadius)) / sigmaMaxPx);
    vec3 abcWide = abc / (sharpWideEff * sharpWideEff);
    vec4 accN;
    vec4 accW;
    accumulatePair(inPos, abc, abcWide, kernelRadius, accN, accW);
    vec4 aniso = accN.a > 1e-5
        ? accN / accN.a
        : texture(InputBuffer, uvWin);
    vec4 sharp = aniso;
    // The acutance's own addition (edge-gated, zero on flats and isotropic
    // kernels) is handed downstream in the output alpha: the SR resolve adds
    // it to the drizzled result, so the SR output gets the same edge
    // sharpening as the Disabled render without inheriting the KernelNet
    // reconstruction band (and its quantization mesh) that a band keep would.
    float acutTerm = 0.0;
    if (sharpAmt > 0.0 && gate > 0.0) {
        vec4 wide = accW.a > 1e-5 ? accW / accW.a : aniso;
        vec4 d = aniso - wide;
        // Halo suppression (Weber-like soft limit). A linear unsharp's
        // overshoot grows with the edge contrast, so the strongest edges -
        // exactly the highlight edges - got the worst halo. The tripod bench
        // is unambiguous: the 2x JPEG carries a ~25-level dark notch on the
        // pot edge that the drizzle's own raw (the 2x DNG, per-site greens)
        // does not have, and the resolve is a convex mix while the tone curve
        // is monotone, so this unsharp is provably the only term that can make
        // it. The added term is limited to acutRel of the LOCAL level: the
        // dark-side undershoot (the visible halo) is crushed where it is most
        // visible, the bright side keeps a mild crispening, and fine texture
        // (small differences) keeps the full amount. tanh is smooth - a hard
        // clamp bands at highlight edges.
        vec3 cap = max(vec3(acutRel) * aniso.rgb, vec3(1e-4));
        vec4 added = vec4(cap * tanh((sharpAmt * gate) * d.rgb / cap), 0.0);
        sharp = aniso + added;
        acutTerm = dot(added.rgb, vec3(0.2126, 0.7152, 0.0722)) * clamp(strength, 0.0, 1.0);
    }
    vec4 bic = textureBicubicHardware(InputBuffer, uvWin);
    vec4 base = mix(bic, sharp, clamp(strength, 0.0, 1.0));
    if (splitChroma != 0) {
        // Luma from the guided reconstruction, chroma from bicubic: chroma
        // planes are smooth, so the full 121-tap anisotropic filter buys
        // nothing there and only risks color moire; keeping bicubic chroma
        // also frees headroom for stronger luma acutance. (No lum709 macro in
        // this shader, so the Rec.709 dot is spelled out.)
        float yBase = dot(base.rgb, vec3(0.2126, 0.7152, 0.0722));
        float yBic = dot(bic.rgb, vec3(0.2126, 0.7152, 0.0722));
        base.rgb = vec3(yBase) + (bic.rgb - vec3(yBic));
    }
    Output = vec4(base.rgb, acutTerm);
}
