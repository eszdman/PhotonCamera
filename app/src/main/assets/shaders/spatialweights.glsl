
precision mediump float;
precision mediump sampler2D;
precision mediump isampler2D;
uniform sampler2D CornersRef;
uniform sampler2D CornersIn;

uniform isampler2D AlignVectors;
#define distribute(x,dev,sigma) (abs(x-dev))
#define MIN_NOISE 0.1f
#define MAX_NOISE 0.7f
#define TILESIZE 32
#define WEIGHTSIZE 128
#define FRAMECOUNT 15
#define INPUTSIZE 1,1
#import coords
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESIZE/2));
    vec2 dist = vec2(0.0);
    ivec2 shift = ivec2(texelFetch(AlignVectors,(xy), 0).rg);
    vec2 in2;
    for (int i=-WEIGHTSIZE/2;i<WEIGHTSIZE/2;i++){
        in2 = texelFetch(CornersIn, mirrorCoords2((xyFrame+shift+ivec2(i, 0)),ivec2(INPUTSIZE)), 0).rg;
        dist+= distribute(texelFetch(CornersRef, mirrorCoords2((xyFrame+ivec2(i, 0)),ivec2(INPUTSIZE)), 0).rg, in2, 0.1);
        in2 = texelFetch(CornersIn, mirrorCoords2((xyFrame+shift+ivec2(0, i)),ivec2(INPUTSIZE)), 0).rg;
        dist+= distribute(texelFetch(CornersRef, mirrorCoords2((xyFrame+ivec2(0, i)),ivec2(INPUTSIZE)), 0).rg, in2, 0.1);
    }
    //dist += ((float(texelFetch(AlignVectors, xy, 0).b)/1024.0));
    Output = ((dist.r+dist.g)/float(TILESIZE*TILESIZE*FRAMECOUNT/30));
    Output /=30.0;
    //Output = ((float(texelFetch(AlignVectors, xy, 0).b)/1024.0))/float(FRAMECOUNT) + 0.25;
}
