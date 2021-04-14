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
#define MAXMOVE (4)
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
    uvec3 bestAlign;
    #if PREVSCALE != 0

    bestAlign = texelFetch(AlignVectors, (xy) / PREVSCALE, 0).rgb;
    uvec3 inp;

    #if LUCKYINPUT == 1

    /*inp = texelFetch(AlignVectors, (xy+ivec2(0,-1)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    inp = texelFetch(AlignVectors, (xy+ivec2(0,1)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    inp = texelFetch(AlignVectors, (xy+ivec2(-1,0)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    inp = texelFetch(AlignVectors, (xy+ivec2(1,0)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;

    inp = texelFetch(AlignVectors, (xy+ivec2(-1,-1)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    inp = texelFetch(AlignVectors, (xy+ivec2(1,1)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    inp = texelFetch(AlignVectors, (xy+ivec2(-1,1)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;
    inp = texelFetch(AlignVectors, (xy+ivec2(1,-1)) / PREVSCALE, 0).rgb;
    if(bestAlign.b < inp.b && inp.b != uint(0)) bestAlign = inp;*/
    uvec2[9] medin;
    medin[0] = texelFetch(AlignVectors, (xy+ivec2(0,0)) / PREVSCALE, 0).rg;
    medin[1] = texelFetch(AlignVectors, (xy+ivec2(0,-1)) / PREVSCALE, 0).rg;
    medin[2] = texelFetch(AlignVectors, (xy+ivec2(-1,0)) / PREVSCALE, 0).rg;
    medin[3] = texelFetch(AlignVectors, (xy+ivec2(0,1)) / PREVSCALE, 0).rg;
    medin[4] = texelFetch(AlignVectors, (xy+ivec2(1,0)) / PREVSCALE, 0).rg;
    medin[5] = texelFetch(AlignVectors, (xy+ivec2(-1,-1)) / PREVSCALE, 0).rg;
    medin[6] = texelFetch(AlignVectors, (xy+ivec2(-1,1)) / PREVSCALE, 0).rg;
    medin[7] = texelFetch(AlignVectors, (xy+ivec2(1,-1)) / PREVSCALE, 0).rg;
    medin[8] = texelFetch(AlignVectors, (xy+ivec2(1,1)) / PREVSCALE, 0).rg;
    bestAlign.rg = median9(medin);


    #endif

    prevAlign = ivec2(ivec2(bestAlign.rg)-16384)*PREVSCALE;
    #endif


    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESCALE));
    vec2 dist = vec2(0.0);
    vec2 dsize;
    vec2 mindist = vec2(FLT_MAX);
    ivec2 outalign = ivec2(0,0);
    ivec2 shift = ivec2(0,0);
    ivec2 inbounds = ivec2(INPUTSIZE);
    vec2 in1;
    vec2 in2;
    vec2 cachex[SCANSIZE+1];
    vec2 cachey[SCANSIZE+1];
    for (int i=-SCANSIZE/2;i<SCANSIZE/2;i++){
        cachex[i+SCANSIZE/2] = texelFetch(MainBufferH, mirrorCoords2((xyFrame+ivec2(i, 0)),inbounds), 0).rg;
        cachey[i+SCANSIZE/2] = texelFetch(MainBufferV, mirrorCoords2((xyFrame+ivec2(0, i)),inbounds), 0).rg;
    }
    vec2 disth = vec2(0.0);
    for(int h = -MAXMOVE;h<MAXMOVE;h++){
        disth = vec2(0.0);
        dsize = vec2(1.0);
        shift = ivec2(0, h)+prevAlign-OFFSET;
        float dist3 = 3.5+abs(float(h)/float(MAXMOVE));
        for (int t=-SCANSIZE/2;t<SCANSIZE/2;t++){
            float dist2 = 1.0+2.0*abs(float(t)/float(SCANSIZE));
            in2 = texelFetch(InputBufferV, mirrorCoords2((xyFrame+shift+ivec2(0, t)),inbounds), 0).rg;
            dsize+=abs(in2);
            disth+=
            distribute(cachey[t+SCANSIZE/2],in2, 0.1)/dist2;
        }
        disth*=dist3;
        //dist/=dsize;
        if(disth.r+disth.g < mindist.r+mindist.g){
            mindist = disth;
            outalign.g = h+prevAlign.g;
        }
    }
    disth = mindist;
    mindist = vec2(FLT_MAX);
    for(int w = -MAXMOVE;w<MAXMOVE;w++){
        dist = disth;
        float dist3 = 3.5+abs(float(w)/float(MAXMOVE));
        shift = ivec2(w, 0)+prevAlign-OFFSET;
        dsize = vec2(1.0);
        for (int t=-SCANSIZE/2;t<SCANSIZE/2;t++){
            float dist2 = 1.0+2.0*abs(float(t)/float(SCANSIZE));
            in2 = texelFetch(InputBufferH, mirrorCoords2((xyFrame+shift+ivec2(t, 0)),inbounds), 0).rg;
            dist+=
            distribute(cachex[t+SCANSIZE/2],in2, 0.1)/dist2;
        }
        dist*=dist3;
        //dist/=dsize;
        if(dist.r+dist.g < mindist.r+mindist.g){
            mindist = dist;
            outalign.r = w+prevAlign.r;
        }
    }
    /*dist = vec2(0.0);
    for(int h0= -SCANSIZE/2;h0<SCANSIZE/2;h0++){
        for(int w0= -SCANSIZE/2;w0<SCANSIZE/2;w0++){
            dist+=distribute(texelFetch(MainBuffer, mirrorCoords((xyFrame+ivec2(h0, w0)), inbounds), 0).rg,
            texelFetch(InputBuffer, mirrorCoords((xyFrame+shift+ivec2(h0, w0)), inbounds), 0).rg, 0.1);
        }
    }*/
    Output.rg = uvec2(outalign.x+16384,outalign.y+16384);

    dist+=float(bestAlign.b)/1024.0;
    Output.b = uint(clamp(dist.r+dist.g,0.0,31.0)*1024.0)+uint(1);
    #if PREVSCALE != 0
    //Output.b += bestAlign.b;
    #endif
}
