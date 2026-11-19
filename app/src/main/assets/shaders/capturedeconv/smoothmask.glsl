#define PACKED 0
#define LAYOUT //
LAYOUT
precision highp float;
precision highp int;
precision highp sampler2D;
uniform sampler2D MaskBuffer;
uniform ivec2 validSize;
layout(rgba16f, binding = 0) writeonly uniform highp image2D OutputBuffer;
float sampleMask(ivec2 p) {
    p = clamp(p,ivec2(0),validSize-1);
#if PACKED
    return texelFetch(MaskBuffer,ivec2(p.x/4,p.y),0)[p.x%4];
#else
    return texelFetch(MaskBuffer,p,0).r;
#endif
}
float smoothAt(ivec2 p) {
    // Horizontal half of the sigma=2 Gaussian; vertical half is fused with RGB output.
    float sum = 0.0;
    sum += 0.0022181959 * sampleMask(p + ivec2(-6,0));
    sum += 0.0087731348 * sampleMask(p + ivec2(-5,0));
    sum += 0.0270231576 * sampleMask(p + ivec2(-4,0));
    sum += 0.0648251851 * sampleMask(p + ivec2(-3,0));
    sum += 0.1211093901 * sampleMask(p + ivec2(-2,0));
    sum += 0.1762131228 * sampleMask(p + ivec2(-1,0));
    sum += 0.1996756275 * sampleMask(p + ivec2(0,0));
    sum += 0.1762131228 * sampleMask(p + ivec2(1,0));
    sum += 0.1211093901 * sampleMask(p + ivec2(2,0));
    sum += 0.0648251851 * sampleMask(p + ivec2(3,0));
    sum += 0.0270231576 * sampleMask(p + ivec2(4,0));
    sum += 0.0087731348 * sampleMask(p + ivec2(5,0));
    sum += 0.0022181959 * sampleMask(p + ivec2(6,0));
    return sum;
}
void main() {
    ivec2 p=ivec2(gl_GlobalInvocationID.xy);
#if PACKED
    ivec2 q=ivec2(p.x*4,p.y);
    if(any(greaterThanEqual(q,validSize))) return;
    imageStore(OutputBuffer,p,vec4(smoothAt(q),smoothAt(q+ivec2(1,0)),smoothAt(q+ivec2(2,0)),smoothAt(q+ivec2(3,0))));
#else
    if(any(greaterThanEqual(p,validSize))) return;
    imageStore(OutputBuffer,p,vec4(smoothAt(p)));
#endif
}
