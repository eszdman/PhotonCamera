precision highp sampler2D;
precision highp float;
uniform sampler2D InputBuffer;   // low-res fused LTM result
uniform sampler2D BrBuffer;      // low-res base brightness (clean guide)
uniform float factor;            // legacy, unused (kept so setVar("factor", ...) stays valid)
uniform int yOffset;
out vec2 result;
#define DH (0.0)
#define FUSIONGAIN 1.0
#define NORM 64.0
// Ceiling on the LTM gain carried by the map. The same bound is re-applied to
// the upsampled gain in initial.glsl.
#define FUSIONCAP 8.0
// Range gate for the edge-aware smoothing, in BrBuffer units. Must sit above
// the base's own noise/ripple (so flat regions average freely and the pattern
// dies) and below genuine edge steps (so the gain edge is not bled across).
// Typical edge steps in BrBuffer are ~0.1; if the pattern survives, raise it
// slightly, if an edge looks soft, lower it.
#define RANGESIGMA 0.06
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
float gammaInverse(float x) {
    return x*x;
}

vec4 reinhard_extended(vec4 v, float max_white) {
    vec4 numerator = v * (vec4(1.0f) + (v / vec4(max_white * max_white)));
    return numerator / (vec4(1.0f) + v);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy += ivec2(0, yOffset);
    ivec2 inputSize = textureSize(InputBuffer, 0);
    ivec2 safePos = clamp(xy, ivec2(0), inputSize - ivec2(1));

    float centerBase = max(texelFetch(BrBuffer, safePos, 0).r, 0.0);

    // Edge-aware (bilateral) smoothing of the per-texel fused/base ratio, with
    // the anti-cascade notch spatial kernel [1,2,2,2,2,2,2,2,1]/16.
    //
    // The fused buffer carries the pyramid's even/odd upsampling phase pattern
    // with amplitude proportional to local edge contrast, so the ratio needs
    // smoothing precisely at hard transitions — exactly where a Gaussian also
    // spreads the gain edge itself (the halo, and the grid inside it). The
    // notch kernel is [1,2,1] x [1,0,1] x [1,0,0,0,1], so its frequency
    // response has exact zeros at f = 1/2 (Nyquist), f = 1/4 (2-texel period)
    // and f = 1/8 (4-texel period) — precisely the frequencies at which the
    // pyramid's 2x upsampling stages inject their even/odd phase patterns.
    // A Gaussian has no nulls and can only trade pattern suppression against
    // kernel width (ever-larger sigmas shrink the grid while growing the
    // halo); the notch removes the pattern outright.
    //
    // A pure spatial notch spreads the gain edge across its whole support
    // (that halo). The range gate computed from the CLEAN base brightness
    // rejects taps from the far side of an edge, so the gain edge is not bled
    // across while the same-side taps still see the full notch and null the
    // pattern. Because the gate is driven by the smooth base rather than the
    // patterned ratio, the pattern cannot fragment the gate — the failure
    // mode that makes range filters useless on checkerboard does not apply.
    float ratioSum = 0.0;
    float ws = 0.0;
    const float rangeSigmaSq2 = 2.0 * RANGESIGMA * RANGESIGMA;
    for (int i = -4; i <= 4; i++) {
        float wi = (abs(i) == 4) ? 1.0 : 2.0;
        for (int j = -4; j <= 4; j++) {
            ivec2 pos = clamp(xy + ivec2(i, j), ivec2(0), inputSize - ivec2(1));
            float fusedValue = max(texelFetch(InputBuffer, pos, 0).r, 0.0);
            float baseValue  = max(texelFetch(BrBuffer, pos, 0).r, 0.0);
            float ratio = fusedValue / max(baseValue, 0.0001);
            // The ratio is unreliable when the base is near black: blend toward
            // the neutral gain as the denominator becomes uninformative.
            float ratioConfidence = smoothstep(0.001, 0.01, baseValue);
            float gain = clamp(mix(1.0, ratio, ratioConfidence), 0.0, FUSIONCAP);
            float spatialWeight = wi * ((abs(j) == 4) ? 1.0 : 2.0);
            float baseDelta = baseValue - centerBase;
            float rangeWeight = exp(-(baseDelta * baseDelta) / rangeSigmaSq2);
            float w = spatialWeight * rangeWeight;
            ratioSum += gain * w;
            ws += w;
        }
    }
    // Re-clamp the smoothed average: the notch can overshoot slightly where
    // the gate truncates it near an edge, and the map must stay inside its
    // legal gain range before the full-res application.
    float lowresVal  = clamp(ratioSum / max(ws, 1e-6), 0.0, FUSIONCAP);
    // Dark-base gain cap: in deep shadows the fused/base ratio is dominated
    // by the long-exposure lift, and applying it multiplies the shadow noise
    // floor by the same factor. Limit the lift where the base is dark and
    // release it smoothly into the midtones, where the signal can afford it.
    // Applied to the smoothed map, so the cap cannot introduce spatial
    // structure of its own.
    float darkCap = mix(1.5, FUSIONCAP, smoothstep(0.04, 0.20, centerBase));
    lowresVal = min(lowresVal, darkCap);
    // /FUSIONGAIN so the *FUSIONGAIN in initial.glsl recovers the true gain.
    result = vec2(lowresVal / FUSIONGAIN, 0.0);
}
