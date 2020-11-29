#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform sampler2D AlignVectors;
uniform int prevLayerScale;
uniform int yOffset;
out vec2 Output;
#define TILESIZE (48)
#define SCANSIZE (48)
#define TILESCALE (TILESIZE/2)
#define OFFSET (0)
#define MAXX (4*1)
#define MAXY (3*1)
#define FLT_MAX 3.402823466e+38
#define M_PI 3.1415926535897932384626433832795
//#define distribute(x,dev,sigma) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))
#define distribute(x,dev,sigma) (abs(x-dev))
//#define getVal(somevec) (somevec.r+somevec.g+somevec.b-somevec.a)
#import coords
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 prevAlign = ivec2(0,0);
    if (prevLayerScale != 0) {
        prevAlign = ivec2(texelFetch(AlignVectors, xy / prevLayerScale, 0).rg*float(TILESIZE*256*prevLayerScale));
    }
    ivec2 xyFrame = ivec2(xy*(TILESCALE));
    float dist = 0.0;
    float mindist = FLT_MAX;
    ivec2 outalign = ivec2(0,0);
    ivec2 shift = ivec2(0,0);
    ivec4 inbounds = ivec4(0,0,ivec2(textureSize(InputBuffer, 0)));
    vec4 in1;
    vec4 in2;
    float cachex[SCANSIZE];
    float cachey[SCANSIZE];
    for (int i=-SCANSIZE/2;i<SCANSIZE/2;i++){
        cachex[i+SCANSIZE/2] = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(i, 0)),inbounds), 0).a;
        cachey[i+SCANSIZE/2] = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(0, i)),inbounds), 0).a;
    }
    for(int h = -MAXY;h<MAXY;h++){
        for(int w = -MAXX;w<MAXX;w++){
                    shift = ivec2(w, h)+prevAlign-OFFSET;
                    dist=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(-1, -1)),inbounds), 0).a,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(-1, -1)),inbounds), 0).a, 0.1);
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(1, -1)),inbounds), 0).a,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(1, -1)),inbounds), 0).a, 0.1);
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(-1, 1)),inbounds), 0).a,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(-1, 1)),inbounds), 0).a, 0.1);
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(1, 1)),inbounds), 0).a,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(1, 1)),inbounds), 0).a, 0.1);
                    for (int t=-SCANSIZE/2;t<SCANSIZE/2;t++){
                        dist+=distribute(cachey[t+SCANSIZE/2],
                        texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(0, t)),inbounds), 0).a, 0.1);
                        dist+=distribute(cachex[t+SCANSIZE/2],
                        texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(t, 0)),inbounds), 0).a, 0.1);
                    }

                    /*if (dist < 0.15){
                        outalign = shift;
                        Output = vec3(outalign.x,outalign.y,0)/float(TILESIZE*16);
                        Output.b = dist;
                        return;*/
                    //} else {
                    if(dist < mindist){
                            mindist = dist;
                            outalign = shift+OFFSET;
                    }
                    //}
        }
    }

    Output = vec2(outalign.x,outalign.y);

    //Output.r = texelFetch(InputBuffer, xy, 0).r;

    //Output.b = (abs(cachex[SCANSIZE/2]-texelFetch(InputBuffer, mirrorCoords((xyFrame+outalign),inbounds), 0).a));
    Output/=float(TILESIZE*256);
    //Output = vec2(TILESIZE,TILESIZE);
}
