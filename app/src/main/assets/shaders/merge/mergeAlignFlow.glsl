#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
uniform highp usampler2D inTexture;
uniform highp sampler2D alignmentTexture;
//layout(r16ui, binding = 0) uniform highp readonly uimage2D inTexture;
layout(rgba16f, binding = 2) uniform highp readonly image2D baseTexture;
layout(rgba16f, binding = 3) uniform highp writeonly image2D outTexture;
layout(rgba16f, binding = 1) uniform highp readonly image2D alterTexture;
#define NIGHT_WEIGHTED 0
#if NIGHT_WEIGHTED
layout(rgba16f, binding = 0) uniform highp writeonly image2D sourceConfidence;
#import night_source_confidence
#endif

uniform float minLevel;
uniform uint whitelevel;
uniform vec4 blackLevel;
uniform float exposure;
uniform float exposureLow;
uniform bool createDiff;
uniform float noiseS;
uniform float noiseO;
uniform ivec2 border;
uniform ivec2 shift;
uniform ivec2 alignmentSize;
uniform ivec2 rawHalf;
uniform vec4 analogBalance;
// Dense optical-flow alignment (FlowNet): the alignment texture holds per-pixel
// flow at the model's reduced resolution. The full frame was stretched onto the
// model grid, so the flow vector stored at texel m already describes rawHalf
// pixel m*scale. Sampling with the plain normalized uv of the current rawHalf
// pixel reads the displacement for that pixel directly.
#define TILE 2
#define CONCAT 1
#define M_PI 3.1415926535897932384626433832795
#define TILE_AL 16

uint getBayer(ivec2 coords, highp usampler2D tex){
    return texelFetch(tex,coords,0).r;
}

vec4 getBayerVec(ivec2 coords, highp usampler2D tex){
    vec4 c0 = vec4(getBayer(coords,tex),getBayer(coords+ivec2(1,0),tex),getBayer(coords+ivec2(0,1),tex),getBayer(coords+ivec2(1,1),tex));
    return clamp((c0 - blackLevel)/(vec4(float(whitelevel))-blackLevel), 0.0, 1.0);
}

float window(float x){
    return 0.5f - 0.5f * cos(2.f * M_PI * ((0.5f * (x + 0.5f) / float(TILE_AL))));
}

float windowxy(ivec2 xy){
    return window(float(xy.x)) * window(float(xy.y));
}

vec4 windowxy4(ivec2 xy){
    return vec4(window(float(xy.x)) * window(float(xy.y)),
                window(float(xy.x+1)) * window(float(xy.y)),
                window(float(xy.x)) * window(float(xy.y+1)),
                window(float(xy.x+1)) * window(float(xy.y+1)));
}

vec2 vec4ToAlignment(vec4 alignment) {
    vec2 converted = vec2(alignment.x * float(rawHalf.x), alignment.y * float(rawHalf.y));
    converted.xy += alignment.zw;
    return converted;
}
vec2 hash22(vec2 p)
{
    vec3 p3 = fract(vec3(p.xyx) * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yzx+33.33);
    return fract((p3.xx+p3.yz)*p3.zy);
}
// Grayscale luma of the guidance image (base texture), computed right here
// with a dot product - no separate luminance pass needed.
float luma(vec4 c) {
    return dot(c, vec4(0.25));
}
void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    ivec2 outSize = imageSize(outTexture);
    if (any(greaterThanEqual(xy, outSize))) return;
    vec4 bayerBase = imageLoad(baseTexture,xy);
    // Guided upsampling of the dense flow field (high-res guide + low-res
    // nearest flow), radius 2, eps 3e-4. Local linear model q = a*I + b is fit
    // per output pixel from 5x5-window sums of the full-res guide (I, I^2) and
    // the piecewise-constant (nearest block) low-res flow (p, I*p). A,B are
    // NOT re-averaged over the window (single-window fit).
    ivec2 flowSize = textureSize(alignmentTexture, 0);
    float curLuma = luma(bayerBase);
    vec2 windowP = vec2(0.0);
    vec2 windowIP = vec2(0.0);
    float windowI = 0.0, windowI2 = 0.0;
    for (int dy = -2; dy <= 2; dy++) {
        for (int dx = -2; dx <= 2; dx++) {
            ivec2 q = clamp(xy + ivec2(dx, dy), ivec2(0), outSize - ivec2(1));
            float I = luma(imageLoad(baseTexture, q));       // full-res guide
            vec2 flowPosF = (vec2(q) + vec2(0.5)) / vec2(rawHalf) * vec2(flowSize);
            ivec2 bl = clamp(ivec2(floor(flowPosF)), ivec2(0), flowSize - ivec2(1));
            vec2 p = texelFetch(alignmentTexture, bl, 0).xy; // nearest low-res flow
            windowI += I;
            windowI2 += I * I;
            windowP += p;
            windowIP += p * I;
        }
    }
    float meanI = windowI / 25.0;
    float meanI2 = windowI2 / 25.0;
    vec2 meanP = windowP / 25.0;
    vec2 meanIP = windowIP / 25.0;
    float varI = max(meanI2 - meanI * meanI, 0.0);
    vec2 cov = meanIP - meanI * meanP;
    vec2 a = cov / (varI + 3e-4);
    vec2 b = meanP - a * meanI;
    vec2 flowOut = a * curLuma + b;
    ivec2 align = ivec2(flowOut);
    ivec2 aligned = clamp(xy + align, ivec2(0), outSize - ivec2(1));
    vec4 bayerAlter = imageLoad(alterTexture, aligned);
    imageStore(outTexture, xy, clamp(bayerAlter*vec4(exposure), vec4(0.0), vec4(1.0)));
#if NIGHT_WEIGHTED
    imageStore(sourceConfidence, xy, vec4(nightSourceConfidence(xy + align), 1.0));
#endif
}
