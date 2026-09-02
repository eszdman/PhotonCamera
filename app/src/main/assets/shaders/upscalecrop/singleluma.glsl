precision highp float;
precision highp usampler2D;
uniform highp usampler2D InputBuffer;
uniform float blMean;
uniform float whiteLevel;
out vec4 Output;

// One luma sample per 2x2 raw quad, four samples packed per rgba16f texel -
// exactly the KernelNet input built by the merge path (merge00 + mergeGrayscale
// in ESD4D), so the single-frame inference sees the same distribution as the
// multi-frame one. Luma is the quad mean (CFA-agnostic), black/white
// normalized and square-rooted, matching the model's training input.
float quadLuma(ivec2 packed) {
    ivec2 sz = textureSize(InputBuffer, 0);
    ivec2 b = packed * 2;
    vec4 c = vec4(
        float(texelFetch(InputBuffer, clamp(b, ivec2(0), sz - ivec2(1)), 0).r),
        float(texelFetch(InputBuffer, clamp(b + ivec2(1, 0), ivec2(0), sz - ivec2(1)), 0).r),
        float(texelFetch(InputBuffer, clamp(b + ivec2(0, 1), ivec2(0), sz - ivec2(1)), 0).r),
        float(texelFetch(InputBuffer, clamp(b + ivec2(1, 1), ivec2(0), sz - ivec2(1)), 0).r));
    float mean = dot(c, vec4(0.25)) / max(whiteLevel, 1.0);
    float l = clamp((mean - blMean) / max(1.0 - blMean, 1e-4), 0.0, 1.0);
    return sqrt(l);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 packedSize = textureSize(InputBuffer, 0) / 2;
    vec4 out4;
    for (int i = 0; i < 4; i++) {
        out4[i] = quadLuma(clamp(ivec2(xy.x * 4 + i, xy.y), ivec2(0), packedSize - ivec2(1)));
    }
    Output = out4;
}
