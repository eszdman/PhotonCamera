
#define LAYOUT //
LAYOUT
precision highp float;
precision highp uimage2D;
// Zeroes the scatter deposit accumulators once per shot before the first
// frame deposits (in-place atomics need a defined start).
layout(r32ui, binding = 0) writeonly uniform highp uimage2D srClearA;
layout(r32ui, binding = 1) writeonly uniform highp uimage2D srClearB;

void main() {
    ivec2 c = ivec2(gl_GlobalInvocationID.xy);
    ivec2 sz = imageSize(srClearA);
    if (c.x >= sz.x || c.y >= sz.y) return;
    imageStore(srClearA, c, uvec4(0u));
    imageStore(srClearB, c, uvec4(0u));
}
