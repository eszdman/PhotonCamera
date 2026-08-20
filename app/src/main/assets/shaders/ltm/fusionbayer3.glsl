precision highp float;
precision highp sampler2D;
uniform sampler2D upsampled;
uniform bool useUpsampled;
uniform float blendMpy;
// Weighting is done using these.
uniform sampler2D normalExpo;

// Blending is done using these.
uniform sampler2D normalExpoDiff;

uniform int level;
// Reciprocal of the output dimensions, computed on the CPU as 1.0/size.
// Multiplying by the precomputed reciprocal keeps full precision and avoids
// the (slower, less precise) per-fragment GPU division.
uniform vec2 upscaleIn;
uniform float gauss;
uniform float target;
//#define TARGET 0.0
//#define GAUSS 0.5
#define MAXLEVEL 4
#define NORM 1.0
#define EPS 1e-6
#define LAPLACEMIN 0.01
#define EXPOMIN 0.01
out float result;
#import gaussian
#import interpolation

vec4 laplace(sampler2D tex, vec4 mid, ivec2 xyCenter) {
        // textureSize is loop-invariant: hoist it out of the 9-tap loop.
        ivec2 size = textureSize(tex, 0);
        vec4 outp = mid*9.0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                ivec2 pos = clamp(xyCenter + ivec2(i, j), ivec2(0), size - ivec2(1));
                outp -= texelFetch(tex, pos, 0);
            }
        }
        return abs(outp);
}

// 3x3 binomial [1,2,1]^2/16 pre-filter on the Laplacian detail. The pyramid's
// upsampling bakes a one-texel even/odd (Nyquist) phase pattern into the
// detail; this kernel has an exact spectral null at Nyquist and removes it at
// the point where the detail is added, instead of letting it ride into the
// fused result. It is a plain low-pass (not a filter swap), so it preserves
// the detail's local mean and does not disturb the analysis/synthesis match.
vec4 fetchDiffSmooth(sampler2D tex, ivec2 xy) {
    ivec2 size = textureSize(tex, 0);
    vec4 acc = vec4(0.0);
    float ws = 0.0;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            ivec2 pos = clamp(xy + ivec2(i, j), ivec2(0), size - 1);
            float w = float((i == 0) ? 2 : 1) * float((j == 0) ? 2 : 1);
            acc += texelFetch(tex, pos, 0) * w;
            ws += w;
        }
    }
    return acc / ws;
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    // If this is the lowest layer, start with zero. The +0.5 centers the
    // coordinate on the output texel so the bilinear fetch is not biased
    // half a texel toward the origin at every pyramid level.
    vec2 upCoord = (vec2(gl_FragCoord.xy) + vec2(0.5)) * upscaleIn;
    float base = (useUpsampled)
    ? texture(upsampled, upCoord).r
    : float(0.0);

    // To know that, look at multiple factors.
    vec4 expoVal = texelFetch(normalExpo, xyCenter, 0)*NORM;
    vec4 weights = vec4(1.0, 1.0, 1.0, 1.0);
    // Factor 1: Well-exposedness.
    vec4 normToAvg = (pdf4((expoVal - vec4(target))/gauss));

    weights *= normToAvg + EXPOMIN;

    // Factor 2: Contrast.
    vec4 laplaceVal = laplace(normalExpo, expoVal/NORM, xyCenter)*NORM;

    weights *= laplaceVal + LAPLACEMIN;

    weights *= weights;
    // How are we going to blend these two? The detail is pre-filtered to strip
    // the even/odd phase pattern before it is weighted and added.
    vec4 expoDiff = fetchDiffSmooth(normalExpoDiff, xyCenter);
    float detail = (expoDiff.r*weights.r + expoDiff.g*weights.g +
            expoDiff.b*weights.b + expoDiff.a*weights.a) /
            max(weights.r + weights.g + weights.b + weights.a, EPS);
    float resultVal = base + detail * blendMpy;
    if (useUpsampled) {
        // Keep the reconstruction inside the local base range so the pyramid
        // cannot synthesize bright/dark rings around strong light sources:
        // excursions above the neighborhood max or below the neighborhood min
        // are clipped instead of being added as a halo. Neighbor taps are
        // clamped explicitly because texture wrap state is not guaranteed.
        vec2 texSize = vec2(textureSize(upsampled, 0));
        vec2 d = vec2(1.0) / texSize;
        vec2 n0 = clamp(upCoord + vec2(-d.x, 0.0), vec2(0.0), vec2(1.0));
        vec2 n1 = clamp(upCoord + vec2(d.x, 0.0), vec2(0.0), vec2(1.0));
        vec2 n2 = clamp(upCoord + vec2(0.0, -d.y), vec2(0.0), vec2(1.0));
        vec2 n3 = clamp(upCoord + vec2(0.0, d.y), vec2(0.0), vec2(1.0));
        float lo = min(base, min(min(texture(upsampled, n0).r, texture(upsampled, n1).r),
                                 min(texture(upsampled, n2).r, texture(upsampled, n3).r)));
        float hi = max(base, max(max(texture(upsampled, n0).r, texture(upsampled, n1).r),
                                 max(texture(upsampled, n2).r, texture(upsampled, n3).r)));
        resultVal = clamp(resultVal, lo, hi);
    }
    result = clamp(resultVal, 0.0, 1.0);
    //if(level == 0){
    //    result = result*result;
    //}
}
