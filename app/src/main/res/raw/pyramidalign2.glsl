#version 300 es
precision highp float;
precision mediump sampler2D;
precision mediump isampler2D;
uniform sampler2D InputBufferH;
uniform sampler2D MainBufferH;
uniform sampler2D InputBufferV;
uniform sampler2D MainBufferV;

uniform sampler2D LowPassV;
uniform sampler2D LowPassH;
//uniform sampler2D MainBuffer;
//uniform sampler2D InputBuffer;
uniform isampler2D AlignVectors;
uniform int yOffset;
out ivec4 Output;
#define TILESIZE (48)
#define SCANSIZE (48)
#define TILESCALE (TILESIZE/2)
#define PREVSCALE (2)
#define INPUTSIZE 1,1
#define LUCKYINPUT 0
#define LOWPASSCOMBINE 0
#define LOWPASSK 4
#define OFFSET (0)
#define MAXMOVE (4)
#define SHARPMOVE (6)
#define FLT_MAX 3.402823466e+38
#define M_PI 3.1415926535897932384626433832795

//#define distribute(x,dev,sigma) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))
#define distribute(x,dev,sigma) (abs(x-dev))
//#define distribute(x,dev,sigma) ((x-dev)*(x-dev))
//#define getVal(somevec) (somevec.r+somevec.g+somevec.b-somevec.a)
#import coords
#import median
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 prevAlign = ivec2(0,0);
    ivec4 bestAlign;
    ivec2 alignvecSize = ivec2(textureSize(AlignVectors, 0));
    #if PREVSCALE != 0

    bestAlign = texelFetch(AlignVectors, (xy) / PREVSCALE, 0).rgba;

    #if LUCKYINPUT == 1

    ivec2[9] medin;
    medin[0] = texelFetch(AlignVectors, mirrorCoords2((xy)/PREVSCALE+ivec2(0,0),alignvecSize), 0).rg;
    medin[1] = texelFetch(AlignVectors, mirrorCoords2((xy/PREVSCALE+ivec2(0,-1)),alignvecSize), 0).rg;
    medin[2] = texelFetch(AlignVectors, mirrorCoords2((xy/PREVSCALE+ivec2(-1,0)),alignvecSize), 0).rg;
    medin[3] = texelFetch(AlignVectors, mirrorCoords2((xy/PREVSCALE+ivec2(0,1)),alignvecSize), 0).rg;
    medin[4] = texelFetch(AlignVectors, mirrorCoords2((xy/PREVSCALE+ivec2(1,0)),alignvecSize), 0).rg;
    medin[5] = texelFetch(AlignVectors, mirrorCoords2((xy)/PREVSCALE+ivec2(-1,-1),alignvecSize), 0).rg;
    medin[6] = texelFetch(AlignVectors, mirrorCoords2((xy)/PREVSCALE+ivec2(-1,1),alignvecSize), 0).rg;
    medin[7] = texelFetch(AlignVectors, mirrorCoords2((xy)/PREVSCALE+ivec2(1,-1),alignvecSize), 0).rg;
    medin[8] = texelFetch(AlignVectors, mirrorCoords2((xy)/PREVSCALE+ivec2(1,1),alignvecSize), 0).rg;

    bestAlign.xy = median9(medin);


    #endif

    prevAlign = ivec2(bestAlign.xy)*PREVSCALE;
    #endif

    ivec2 inbounds = ivec2(INPUTSIZE);
    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESCALE));
    ivec2 prevShift = ivec2(bestAlign.ba*PREVSCALE);
    ivec2 shiftFrame = ivec2(0,0);
    //Shift frame coords to most sharp region
    vec2 dist;
    float mindist = 0.0;
    for(int w = -SHARPMOVE;w<SHARPMOVE;w++){
        float dist3 = 3.0+abs(float(w)/float(SHARPMOVE));
        dist = vec2(0,0);
        for(int sh = -SCANSIZE/2;sh<SCANSIZE/2; sh++){
            dist+=texelFetch(MainBufferV, mirrorCoords2((xyFrame+ivec2(w, sh)),inbounds), 0).rg;
        }
        dist*=dist3;
        if(dist.r + dist.g > mindist){
            mindist = dist.r + dist.g;
            shiftFrame.x = w+prevShift.x;
        }
    }
    xyFrame.x+=shiftFrame.x;
    mindist = 0.0;
    for(int h = -SHARPMOVE;h<SHARPMOVE;h++){
        float dist3 = 3.0+abs(float(h)/float(SHARPMOVE));
        dist = vec2(0,0);
        for(int sw = -SCANSIZE/2;sw<SCANSIZE/2; sw++){
            dist+=texelFetch(MainBufferH, mirrorCoords2((xyFrame+ivec2(sw, h)),inbounds), 0).rg;
        }
        dist*=dist3;
        if(dist.r + dist.g > mindist){
            mindist = dist.r + dist.g;
            shiftFrame.y = h+prevShift.y;
        }
    }
    dist = vec2(0,0);
    mindist = float(FLT_MAX);


    xyFrame.y+=shiftFrame.y;
    ivec2 outalign = ivec2(0,0);
    ivec2 shift = ivec2(0,0);
    vec2 in1;
    vec2 in2;
    vec2 cachex[SCANSIZE];
    vec2 cachey[SCANSIZE];
    for (int i=-SCANSIZE/2;i<SCANSIZE/2;i++){
        cachex[i+SCANSIZE/2] = texelFetch(MainBufferH, mirrorCoords2((xyFrame+ivec2(i, 0)),inbounds), 0).rg;
        cachey[i+SCANSIZE/2] = texelFetch(MainBufferV, mirrorCoords2((xyFrame+ivec2(0, i)),inbounds), 0).rg;
        #if LOWPASSCOMBINE == 1
        cachex[i+SCANSIZE/2] *= texelFetch(LowPassH, mirrorCoords2((xyFrame+ivec2(i, 0)),inbounds)/LOWPASSK, 0).rg;
        cachey[i+SCANSIZE/2] *= texelFetch(LowPassV, mirrorCoords2((xyFrame+ivec2(0, i)),inbounds)/LOWPASSK, 0).rg;
        #endif
    }
    vec2 disth = vec2(0.0);
    for(int h = -MAXMOVE;h<MAXMOVE;h++){
        disth = vec2(0.0);
        shift = ivec2(0, h)+prevAlign-OFFSET;
        float dist3 = 6.0+abs(float(h)/float(MAXMOVE));
        for (int t=-SCANSIZE/2;t<SCANSIZE/2;t++){
            float dist2 = 1.0+2.0*abs(float(t)/float(SCANSIZE));
            in2 = texelFetch(InputBufferH, mirrorCoords2((xyFrame+shift+ivec2(t, 0)),inbounds), 0).rg;
            #if LOWPASSCOMBINE == 1
            in2 *= texelFetch(LowPassH, mirrorCoords2((xyFrame+shift+ivec2(t, 0)),inbounds)/LOWPASSK, 0).rg;
            #endif
            disth+=
            distribute(cachex[t+SCANSIZE/2],in2, 0.1);///dist2;
        }
        disth*=dist3;
        if((disth.r+disth.g) < mindist){
            mindist = (disth.r+disth.g);
            outalign.y = h;
        }
    }
    mindist = float(FLT_MAX);
    for(int w = -MAXMOVE;w<MAXMOVE;w++){
        dist = vec2(0.0);
        float dist3 = 6.0+abs(float(w)/float(MAXMOVE));
        shift = ivec2(w, outalign.y)+prevAlign-OFFSET;
        for (int t=-SCANSIZE/2;t<SCANSIZE/2;t++){
            float dist2 = 1.0+2.0*abs(float(t)/float(SCANSIZE));
            in2 = texelFetch(InputBufferV, mirrorCoords2((xyFrame+shift+ivec2(0, t)),inbounds), 0).rg;
            #if LOWPASSCOMBINE == 1
            in2 *= texelFetch(LowPassV, mirrorCoords2((xyFrame+shift+ivec2(0, t)),inbounds)/LOWPASSK, 0).rg;
            #endif
            dist+=
            distribute(cachey[t+SCANSIZE/2],in2, 0.1);///dist2;
        }
        dist*=dist3;
        if((dist.r+dist.g) < mindist){
            mindist = (dist.r+dist.g);
            outalign.x = w;
        }
    }
    Output.rg = ivec2(outalign.x+prevAlign.x,outalign.y+prevAlign.y);

    Output.ba = shiftFrame;
    #if PREVSCALE != 0
    //Output.b += bestAlign.b;
    #endif
}
