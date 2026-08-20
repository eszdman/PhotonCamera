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
#define MAXLEVEL (1)
#define NORM 64.0
out float result;
#import gaussian
#import interpolation
float laplace(sampler2D tex, float mid, ivec2 xyCenter) {
    ivec2 size = textureSize(tex, 0);
    ivec2 leftPos = clamp(xyCenter - ivec2(1, 0), ivec2(0), size - ivec2(1));
    ivec2 rightPos = clamp(xyCenter + ivec2(1, 0), ivec2(0), size - ivec2(1));
    ivec2 topPos = clamp(xyCenter - ivec2(0, 1), ivec2(0), size - ivec2(1));
    ivec2 bottomPos = clamp(xyCenter + ivec2(0, 1), ivec2(0), size - ivec2(1));
    float left = texelFetch(tex, leftPos, 0).r,
    right = texelFetch(tex, rightPos, 0).r,
    top = texelFetch(tex, topPos, 0).r,
    bottom = texelFetch(tex, bottomPos, 0).r;

    return distance(4. * mid, (left + right + top + bottom)*NORM);
}
float laplace2(sampler2D tex, float mid, ivec2 xyCenter) {
    ivec2 size = textureSize(tex, 0);
    ivec2 leftPos = clamp(xyCenter - ivec2(1, 0), ivec2(0), size - ivec2(1));
    ivec2 rightPos = clamp(xyCenter + ivec2(1, 0), ivec2(0), size - ivec2(1));
    ivec2 topPos = clamp(xyCenter - ivec2(0, 1), ivec2(0), size - ivec2(1));
    ivec2 bottomPos = clamp(xyCenter + ivec2(0, 1), ivec2(0), size - ivec2(1));
    float left = texelFetch(tex, leftPos, 0).b,
    right = texelFetch(tex, rightPos, 0).b,
    top = texelFetch(tex, topPos, 0).b,
    bottom = texelFetch(tex, bottomPos, 0).b;

    return distance(4. * mid, (left + right + top + bottom)*NORM);
}

// 3x3 binomial [1,2,1]^2/16 pre-filter on the Laplacian detail: exact null at
// the one-texel even/odd (Nyquist) phase pattern the pyramid upsampling bakes
// into the detail, applied where the detail is read for blending.
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

    // If this is the lowest layer, start with zero.
    float base = (useUpsampled)
    ? texture(upsampled,
            vec2(gl_FragCoord.xy) * upscaleIn).r
    : float(0.0);
    // How are we going to blend these two? The detail is pre-filtered to strip
    // the even/odd phase pattern before it is blended and added.
    vec4 diffS = fetchDiffSmooth(normalExpoDiff, xyCenter);
    vec2 normal = diffS.rg;
    vec2 high = diffS.ba;

    // To know that, look at multiple factors.
    vec2 midNormal = texelFetch(normalExpo, xyCenter, 0).rg*NORM;
    vec2 midHigh = texelFetch(normalExpo, xyCenter, 0).ba*NORM;

    float normalWeight = 1.;
    float highWeight = 1.;

    // Factor 1: Well-exposedness.
    float midNormalToAvg = (pdf((midNormal.r - target)/gauss));
    float midHighToAvg = (pdf((midHigh.r - target)/gauss));

    normalWeight *= midNormalToAvg;
    highWeight *= midHighToAvg;

    // Factor 2: Contrast.
    float laplaceNormal = laplace(normalExpo, midNormal.r, xyCenter);
    float laplaceHigh = laplace2(normalExpo, midHigh.r, xyCenter);

    normalWeight *= (laplaceNormal + 0.001);
    highWeight *= (laplaceHigh + 0.001);

    float blend = highWeight / (normalWeight + highWeight); // [0, 1]
    result = base + mix(normal.r, high.r, blend);
    result = clamp(result,0.0,1.0);
    //if(level == 0){
    //    result = result*result;
    //}
}
