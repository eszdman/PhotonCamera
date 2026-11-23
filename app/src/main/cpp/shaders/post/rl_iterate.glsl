//
// Created by eszdman on 21.11.2026.
//

// Circular Richardson-Lucy convolution for radius 3, 4 or 6.
// The GLES pass bakes the same nonzero weights into its kernel expression.
#define UPDATE 0
uniform sampler2D SourceBuffer;
#if UPDATE
uniform sampler2D EstimateBuffer;
#else
uniform sampler2D OriginalBuffer;
#endif
uniform ivec2 validSize;
uniform int radius;
layout(std430,binding=1) buffer KernelWeights { float weights[]; };
layout(rgba16f,binding=0) writeonly uniform highp image2D OutputBuffer;
shared float tile[20*20];
void main() {
    int stride = 8+2*radius;
    ivec2 base = ivec2(gl_WorkGroupID.xy)*8-radius;
    for (int i=int(gl_LocalInvocationIndex); i<stride*stride; i+=64) {
        ivec2 q=clamp(base+ivec2(i%stride,i/stride),ivec2(0),validSize-1);
        tile[i]=texelFetch(SourceBuffer,q,0).r;
    }
    barrier();
    ivec2 p=ivec2(gl_GlobalInvocationID.xy);
    if(any(greaterThanEqual(p,validSize))) return;
    int first=int(gl_LocalInvocationID.y)*stride+int(gl_LocalInvocationID.x);
    float blurred=0.0;
    int k=0;
    for(int y=0;y<=2*radius;++y)
        for(int x=0;x<=2*radius;++x)
            blurred+=weights[k++]*tile[first+y*stride+x];
#if UPDATE
    float value=texelFetch(EstimateBuffer,p,0).r*blurred;
#else
    float value=texelFetch(OriginalBuffer,p,0).r/max(blurred,1e-7);
#endif
    imageStore(OutputBuffer,p,vec4(max(value,0.0)));
}
