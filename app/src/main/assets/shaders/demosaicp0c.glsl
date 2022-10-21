precision highp float;
precision highp int;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 1) uniform highp writeonly image2D outTexture;
#define WB 1.0,1.0,1.0
#define BL 0.0,0.0,0.0
#define LAYOUT //
#define OUTSET 0,0
LAYOUT
void main() {
    ivec2 xyIn = ivec2(gl_GlobalInvocationID.xy);
    if(xyIn.x >= ivec2(OUTSET).x) return;
    if(xyIn.y >= ivec2(OUTSET).y) return;
    vec4 Output;
    Output.r = (
    float(imageLoad(inTexture, (xyIn+ivec2(-1,0))).x) -
    float(imageLoad(inTexture, (xyIn+ivec2(1,0))).x))
    //+abs(2.0*center-float(texelFetch(RawBuffer, (xy+ivec2(-2,0)), 0).x)-float(texelFetch(RawBuffer, (xy+ivec2(2,0)), 0).x))
    ;
    Output.g = (
    float(imageLoad(inTexture, (xyIn+ivec2(0,-1))).x) -
    float(imageLoad(inTexture, (xyIn+ivec2(0,1))).x))
    //+abs(2.0*center-float(texelFetch(RawBuffer, (xy+ivec2(0,-2)), 0).x)-float(texelFetch(RawBuffer, (xy+ivec2(0,2)), 0).x))
    ;
    Output/=2.0;
    imageStore(outTexture, xyIn, Output);
}