#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D RawBuffer;
#define QUAD 0
#define demosw (1.0/10000.0)
out vec2 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    //if(fact1+fact2 != 1){
    //    Output = float(texelFetch(RawBuffer, (xy), 0).x);
    //}
    //else {
        //Output = clamp(float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel),0.,1.);
    //float center = float(texelFetch(RawBuffer, (xy), 0).x);
    ivec2 shift = xy%2;
    if(shift.x+shift.y !=  1) return;
    Output.r = abs(
    float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x) -
    float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x))
    //+abs(2.0*center-float(texelFetch(RawBuffer, (xy+ivec2(-2,0)), 0).x)-float(texelFetch(RawBuffer, (xy+ivec2(2,0)), 0).x))
    ;
    Output.g = abs(
    float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x) -
    float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x))
    //+abs(2.0*center-float(texelFetch(RawBuffer, (xy+ivec2(0,-2)), 0).x)-float(texelFetch(RawBuffer, (xy+ivec2(0,2)), 0).x))
    ;
    Output/=2.0;
    //}
}
