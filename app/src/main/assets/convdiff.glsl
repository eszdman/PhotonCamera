#version 300 es
#define INSIZE 1,1
#define tvar vec2
#define tscal float
#define TSAMP sampler2D

precision mediump TSAMP;
precision mediump tscal;

uniform float rotation;
uniform TSAMP InputBuffer;
#define GRADSHIFT 0.5
#define j 0
#define i 0
#define sum(vecin) (vecin.r+vecin.g)
#define sum3(vecin) (vecin.r*0.299+vecin.g*0.587+vecin.b*0.114)
#import coords
#define TEXSIZE 2
out tvar Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 insizev = ivec2(INSIZE);
    vec2 xy2 = vec2(xy)-vec2(insizev)/2.0;
    #if TEXSIZE == 2
    if(abs(rotation) >= 0.01){
        xy.x = int(xy2.x*cos(rotation)-xy2.y*sin(rotation));
        xy.y = int(xy2.y*sin(rotation)+xy2.y*cos(rotation));
        xy+=insizev/2;
    }
    Output.r = sum(texelFetch(InputBuffer, mirrorCoords2(xy-ivec2(i,1),ivec2(INSIZE)),0).rg)-sum(texelFetch(InputBuffer, xy+ivec2(i,1),0).rg);
    Output.g = sum(texelFetch(InputBuffer, mirrorCoords2(xy-ivec2(1,i),ivec2(INSIZE)),0).rg)-sum(texelFetch(InputBuffer, xy+ivec2(i,1),0).rg);
    Output=(Output)/2.0;
    Output+=GRADSHIFT;
    #endif
    #if TEXSIZE == 3
    Output.r = sum3(texelFetch(InputBuffer, mirrorCoords2(xy-ivec2(i,1),ivec2(INSIZE)),0).rgb)-sum3(texelFetch(InputBuffer, xy+ivec2(i,1),0).rgb);
    Output.g = sum3(texelFetch(InputBuffer, mirrorCoords2(xy-ivec2(1,i),ivec2(INSIZE)),0).rgb)-sum3(texelFetch(InputBuffer, xy+ivec2(i,1),0).rgb);
    Output=(Output)/3.0;
    #endif
}
