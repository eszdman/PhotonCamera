#define LAYOUT //
#define VERTICAL 0
#define UPDATE 0
#define CENTER_WEIGHT 1.0
#define PAIR_WEIGHT 0.0
#define PAIR_OFFSET 1.0
#define CORNER_WEIGHT 0.0
LAYOUT
precision highp float;
precision highp int;
precision highp sampler2D;
uniform sampler2D SourceBuffer;
uniform sampler2D CornerBuffer;
uniform sampler2D OriginalBuffer;
uniform sampler2D EstimateBuffer;
uniform ivec2 validSize;
uniform int firstIteration;
layout(rgba16f, binding = 0) writeonly uniform highp image2D OutputBuffer;
vec4 fetchPlane(sampler2D plane, ivec2 p) {
    int last = (validSize.x - 1) / 4;
    vec4 v = texelFetch(plane,ivec2(clamp(p.x,0,last),clamp(p.y,0,validSize.y-1)),0);
    if(p.x < 0) v = vec4(v.x);
    if(p.x > last) v = vec4(v[(validSize.x-1)%4]);
    return v;
}
// Horizontal offsets of two pixels, spanning adjacent packed texels.
vec4 outerPair(ivec2 p) {
    vec4 left = fetchPlane(CornerBuffer,p-ivec2(1,0));
    vec4 center = fetchPlane(CornerBuffer,p);
    vec4 right = fetchPlane(CornerBuffer,p+ivec2(1,0));
    return vec4(left.zw,center.xy) + vec4(center.zw,right.xy);
}
void main() {
    ivec2 p=ivec2(gl_GlobalInvocationID.xy);
    if(p.x*4>=validSize.x || p.y>=validSize.y) return;
    const float outerWeight=PAIR_WEIGHT*(PAIR_OFFSET-1.0);
    const float innerWeight=PAIR_WEIGHT-outerWeight;
    vec4 center=fetchPlane(SourceBuffer,p);
    vec4 blurred=CENTER_WEIGHT*center;
#if VERTICAL
    blurred+=innerWeight*(fetchPlane(SourceBuffer,p-ivec2(0,1))+fetchPlane(SourceBuffer,p+ivec2(0,1)));
    blurred+=outerWeight*(fetchPlane(SourceBuffer,p-ivec2(0,2))+fetchPlane(SourceBuffer,p+ivec2(0,2)));
    // Subtract square-kernel corners to retain the circular Gaussian support.
    blurred-=CORNER_WEIGHT*(outerPair(p-ivec2(0,2))+outerPair(p+ivec2(0,2)));
    blurred=max(blurred/(1.0-4.0*CORNER_WEIGHT),vec4(0.0));
#if UPDATE
    blurred*=firstIteration!=0 ? texelFetch(OriginalBuffer,p,0) : texelFetch(EstimateBuffer,p,0);
#else
    blurred=texelFetch(OriginalBuffer,p,0)/max(blurred,vec4(1e-7));
#endif
#else
    vec4 left=fetchPlane(SourceBuffer,p-ivec2(1,0));
    vec4 right=fetchPlane(SourceBuffer,p+ivec2(1,0));
    blurred+=innerWeight*(vec4(left.w,center.xyz)+vec4(center.yzw,right.x));
    blurred+=outerWeight*(vec4(left.zw,center.xy)+vec4(center.zw,right.xy));
#endif
    // Replicate the last real pixel into padding after every pass.
    if(p.x==(validSize.x-1)/4) {
        int last=(validSize.x-1)%4;
        for(int c=last+1;c<4;c++) blurred[c]=blurred[last];
    }
    imageStore(OutputBuffer,p,blurred);
}
