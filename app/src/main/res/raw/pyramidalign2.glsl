#version 300 es
precision highp float;
precision mediump sampler2D;
precision mediump isampler2D;
//uniform sampler2D InputBufferH;
//uniform sampler2D MainBufferH;

uniform sampler2D CornersRef;
uniform sampler2D DiffHVIn;
uniform sampler2D DiffHVRef;
uniform sampler2D MainBuffer;
uniform sampler2D InputBuffer;
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
#define INITIALMOVE 0,0
#define MAXMOVE (4)
#define SHARPMOVE (16)
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
    #else
    prevAlign = ivec2(INITIALMOVE);
    #endif

    ivec2 inbounds = ivec2(INPUTSIZE);
    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESCALE));
    ivec2 prevShift = ivec2(bestAlign.ba*PREVSCALE);
    ivec2 shiftFrame = ivec2(0,0);
    //Shift frame coords to most sharp region
    vec2 dist;
    float mindist = 0.0;
    /*
    for(int w = -SHARPMOVE;w<=SHARPMOVE;w++){
        float dist3 = 3.0+abs(float(w)/float(SHARPMOVE));
        dist = vec2(0,0);
        for(int sh = -SCANSIZE/5;sh<=SCANSIZE/5; sh++){
            dist.g+=texelFetch(CornersIn, mirrorCoords2((xyFrame+ivec2(w, sh)),inbounds), 0).g;
        }
        dist*=dist3;
        if(dist.r + dist.g > mindist){
            mindist = dist.r + dist.g;
            shiftFrame.x = w;
        }
    }
    shiftFrame.x+=prevShift.x;
    xyFrame.x+=shiftFrame.x;
    mindist = 0.0;
    for(int h = -SHARPMOVE;h<=SHARPMOVE;h++){
        float dist3 = 3.0+abs(float(h)/float(SHARPMOVE));
        dist = vec2(0,0);
        for(int sw = -SCANSIZE/5;sw<=SCANSIZE/5; sw++){
            dist.r+=texelFetch(CornersIn, mirrorCoords2((xyFrame+ivec2(sw, h)),inbounds), 0).r;
        }
        dist*=dist3;
        if(dist.r + dist.g > mindist){
            mindist = dist.r + dist.g;
            shiftFrame.y = h;
        }
    }*/
    for(int h = -SHARPMOVE;h<=SHARPMOVE;h++){
        for(int w = -SHARPMOVE;w<=SHARPMOVE;w++){
            float dist3 = 3.0+abs(float(h)/float(SHARPMOVE))+abs(float(w)/float(SHARPMOVE));
            dist = texelFetch(CornersRef, mirrorCoords2((xyFrame+ivec2(w, h)), inbounds), 0).rg;
            dist/=dist3;
            if (dist.r + dist.g > mindist){
                mindist = dist.r + dist.g;
                shiftFrame.y = h;
                shiftFrame.x = w;
            }
        }
    }
    mindist = 0.0;
    vec2 in2;
    shiftFrame.x+=prevShift.x;
    xyFrame.x+=shiftFrame.x;

    shiftFrame.y+=prevShift.y;
    xyFrame.y+=shiftFrame.y;

    dist = vec2(0,0);


    int outx = 0;
    int outy = 0;
    ivec2 shift = ivec2(0,0);
    ivec2 shift2 = ivec2(0,0);

    /*vec2 cachex[SCANSIZE+2];
    vec2 cachey[SCANSIZE+2];
    for (int i=-SCANSIZE/2;i<=SCANSIZE/2;i++){
        cachex[i+SCANSIZE/2] = texelFetch(MainBufferH, mirrorCoords2((xyFrame+ivec2(i, 0)),inbounds), 0).rg;
        cachey[i+SCANSIZE/2] = texelFetch(MainBufferV, mirrorCoords2((xyFrame+ivec2(0, i)),inbounds), 0).rg;
    }*/
    float dist3 = 0.0;
    float dist2 = 0.0;
    mindist = float(FLT_MAX);
    float mindist2 = float(FLT_MAX);
    /*float cnt = 0.0;
    for(int k =0;k<=1;k++)
    for (int t=-SCANSIZE/2;t<=SCANSIZE/2;t++){
        dist2 = 1.0+5.0*abs(float(t)/float(SCANSIZE));
        vec2 dxy;
        dxy.r = texelFetch(DiffHVRef, mirrorCoords2((xyFrame+ivec2(k, t)),inbounds), 0).r;
        dist3 = (texelFetch(MainBuffer, mirrorCoords2((xyFrame+ivec2(k, t)),inbounds), 0).r +
        texelFetch(MainBuffer, mirrorCoords2((xyFrame+ivec2(k, t)),inbounds), 0).g);
        dist3-= (texelFetch(InputBuffer, mirrorCoords2((xyFrame+prevAlign+ivec2(k, t)),inbounds), 0).r+
        texelFetch(InputBuffer, mirrorCoords2((xyFrame+prevAlign+ivec2(k, t)),inbounds), 0).g);
        dist3/= (dxy.r
        -texelFetch(DiffHVIn, mirrorCoords2((xyFrame+prevAlign+ivec2(k, t)),inbounds), 0).r
        )/2.0;
        dist.r+=dist3;
        dxy.g = texelFetch(DiffHVRef, mirrorCoords2((xyFrame+ivec2(t, k)),inbounds), 0).g;
        dist3 = (texelFetch(MainBuffer, mirrorCoords2((xyFrame+ivec2(t, k)),inbounds), 0).r +
        texelFetch(MainBuffer, mirrorCoords2((xyFrame+ivec2(t, k)),inbounds), 0).g);
        dist3-= (texelFetch(InputBuffer, mirrorCoords2((xyFrame+prevAlign+ivec2(t, k)),inbounds), 0).r+
        texelFetch(InputBuffer, mirrorCoords2((xyFrame+prevAlign+ivec2(t, k)),inbounds), 0).g);
        dist3/= (dxy.g
        -texelFetch(DiffHVIn, mirrorCoords2((xyFrame+prevAlign+ivec2(t, k)),inbounds), 0).g
        )/2.0;
        dist.g+=dist3;
        cnt+=1.0;
    }
    dist/=cnt;
    prevAlign.y+=int(dist.r+0.5);
    prevAlign.x+=int(dist.g+0.5);*/

    for(int h = -MAXMOVE;h<=MAXMOVE;h++){
        dist = vec2(0.0);
        shift = ivec2(0, h)+prevAlign-OFFSET;
        shift2 = ivec2(h, 0)+prevAlign-OFFSET;
        dist3 = 1.0+abs(float(h)/float(MAXMOVE));
        for(int k =-1;k<=1;k++)
        for (int t=-SCANSIZE/2;t<=SCANSIZE/2;t++){
            dist2 = 1.0+5.0*abs(float(t)/float(SCANSIZE));
            in2 = texelFetch(DiffHVIn, mirrorCoords2((xyFrame+shift+ivec2(t, k)),inbounds), 0).rg;
            float inputf = in2.r;
            //in2 = texelFetch(InputBufferH, mirrorCoords2((xyFrame+shift+ivec2(t, 0)),inbounds), 0).rg;
            //in2.r = in2.r+in2.g;
            in2 = texelFetch(DiffHVRef, mirrorCoords2((xyFrame+ivec2(t, k)),inbounds), 0).rg;
            dist.y+=distribute(in2.r,inputf, 0.1)/dist2;

            in2 = texelFetch(DiffHVIn, mirrorCoords2((xyFrame+shift2+ivec2(k, t)),inbounds), 0).rg;
            inputf = in2.g;
            //inputf = texelFetch(InputBufferV, mirrorCoords2((xyFrame+shift2+ivec2(0, t)),inbounds), 0).r;
            //in2 = texelFetch(InputBufferH, mirrorCoords2((xyFrame+shift2+ivec2(0, t)),inbounds), 0).rg;
            //in2.r = in2.r+in2.g;
            in2 = texelFetch(DiffHVRef, mirrorCoords2((xyFrame+ivec2(k, t)),inbounds), 0).rg;
            dist.x+=distribute(in2.g,inputf, 0.1)/dist2;
        }
        dist*=dist3;
        if(dist.x < mindist2){
            mindist2 = dist.x;
            outx = h;
        }
        if(dist.y < mindist){
            mindist = dist.y;
            outy = h;
        }
    }

    //mindist = float(FLT_MAX);

    /*for(int w = -MAXMOVE;w<=MAXMOVE;w++){
        //dist = vec2(0.0);
        dist3 = 1.0+abs(float(w)/float(MAXMOVE));
        shift = ivec2(w, 0)+prevAlign-OFFSET;
        for (int t=-SCANSIZE/2;t<=SCANSIZE/2;t++){
            dist2 = 1.0+5.0*abs(float(t)/float(SCANSIZE));
            in2 = texelFetch(InputBufferV, mirrorCoords2((xyFrame+shift+ivec2(0, t)),inbounds), 0).rg;
            dist+=
            distribute(cachey[t+SCANSIZE/2],in2, 0.1);
        }
        //dist/=dist3;
        if((dist.r+dist.g) < mindist){
            mindist = (dist.r+dist.g);
            outalign.x = w;
        }
    }*/

    Output = ivec4(outx+prevAlign.x,outy+prevAlign.y,shiftFrame.x,shiftFrame.y);

    #if PREVSCALE != 0
    //Output.b += bestAlign.b;
    #endif
}
