#version 300 es
precision highp float;
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
#define MAXMP (2)
#define MAXX (4*MAXMP)
#define MAXY (3*MAXMP)
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
        prevAlign = ivec2((vec2(0.5)-texelFetch(AlignVectors, xy / prevLayerScale, 0).rg)*float(TILESIZE*8))*prevLayerScale;
    }
    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESCALE));
    vec2 dist = vec2(0.0);
    vec2 mindist = vec2(FLT_MAX);
    ivec2 outalign = ivec2(0,0);
    ivec2 shift = ivec2(0,0);
    ivec4 inbounds = ivec4(0,0,ivec2(textureSize(InputBuffer, 0)));
    vec4 in1;
    vec4 in2;
    vec2 cachex[SCANSIZE+1];
    vec2 cachey[SCANSIZE+1];
    for (int i=-SCANSIZE/2;i<SCANSIZE/2;i++){
        cachex[i+SCANSIZE/2] = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(i, 0)),inbounds), 0).rg;
        cachey[i+SCANSIZE/2] = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(0, i)),inbounds), 0).rg;
    }
    for(int h = -MAXY;h<MAXY;h++){
        for(int w = -MAXX;w<MAXX;w++){
            float dist3 = 1.0+abs(float(w)/float(MAXX))+abs(float(h)/float(MAXY));
            dist = vec2(0.0);
            shift = ivec2(w, h)+prevAlign-OFFSET;
            /*for(int h0= -SCANSIZE/2;h0<SCANSIZE/2;h0++){
                for(int w0= -SCANSIZE/2;w0<SCANSIZE/2;w0++){
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(h0, w0)),inbounds), 0).rg,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(h0, w0)),inbounds), 0).rg, 0.1);
                }
            }*/

                    dist=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(-1, -1)),inbounds), 0).rg,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(-1, -1)),inbounds), 0).rg, 0.1);
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(1, -1)),inbounds), 0).rg,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(1, -1)),inbounds), 0).rg, 0.1);
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(-1, 1)),inbounds), 0).rg,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(-1, 1)),inbounds), 0).rg, 0.1);
                    dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(1, 1)),inbounds), 0).rg,
                    texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(1, 1)),inbounds), 0).rg, 0.1);
                    for (int t=-SCANSIZE/2;t<SCANSIZE/2;t++){
                        float dist2 = 1.0+3.0*abs(float(t)/float(SCANSIZE));
                        dist+=dist2*distribute(cachey[t+SCANSIZE/2],
                        texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(0, t)),inbounds), 0).rg, 0.1);
                        dist+=dist2*distribute(cachex[t+SCANSIZE/2],
                        texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(t, 0)),inbounds), 0).rg, 0.1);
                    }

                    dist*=dist3;
                    /*if (dist < 0.15){
                        outalign = shift;
                        Output = vec3(outalign.x,outalign.y,0)/float(TILESIZE*16);
                        Output.b = dist;
                        return;*/
                    //} else {
                    if(dist.r+dist.g < mindist.r+mindist.g){
                            mindist = dist;
                            outalign = shift+OFFSET;
                    }
                    //}
        }
    }

    Output = vec2(outalign.x,outalign.y);

    //Output.r = texelFetch(InputBuffer, xy, 0).r;

    //Output.b = (abs(cachex[SCANSIZE/2]-texelFetch(InputBuffer, mirrorCoords((xyFrame+outalign),inbounds), 0).a));
    Output/=float(TILESIZE*8);
    Output=vec2(0.5)-Output;
    //Output = vec2(TILESIZE,TILESIZE);
}
