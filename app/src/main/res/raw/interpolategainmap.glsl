#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D GainMap;
uniform ivec2 RawSize;
#define CFAPATTERN (0)
#define RAWSIZE 1,1
#import interpolation
out float Output;
uniform int yOffset;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 fact = (xy-ivec2(CFAPATTERN%2,CFAPATTERN/2))%2;
    vec4 gains = textureBicubic(GainMap, vec2(float(xy.x),float(xy.y))/vec2(RAWSIZE));
    if(fact.x+fact.y == 1){
        Output = ((gains.g+gains.b)/2.0);
    } else if(fact.x == 0){
        Output = gains.r;
    } else {
        Output = gains.a;
    }
    Output/=5.0;
    //Output = uint(((gains.g+gains.b)/2.0 + gains.r + gains.a)*32768.0/9.0);
}
