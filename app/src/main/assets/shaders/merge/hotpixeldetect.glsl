precision highp float;
precision highp int;
precision highp sampler2D;
uniform highp sampler2D inTexture;
uniform ivec2 rawSize;
uniform ivec2 cfaShift;
uniform float exposure;
uniform float noiseS;
uniform float noiseO;
uniform float detectThr;
uniform int maxCount;

struct HotPixel {
    uint x;
    uint y;
    uint channels; // Packed CFA site bits, not RGB color indices.
    uint strength;
};
layout(std430, binding = 1) buffer HotPixelList {
    uint count;
    HotPixel hotPixels[];
};
#define LAYOUT //
LAYOUT
void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(p, textureSize(inTexture, 0)))) return;
    ivec2 raw = 2 * p - cfaShift;
    // Only propose real sites with a complete neighborhood, never CFA padding.
    if (any(lessThan(raw, ivec2(3))) || any(greaterThanEqual(raw, rawSize - ivec2(4)))) return;
    vec4 maximum = vec4(0.0);
    for (int dy = -1; dy <= 1; ++dy)
        for (int dx = -1; dx <= 1; ++dx)
            if (dx != 0 || dy != 0)
                maximum = max(maximum, texelFetch(inTexture, p + ivec2(dx,dy), 0) / exposure);
    vec4 value = texelFetch(inTexture, p, 0) / exposure;
    vec4 threshold = max(vec4(.02), detectThr * sqrt(max(noiseS * maximum + noiseO, vec4(0.0))));
    bvec4 proposed = greaterThan(value - maximum, threshold);
    uint mask = 0u;
    for (int c = 0; c < 4; ++c) {
        ivec2 site = raw + ivec2(c % 2, c / 2);
        if (proposed[c] && all(greaterThanEqual(site, ivec2(4)))
                && all(lessThan(site, rawSize - ivec2(4)))) mask |= 1u << uint(c);
    }
    if (mask != 0u) {
        uint index = atomicAdd(count, 1u);
        if (index < uint(maxCount)) hotPixels[index] = HotPixel(uint(p.x), uint(p.y), mask, 0u);
    }
}
