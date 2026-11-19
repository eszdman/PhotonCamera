#define PACKED 0
#define LAYOUT //
LAYOUT
precision highp float;
precision highp int;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform int originY;
uniform ivec2 validSize;
layout(rgba16f, binding = 0) writeonly uniform highp image2D OutputBuffer;
float luminanceAt(ivec2 p) {
    p.x = min(p.x, validSize.x - 1);
    return max(dot(texelFetch(InputBuffer, p + ivec2(0, originY), 0).rgb,
            vec3(0.212671, 0.715160, 0.072169)), 0.0);
}
void main() {
    ivec2 p=ivec2(gl_GlobalInvocationID.xy);
#if PACKED
    ivec2 q=ivec2(p.x*4,p.y);
    if(any(greaterThanEqual(q,validSize))) return;
    imageStore(OutputBuffer,p,vec4(luminanceAt(q),luminanceAt(q+ivec2(1,0)),luminanceAt(q+ivec2(2,0)),luminanceAt(q+ivec2(3,0))));
#else
    if(any(greaterThanEqual(p,validSize))) return;
    imageStore(OutputBuffer,p,vec4(luminanceAt(p)));
#endif
}
