precision highp float;
precision mediump sampler2D;
uniform sampler2D RawBuffer;
#define QUAD 0
#define demosw (1.0/10000.0)
out vec2 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 shift = xy%2;
    ivec2 fact = (xy/2)%2;
    //if(fact1+fact2 != 1){
    //    Output = float(texelFetch(RawBuffer, (xy), 0).x);
    //}
    //else {
        //Output = clamp(float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel),0.,1.);
    //float center = float(texelFetch(RawBuffer, (xy), 0).x);
    float center = texelFetch(RawBuffer, (xy), 0).x;
    Output.r = //abs(
    float(texelFetch(RawBuffer, (xy+ivec2(1-shift.x*2,0)), 0).x) -
    center
    //)
    //+abs(2.0*center-float(texelFetch(RawBuffer, (xy+ivec2(-2,0)), 0).x)-float(texelFetch(RawBuffer, (xy+ivec2(2,0)), 0).x))
    ;
    Output.g = //abs(
    float(texelFetch(RawBuffer, (xy+ivec2(0,1-shift.y*2)), 0).x) -
    center
    //)
    //+abs(2.0*center-float(texelFetch(RawBuffer, (xy+ivec2(0,-2)), 0).x)-float(texelFetch(RawBuffer, (xy+ivec2(0,2)), 0).x))
    ;
    Output/=2.0;
    Output+=0.5;
    //}
}
