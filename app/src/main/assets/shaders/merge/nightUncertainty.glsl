#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
uniform highp sampler2D mergedImage;
uniform highp sampler2D varianceImage;
uniform highp sampler2D precisionImage;
uniform ivec2 rawSize;
uniform ivec2 cfaShift;
uniform float referenceS;
uniform float referenceO;
layout(rgba32f, binding = 0) uniform highp writeonly image2D uncertaintyOutput;
void main() {
    ivec2 cell = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(cell, imageSize(uncertaintyOutput)))) return;
    float worstRatio = 0.0, worstVariance = 0.0;
    // One cell per 16x16 RAW pixels. Max reduction retains narrow rejected regions.
    // Visit every quad touched by the cell, including CFA-shifted boundaries.
    ivec2 first = (cell * 16 + cfaShift) / 2;
    ivec2 last = (min(cell * 16 + 15, rawSize - 1) + cfaShift) / 2;
    for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) {
        ivec2 q = first + ivec2(x, y);
        if (any(greaterThan(q, last))) continue;
        vec4 signalValue = texelFetch(mergedImage, q, 0);
        float signalMax = max(max(signalValue.r, signalValue.g), max(signalValue.b, signalValue.a));
        float referenceVariance = max(referenceS * max(signalMax, 0.0) + referenceO, 1e-10);
        float variance = max(texelFetch(varianceImage, q, 0).r, 1e-10);
        float precisionValue = texelFetch(precisionImage, q, 0).r;
        float ratio = precisionValue > 0.0 ? clamp(variance / referenceVariance, .001, 1.0) : 1.0;
        worstRatio = max(worstRatio, ratio);
        worstVariance = max(worstVariance, variance);
    }
    imageStore(uncertaintyOutput, cell, vec4(worstVariance, worstRatio, 0.0, 1.0));
}
