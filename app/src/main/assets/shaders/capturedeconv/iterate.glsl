#define LAYOUT //
#define RADIUS 2
#define KERNEL 0.0
#define UPDATE 0
LAYOUT
precision highp float;
precision highp int;
precision highp sampler2D;
uniform sampler2D SourceBuffer;
uniform sampler2D OriginalBuffer;
uniform sampler2D EstimateBuffer;
uniform ivec2 validSize;
layout(rgba16f, binding = 0) writeonly uniform highp image2D OutputBuffer;
const int STRIDE = 16 + 2 * RADIUS;
shared float tile[STRIDE * STRIDE];
void main() {
    ivec2 base = ivec2(gl_WorkGroupID.xy) * 16 - RADIUS;
    // All lanes participate, including partial workgroups at the image edges.
    for (int i = int(gl_LocalInvocationIndex); i < STRIDE * STRIDE; i += 256) {
        ivec2 p = clamp(base + ivec2(i % STRIDE, i / STRIDE), ivec2(0), validSize - 1);
        tile[i] = texelFetch(SourceBuffer, p, 0).r;
    }
    barrier();
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(p, validSize))) return;
    int center = (int(gl_LocalInvocationID.y) + RADIUS) * STRIDE
            + int(gl_LocalInvocationID.x) + RADIUS;
    float blurred = KERNEL;
#if UPDATE
    float value = texelFetch(EstimateBuffer, p, 0).r * blurred;
#else
    float value = texelFetch(OriginalBuffer, p, 0).r / max(blurred, 1e-7);
#endif
    imageStore(OutputBuffer, p, vec4(max(value, 0.0)));
}
