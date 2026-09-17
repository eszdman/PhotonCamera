#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
// Normalized fp16 raw input (Allocator.createF16 pre-normalizes on the CPU).
uniform highp sampler2D inTexture;
uniform highp sampler2D gainMap;
layout(rgba16f, binding = 0) uniform highp writeonly image2D outTexture;

uniform float exposure;
uniform float blurSigma;

// Half-res packing prefilter: separable 5-tap gaussian over quad steps
// -2..2, centered on the texel. Fixed sigma 1.5 quad units (blurSigma uniform
// is passed the same constant by Java so the alignment's noise model can
// replicate the weights; see PyramidAlignment PREFILTER_N).
// No min/max trimming: hot pixels and stuck-black sites are already invisible
// after this average and the gaussian pyramid above it (a saturated site is
// ~1/25 of one channel of one tap), while dropping the extreme samples cuts
// real highlight/shadow detail the matcher needs - measured on real bursts in
// tools/alignment-bench (trim vs no-trim: identical error, less detail).
vec4 loadQuad(ivec2 coords) {
    // clamp fetches: out-of-range texelFetch is undefined and would darken
    // border texels, creating fake structure for the tile matcher
    ivec2 size = textureSize(inTexture, 0);
    coords = clamp(coords, ivec2(0), size - ivec2(2));
    vec4 c0 = vec4(texelFetch(inTexture, coords, 0).r,
                   texelFetch(inTexture, coords + ivec2(1, 0), 0).r,
                   texelFetch(inTexture, coords + ivec2(0, 1), 0).r,
                   texelFetch(inTexture, coords + ivec2(1, 1), 0).r);
    return clamp(c0, 0.0, 1.0);
}

void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    float s2 = 2.0 * max(blurSigma, 0.05) * max(blurSigma, 0.05);
    float wx[5];
    float wsum = 0.0;
    for (int i = 0; i < 5; i++) {
        float d = float(i - 2);
        wx[i] = exp(-d * d / s2);
        wsum += wx[i];
    }
    for (int i = 0; i < 5; i++) wx[i] /= wsum;
    vec4 sum = vec4(0.0);
    for (int j = 0; j < 5; j++) {
        for (int i = 0; i < 5; i++) {
            sum += wx[i] * wx[j] * loadQuad((xy + ivec2(i - 2, j - 2)) * 2);
        }
    }
    float gains = dot(texture(gainMap, (vec2(xy) + 0.5) / vec2(imageSize(outTexture))), vec4(0.25));
    vec4 bayer = clamp(sum * gains, vec4(0.0), vec4(1.0));
    imageStore(outTexture, xy, clamp(bayer * vec4(exposure), vec4(0.0), vec4(1.0)));
}
