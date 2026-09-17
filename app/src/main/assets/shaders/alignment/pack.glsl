#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
uniform highp sampler2D inTexture;
layout(rgba16f, binding = 0) uniform highp writeonly image2D outTexture;
uniform ivec2 shift;
uniform ivec2 rawHalf;
uniform int startLevel;

void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    vec4 align = texelFetch(inTexture, xy >> startLevel, 0);
    if (startLevel > 0) {
        // The finest matched pyramid level was startLevel levels coarser than
        // raw/2, so one vector covers a 2^startLevel x 2^startLevel block of
        // the output grid (broadcast - mergeAlign's overlapping 4-tile window
        // fetch already smooths block edges) and its displacement must be
        // rescaled from that level's texels to raw/2 texels. Reconstruct,
        // scale and re-encode exactly like align2.alignmentToVec4 so
        // mergeAlign's rawHalf-based vec4ToAlignment keeps working unchanged.
        vec2 v = floor(align.xy * vec2(rawHalf) + vec2(0.5)) + align.zw;
        v *= float(1 << startLevel);
        align = vec4(floor(v) / vec2(rawHalf), fract(v));
    }
    imageStore(outTexture, xy + shift, align);
}