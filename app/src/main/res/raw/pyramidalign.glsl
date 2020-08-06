#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform usampler2D AlignVectors;
uniform int yOffset;
uniform int Mpy;
out ivec2 Output;
#define FLT_MAX 3.402823466e+38
#define TILESIZE (32)
#define MAXX (4)
#define MAXY (3)
float cmpTiles(ivec2 xy,int tSize,ivec2 shift){
    float dist = 0.0;
    for(int h=0; h<tSize; h++){
        for(int w=0;w<tSize;w++){
            dist+= abs(float(texelFetch(MainBuffer, (xy+ivec2(w,h)), 0).x)
            -float(texelFetch(InputBuffer, (xy+ivec2(w,h)+shift), 0).x));
        }
    }
    return dist;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 align = ivec2(0,0);
    int tSize = TILESIZE/Mpy;
    ivec2 prevAlign = ivec2(texelFetch(AlignVectors, (xy/tSize), 0).xy);
    float lumDist = FLT_MAX;
    for(int h =-MAXY;h<MAXY;h++){
        for(int w = -MAXX;w<MAXX;w++){
            float inp = cmpTiles(xy,tSize,ivec2(w,h)+prevAlign/tSize);
            if(inp < lumDist){
                lumDist = inp;
                align = ivec2(w,h);
            }
        }
    }
    Output = prevAlign + Mpy*align;
}
