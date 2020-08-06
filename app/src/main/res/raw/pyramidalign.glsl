#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform usampler2D AlignVectors;
uniform int yOffset;
uniform ivec2 maxSize;
uniform ivec2 minSize;
uniform int Mpy;
out ivec2 Output;
#define FLT_MAX 3.402823466e+38
#define TILESIZE (128)
#define MAXX (4)
#define MAXY (3)
float cmpTiles(ivec2 xy,int tSize,ivec2 shift){
    float dist = 0.0;
    int cnt = 0;
    tSize = max(2,tSize);
    ivec2 shifted =  xy+shift;
    for(int h=-1; h<tSize; h++){
        for(int w=-1;w<tSize;w++){
            dist+= abs((texelFetch(MainBuffer, (xy+ivec2(w,h)), 0).x)
            -(texelFetch(InputBuffer, shifted+ivec2(w,h), 0).x));
            cnt++;
        }
    }
    return dist/float(cnt);
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 align = ivec2(0,0);
    int mpy = Mpy;
    int tSize = TILESIZE/mpy;
    ivec2 prevAlign = ivec2(texelFetch(AlignVectors, (xy), 0).xy);
    float lumDist = FLT_MAX;
    if(xy.x < maxSize.x && xy.y < maxSize.y && xy.x > minSize.x && xy.y > minSize.y)
    for(int h =-MAXY;h<MAXY;h++){
        for(int w = -MAXX;w<MAXX;w++){
            float inp = cmpTiles(xy*tSize,tSize,ivec2(w,h)+prevAlign/mpy);
            if(inp < lumDist){
                lumDist = inp;
                align = ivec2(w,h);
            }
        }
    }
    Output = prevAlign + mpy*align;
}
