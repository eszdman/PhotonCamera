#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform sampler2D AlignVectors;
uniform sampler2D PrevVectors;
uniform ivec2 xyshift;
uniform int prevLayerScale;
uniform int yOffset;
out vec3 Output;
#define TILESIZE (48)
#define TILESCALE (TILESIZE/2)
#define FLT_MAX 3.402823466e+38
#define distribute(x,dev) ((x-dev)*(x-dev))
#import coords
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 prevAlign = ivec2(0,0);
    if (prevLayerScale != 0) {
        prevAlign = ivec2((vec2(0.5)-texelFetch(AlignVectors, xy / prevLayerScale, 0).rg)*float(TILESIZE*8))*prevLayerScale;
    }
    Output = texelFetch(PrevVectors, xy, 0).rgb;
    float prevDist = Output.b;
    if(prevDist == 0.0) prevDist = FLT_MAX;
    //ivec2 xyFrame = ivec2(gl_FragCoord.xy*float(TILESCALE));
    vec2 dist = vec2(0.0);
    ivec4 inbounds = ivec4(0,0,ivec2(textureSize(InputBuffer, 0)));
    ivec2 xyShifted = xyshift;
    dist = distribute(texelFetch(MainBuffer, mirrorCoords((xy), inbounds), 0).rg,
    texelFetch(InputBuffer, mirrorCoords((xy+xyShifted), inbounds), 0).rg);
    if(dist.r+dist.g < prevDist) {
        //Output.b = dist.r+dist.b;
        Output.rg = vec2(prevAlign+xyShifted);
        Output.rg/=float(TILESIZE*8);
        Output.rg=vec2(0.5)-Output.rg;
    }
}
