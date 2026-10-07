#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
// Dense low-res flow (FlowNet's alignment texture): .xy = displacement in
// rawHalf px, the same units the cell atlas decodes to.
uniform highp sampler2D flowIn;
// Running base (packed RGBA at rawHalf size): the guidance image, exactly
// the guide merge/mergeAlignFlow uses for its per-pixel upsample.
uniform highp sampler2D guidePacked;
// Packed (rawHalf) size: maps this grid's texels onto the guide and the flow.
uniform ivec2 packedSize;
// Output: the guided motion in rawHalf px at every texel, sampled bilinearly
// by the SR drizzles (merge/srscatter, merge/srbayer).
layout(rgba16f, binding = 0) writeonly uniform highp image2D flowOut;

float luma(vec4 c) {
    return dot(c, vec4(0.25));
}

void main() {
    ivec2 o = ivec2(gl_GlobalInvocationID.xy);
    ivec2 outSize = imageSize(flowOut);
    if (o.x >= outSize.x || o.y >= outSize.y) return;
    // Packed position of this output texel's center (the guide's grid).
    vec2 toPacked = vec2(packedSize) / vec2(outSize);
    ivec2 flowSize = textureSize(flowIn, 0);
    ivec2 gmax = textureSize(guidePacked, 0) - ivec2(1);
    float curLuma = luma(texelFetch(guidePacked,
            clamp(ivec2((vec2(o) + vec2(0.5)) * toPacked), ivec2(0), gmax), 0));
    // Guided upsampling of the dense flow field (high-res guide + nearest
    // low-res flow), radius 2, eps 3e-4, identical to mergeAlignFlow: a local
    // linear model q = a*I + b is fit per output texel from 5x5-window sums
    // of the guide (I, I^2) and the nearest low-res flow (p, I*p). The flow
    // then follows the guide's edges instead of blending across them, which
    // is what a moving subject's boundary needs: a plain bilinear field
    // mixes the subject's motion with the background's there, so the drizzle
    // either fuses misregistered samples or drops those frames.
    vec2 windowP = vec2(0.0);
    vec2 windowIP = vec2(0.0);
    float windowI = 0.0, windowI2 = 0.0;
    for (int dy = -2; dy <= 2; dy++) {
        for (int dx = -2; dx <= 2; dx++) {
            ivec2 q = clamp(ivec2((vec2(o + ivec2(dx, dy)) + vec2(0.5)) * toPacked),
                    ivec2(0), gmax);
            float I = luma(texelFetch(guidePacked, q, 0));
            vec2 flowPosF = (vec2(q) + vec2(0.5)) / vec2(packedSize) * vec2(flowSize);
            ivec2 bl = clamp(ivec2(floor(flowPosF)), ivec2(0), flowSize - ivec2(1));
            vec2 p = texelFetch(flowIn, bl, 0).xy;
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
    imageStore(flowOut, o, vec4(a * curLuma + b, 0.0, 0.0));
}
