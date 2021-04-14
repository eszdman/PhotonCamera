#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D MainBuffer;
uniform sampler2D InputBuffer;
uniform usampler2D AlignVectors;
#define distribute(x,dev,sigma) (abs(x-dev))
#define MIN_NOISE 0.1f
#define MAX_NOISE 0.7f
#define TILESIZE (32)
#define FRAMECOUNT 15
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESIZE/2));
    vec2 dist = vec2(0.0);
    ivec2 shift = ivec2(ivec2(texelFetch(AlignVectors,(xy), 0).rg)-ivec2(16384));
    vec2 in2;
    for (int i=-TILESIZE/3;i<TILESIZE/3;i++){
        for (int j=-TILESIZE/3;j<TILESIZE/3;j++){
            in2 = texelFetch(InputBuffer, ((xyFrame+shift+ivec2(i, j))), 0).rg;
            dist+= distribute(texelFetch(MainBuffer, ((xyFrame+ivec2(i, j))), 0).rg, in2, 0.1);
        }
    }
    dist += ((float(texelFetch(AlignVectors, xy, 0).b)/1024.0));
    Output = ((dist.r+dist.g)/float(FRAMECOUNT))+0.25;
    //Output = ((float(texelFetch(AlignVectors, xy, 0).b)/1024.0))/float(FRAMECOUNT) + 0.25;
}
