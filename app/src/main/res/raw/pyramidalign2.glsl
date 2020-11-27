#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
precision mediump isampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform sampler2D AlignVectors;
uniform int prevLayerScale;
uniform int yOffset;
out vec2 Output;
#define TILESIZE (48)
#define SCANSIZE (48)
#define TILESCALE (TILESIZE/2)
#define oversizek (1)
#define MAXX (4*1)
#define MAXY (3*1)
#define FLT_MAX 3.402823466e+38
#define M_PI 3.1415926535897932384626433832795
#define distribute(x,dev,sigma) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))
#import coords
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 prevAlign = ivec2(0,0);
    if (prevLayerScale != 0) {
        prevAlign = ivec2(texelFetch(AlignVectors, xy / prevLayerScale, 0).rg*float(TILESIZE*256))*(prevLayerScale);
    }
    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESCALE));
    float dist = 0.0;
    float mindist = FLT_MAX;
    ivec2 outalign = ivec2(0,0);
    ivec2 shift = ivec2(0,0);
    ivec4 inbounds = ivec4(0,0,ivec2(textureSize(InputBuffer, 0)));
    //vec4 in1;
    //vec4 in2;
    float cachex[SCANSIZE];
    float cachey[SCANSIZE];
    for (int i=-SCANSIZE/2;i<SCANSIZE/2;i++){
        cachex[i+SCANSIZE/2] = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(i, 0)),inbounds), 0).a;
        cachey[i+SCANSIZE/2] = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(0, i)),inbounds), 0).a;
    }
    for(int h = 0;h<MAXY;h++){
        for(int w = 0;w<MAXX;w++){
            for(int i =-1;i<1;i+=2){
                for (int j =-1;j<1;j+=2){
                    shift = ivec2(w*i, h*j)+prevAlign;

                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(-1, -1)),inbounds), 0).a,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(-1, -1)),inbounds), 0).a, 0.1);
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(1, -1)),inbounds), 0).a,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(1, -1)),inbounds), 0).a, 0.1);
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(-1, 1)),inbounds), 0).a,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(-1, 1)),inbounds), 0).a, 0.1);
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(1, 1)),inbounds), 0).a,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(1, 1)),inbounds), 0).a, 0.1);
                    for (int h0=-SCANSIZE/2;h0<SCANSIZE/2;h0++){
                        dist+=distribute(cachey[h0+SCANSIZE/2],
                        texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(0, h0)),inbounds), 0).a, 0.1);
                    }
                    for (int w0=-SCANSIZE/2;w0<SCANSIZE/2;w0++){
                        dist+=distribute(cachex[w0+SCANSIZE/2],
                        texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(w0, 0)),inbounds), 0).a, 0.1);
                    }

                    /*if (dist < 0.15){
                        outalign = shift;
                        Output = vec3(outalign.x,outalign.y,0)/float(TILESIZE*16);
                        Output.b = dist;
                        return;*/
                    //} else {
                        if(dist < mindist){
                            mindist = dist;
                            outalign = shift;
                        }
                    //}
                    dist = 0.0;
                }
            }
        }
    }
    Output = vec2(outalign.x,outalign.y);
    //Output.b = (abs(cachex[SCANSIZE/2]-texelFetch(InputBuffer, mirrorCoords((xyFrame+outalign),inbounds), 0).a));
    Output/=float(TILESIZE*256);
    //Output = vec2(1000,1000);
}
