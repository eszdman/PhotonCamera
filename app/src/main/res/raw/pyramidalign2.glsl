#version 300 es
precision highp float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D InputBufferH;
uniform sampler2D MainBufferH;
uniform sampler2D InputBufferV;
uniform sampler2D MainBufferV;
uniform sampler2D MainBuffer;
uniform sampler2D InputBuffer;
uniform usampler2D AlignVectors;
uniform int yOffset;
out uvec3 Output;
#define TILESIZE (48)
#define SCANSIZE (48)
#define TILESCALE (TILESIZE/2)
#define PREVSCALE (2)
#define INPUTSIZE 1,1
#define LUCKYINPUT 0
#define OFFSET (0)
#define MAXMP (2)
#define MAXMOVE ((TILESCALE/2)+1)
#define FLT_MAX 3.402823466e+38
#define M_PI 3.1415926535897932384626433832795

//#define distribute(x,dev,sigma) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))
#define distribute(x,dev,sigma) (abs(x-dev))
//#define distribute(x,dev,sigma) ((x-dev)*(x-dev))
//#define getVal(somevec) (somevec.r+somevec.g+somevec.b-somevec.a)
#import coords
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 prevAlign = ivec2(0,0);
    #if PREVSCALE != 0

    uvec3 bestAlign = texelFetch(AlignVectors, (xy+ivec2(0,0)) / PREVSCALE, 0).rgb;
    uvec3 inp;

    #if LUCKYINPUT == 1
    inp = texelFetch(AlignVectors, (xy+ivec2(0,-1)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    inp = texelFetch(AlignVectors, (xy+ivec2(0,1)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    inp = texelFetch(AlignVectors, (xy+ivec2(-1,0)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    inp = texelFetch(AlignVectors, (xy+ivec2(1,0)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    #endif

    prevAlign = ivec2(ivec2(bestAlign.rg)-16384)*PREVSCALE;

    #endif

    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESCALE));
    vec2 dist = vec2(0.0);
    vec2 mindist = vec2(FLT_MAX);
    ivec2 outalign = ivec2(0,0);
    ivec2 shift = ivec2(0,0);
    ivec4 inbounds = ivec4(0,0,ivec2(INPUTSIZE));
    vec2 in1;
    vec2 in2;
    vec2 cachex[SCANSIZE+1];
    vec2 cachey[SCANSIZE+1];
    for (int i=-SCANSIZE/2;i<SCANSIZE/2;i++){
        cachex[i+SCANSIZE/2] = texelFetch(MainBufferH, mirrorCoords((xyFrame+ivec2(i, 0)),inbounds), 0).rg;
        cachey[i+SCANSIZE/2] = texelFetch(MainBufferV, mirrorCoords((xyFrame+ivec2(0, i)),inbounds), 0).rg;
    }
    for(int h = -MAXMOVE;h<MAXMOVE;h++){
        vec2 disth = vec2(0.0);
        shift = ivec2(0, h)+prevAlign-OFFSET;
        for (int t=-SCANSIZE/2;t<SCANSIZE/2;t++){
            float dist2 = 1.0+4.0*abs(float(t)/float(SCANSIZE));
            in2 = texelFetch(InputBufferH, mirrorCoords((xyFrame+shift+ivec2(t, 0)),inbounds), 0).rg;
            disth+=dist2*
            distribute(cachex[t+SCANSIZE/2],in2, 0.1);
        }
        for(int w = -MAXMOVE;w<MAXMOVE;w++){
            dist = disth;
            float dist3 = 1.5+abs(float(w)/float(MAXMOVE))+abs(float(h)/float(MAXMOVE));
            shift = ivec2(w, h)+prevAlign-OFFSET;
            /*for(int h0= -SCANSIZE/2;h0<SCANSIZE/2;h0++){
                for(int w0= -SCANSIZE/2;w0<SCANSIZE/2;w0++){
                        dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(h0, w0)), inbounds), 0).rg,
                        texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(h0, w0)), inbounds), 0).rg, 0.1);
                }
            }*/
            /*in1 = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(-1, -1)),inbounds), 0).rg;
            in2 = texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(-1, -1)),inbounds), 0).rg;
            dist+=distribute(in1,in2, 0.1);
            in1 = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(1, -1)),inbounds), 0).rg;
            in2 = texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(1, -1)),inbounds), 0).rg;
            dist+=distribute(in1,in2, 0.1);
            in1 = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(-1, 1)),inbounds), 0).rg;
            in2 = texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(-1, 1)),inbounds), 0).rg;
            dist+=distribute(in1,in2, 0.1);
            in1 = texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(1, 1)),inbounds), 0).rg;
            in2 = texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(1, 1)),inbounds), 0).rg;
            dist+=distribute(in1,in2, 0.1);*/
            //dist*=2.5;

            /*for (int t=-SCANSIZE/2;t<SCANSIZE/2;t++){
                float dist2 = 1.0+6.0*abs(float(t)/float(SCANSIZE));
                in2 = texelFetch(InputBufferH, mirrorCoords((xyFrame+shift+ivec2(0, t)),inbounds), 0).rg;
                dist+=//dist2*
                distribute(cachey[t+SCANSIZE/2],in2, 0.1);
                in2 = texelFetch(InputBufferV, mirrorCoords((xyFrame+shift+ivec2(t, 0)),inbounds), 0).rg;
                dist+=//dist2*
                distribute(cachex[t+SCANSIZE/2],in2, 0.1);
            }*/
            for (int t=-SCANSIZE/2;t<SCANSIZE/2;t++){
                float dist2 = 1.0+4.0*abs(float(t)/float(SCANSIZE));

                in2 = texelFetch(InputBufferV, mirrorCoords((xyFrame+shift+ivec2(0, t)),inbounds), 0).rg;
                dist+=dist2*
                distribute(cachey[t+SCANSIZE/2],in2, 0.1);

                //in2 = texelFetch(InputBufferH, mirrorCoords((xyFrame+shift+ivec2(t, 0)),inbounds), 0).rg;
                //dist+=dist2*
                //distribute(cachex[t+SCANSIZE/2],in2, 0.1);
            }
            in2 = texelFetch(InputBufferH, mirrorCoords((xyFrame+shift),inbounds), 0).rg;
            dist+=
            distribute(cachex[SCANSIZE/2],in2, 0.1);

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
    dist = vec2(0.0);
    for(int h0= -SCANSIZE/2;h0<SCANSIZE/2;h0++){
        for(int w0= -SCANSIZE/2;w0<SCANSIZE/2;w0++){
            dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(h0, w0)), inbounds), 0).rg,
            texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(h0, w0)), inbounds), 0).rg, 0.1);
        }
    }

    Output.rg = uvec2(16384-outalign.x,16384-outalign.y);

    Output.b = uint((dist.r+dist.g)*8192.0)+uint(1);
    #if PREVSCALE != 0
    //Output.b += bestAlign.b;
    #endif
    //Output.r = texelFetch(InputBuffer, xy, 0).r;

    //Output.b = (abs(cachex[SCANSIZE/2]-texelFetch(InputBuffer, mirrorCoords((xyFrame+outalign),inbounds), 0).a));
    //Output/=float(TILESIZE*8);
    //Output=vec2(0.5)-Output;
    //Output=vec2(1.0)/(Output+0.01);
    //Output = vec2(TILESIZE,TILESIZE);
}
