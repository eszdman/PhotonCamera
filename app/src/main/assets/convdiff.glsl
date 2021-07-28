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
#import coords
out tvar Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 insizev = ivec2(INSIZE);
    vec2 xy2 = vec2(xy)-vec2(insizev)/2.0;
    if(abs(rotation) >= 0.01){
        xy.x = int(xy2.x*cos(rotation)-xy2.y*sin(rotation));
        xy.y = int(xy2.y*sin(rotation)+xy2.y*cos(rotation));
        xy+=insizev/2;
    }
    //vec2 center = texelFetch(InputBuffer, xy,0).rg;
    //for(int i =-3; i<3;i++){
        //Output.r += sum(texelFetch(InputBuffer, mirrorCoords2(xy-ivec2(i,1),ivec2(INSIZE)),0).rg)-sum(texelFetch(InputBuffer, xy+ivec2(i,1),0).rg);
        //Output.g += sum(texelFetch(InputBuffer, mirrorCoords2(xy-ivec2(1,i),ivec2(INSIZE)),0).rg)-sum(texelFetch(InputBuffer, xy+ivec2(i,1),0).rg);
        Output.r = sum(texelFetch(InputBuffer, mirrorCoords2(xy-ivec2(i,1),ivec2(INSIZE)),0).rg)-sum(texelFetch(InputBuffer, xy+ivec2(i,1),0).rg);
        Output.g = sum(texelFetch(InputBuffer, mirrorCoords2(xy-ivec2(1,i),ivec2(INSIZE)),0).rg)-sum(texelFetch(InputBuffer, xy+ivec2(i,1),0).rg);
    //}
    //Output/=6.0;

    //Output*=max(1.0-abs((center.r+center.g)/2.0 - 0.5)*1.9,0.0);
    //Output=max(min((Output-0.02)*10.0,1.0),0.0);
    Output=(Output)/2.0;
    Output+=GRADSHIFT;
}
