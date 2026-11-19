#define LAYOUT //
LAYOUT
precision highp float;
precision highp int;
precision highp sampler2D;

uniform sampler2D rawTexture;
uniform mat3 gyroHomography;
// Two raw fp16 pixels per uint: directly consumable by the Halide fp16 kernels.
layout(std430, binding = 0) writeonly buffer WarpedRaw {
    uint pixels[];
};

float warpSite(ivec2 p, ivec2 rawSize) {
    ivec2 parity = p & 1;
    vec3 projected = gyroHomography * vec3(vec2(p), 1.0);
    ivec2 last = rawSize / 2 - 1;
    vec2 uv = clamp((projected.xy / projected.z - vec2(parity)) * 0.5,
                    vec2(0.0), vec2(last));
    ivec2 lo = ivec2(floor(uv));
    ivec2 hi = min(lo + 1, last);
    vec2 f = fract(uv);
    // Sample the same Bayer parity at every corner; never interpolate colors.
    float a = texelFetch(rawTexture, 2 * lo + parity, 0).r;
    float b = texelFetch(rawTexture, 2 * ivec2(hi.x, lo.y) + parity, 0).r;
    float c = texelFetch(rawTexture, 2 * ivec2(lo.x, hi.y) + parity, 0).r;
    float d = texelFetch(rawTexture, 2 * hi + parity, 0).r;
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    ivec2 pair = ivec2(gl_GlobalInvocationID.xy);
    ivec2 rawSize = textureSize(rawTexture, 0);
    if (pair.x >= rawSize.x / 2 || pair.y >= rawSize.y) return;
    ivec2 p = ivec2(2 * pair.x, pair.y);
    pixels[pair.y * (rawSize.x / 2) + pair.x] = packHalf2x16(
            vec2(warpSite(p, rawSize), warpSite(p + ivec2(1, 0), rawSize)));
}
