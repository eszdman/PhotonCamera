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
    float center = texelFetch(RawBuffer, (xy+ivec2(0,0)), 0).x;
    vec4 temp;
    temp.r = center - texelFetch(RawBuffer, (xy+ivec2(1,1)), 0).x;
    temp.g = center - texelFetch(RawBuffer, (xy+ivec2(-1,1)), 0).x;
    temp.b = center - texelFetch(RawBuffer, (xy+ivec2(-1,-1)), 0).x;
    temp.a = center - texelFetch(RawBuffer, (xy+ivec2(1,-1)), 0).x;

    /*Output.r = abs(
    float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x) -
    float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x))
    //+abs(2.0*center-float(texelFetch(RawBuffer, (xy+ivec2(-2,0)), 0).x)-float(texelFetch(RawBuffer, (xy+ivec2(2,0)), 0).x))
    ;
    Output.g = abs(
    float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x) -
    float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x))
    //+abs(2.0*center-float(texelFetch(RawBuffer, (xy+ivec2(0,-2)), 0).x)-float(texelFetch(RawBuffer, (xy+ivec2(0,2)), 0).x))
    ;
    */
    Output.xy = abs(vec2(
    ((temp.r+temp.a)-(temp.g+temp.b)),
    ((temp.r+temp.g)-(temp.b+temp.a))));
    Output/=4.0;
    //}
}
