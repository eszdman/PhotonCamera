#version 300 es
uniform sampler2D GainMap;
#define CFAPATTERN (0)
#define RAWSIZE (1,1)
#import interpolation
out uint Output;
uniform int yOffset;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 fact = (xy-ivec2(CFAPATTERN%2,CFAPATTERN/2))%2;
    vec4 gains = textureBicubicHardware(GainMap, vec2(xy)/vec2(RAWSIZE));
    if(fact.x+fact.y == 1){
        Output = uint((gains.g+gains.b)*32768.0/2.0);
    } else if(fact.x == 0){
        Output = uint(gains.r*32768.0);
    } else {
        Output = uint(gains.a*32768.0);
    }
    //Output = uint(((gains.g+gains.b)/2.0 + gains.r + gains.a)*32768.0/9.0);
}
