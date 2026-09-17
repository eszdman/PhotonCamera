#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
uniform highp sampler2D baseTexture;
uniform highp sampler2D gainMap;
// rgba16f (default) or rgba8 (u8 pyramid mode) - overridden by a define from
// PyramidAlignment; imageStore to rgba8 clamps to [0,1] implicitly.
#define OUT_FORMAT rgba16f
// 1 = store sqrt(v) (gamma ~2.0 encoding): the u8 quantization step in linear
// space becomes 2*sqrt(v)/255, proportional to the shot-noise sigma
// sqrt(v*noiseS+noiseO), so the quantization floor stays a fixed fraction of
// sigma across brightness instead of dominating it in the shadows. Decoded by
// squaring in align2.glsl's getPixel; pyramid levels stay encoded (bicubic
// filters in gamma space - a mild approximation).
#define SQRT_ENC 0
layout(OUT_FORMAT, binding = 0) uniform highp writeonly image2D outTexture;
uniform vec4 blackLevel;
uniform float whiteLevel;
uniform float sharpness;
#define TILE 2
#define CONCAT 1
#define M_PI 3.1415926535897932384626433832795
#define TILE_AL 16

vec4 getPixelLaplacian(ivec2 coords, highp sampler2D tex) {
    ivec2 size = textureSize(tex, 0);
    coords = clamp(coords, ivec2(1), size - ivec2(2));
    vec4 center = texelFetch(tex, coords, 0);
    vec4 left = texelFetch(tex, coords + ivec2(-1, 0), 0);
    vec4 right = texelFetch(tex, coords + ivec2(1, 0), 0);
    vec4 up = texelFetch(tex, coords + ivec2(0, -1), 0);
    vec4 down = texelFetch(tex, coords + ivec2(0, 1), 0);
    return (left + right + up + down) - center * 3.0;
}

void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    vec4 bayer = getPixelLaplacian(xy, baseTexture);
    bayer = mix(texelFetch(baseTexture, xy, 0), bayer, sharpness);
    float gains = dot(texture(gainMap, vec2(xy)/vec2(imageSize(outTexture).xy)),vec4(0.25));
    bayer = clamp((bayer - blackLevel) / (vec4(whiteLevel) - blackLevel), 0.0, 1.0);
#if SQRT_ENC
    bayer = sqrt(bayer);
#endif
    imageStore(outTexture, xy, bayer);
}