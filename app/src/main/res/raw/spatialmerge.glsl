#version 300 es
precision highp float;
precision highp sampler2D;
precision mediump isampler2D;
precision mediump usampler2D;
uniform sampler2D OutputBuffer;

uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;


uniform sampler2D SumWeights;
uniform sampler2D Weight;
uniform usampler2D AlignVectors;
uniform int yOffset;
uniform float alignk;
uniform uvec2 rawsize;
uniform uvec2 alignsize;
uniform uvec2 weightsize;
uniform int CfaPattern;
uniform int number;
out float Output;
#define MIN_NOISE 0.1f
#define MAX_NOISE 1.0f
#define TILESIZE (48)
#import interpolation
#import coords
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    //ivec2 align = ivec2(texelFetch(AlignVectors, (xy/TILESIZE), 0).xy);
    //vec2 xyInterp = vec2(xy)/float(TILESIZE);
    //xyInterp/=vec2(alignsize);
    //xyInterp = vec2(xy)/float(TILESIZE);

    //xyInterp/=vec2(weightsize);
    //float weight = float(texture(SpatialWeights, xyInterp).x);
    //float inp = boxdown22(aligned/2,InputBuffer22)/4.0;
    //float target = boxdown22(xy/2,MainBuffer22)/4.0;
    //float weight = abs(inp-target);
    //float weight = smoothstep(MIN_NOISE, MAX_NOISE, abs(inp-target));
    //float weight = float(texelFetch(SpatialWeights, ivec2(xyInterp), 0).x);

    //alignf/=float(TILESIZE*2);

    //float dist2 = alignf.x*alignf.x+alignf.y*alignf.y;
    //float dist2 = smoothstep(0.0,2.0,abs(alignf.x)+abs(alignf.y));
    //float windoww = 1.0 - (weight)*5.6 - 0.4;
    float sumweights = texelFetch(SumWeights, (xy/(TILESIZE*2)), 0).r + 1.0;
    float windoww = 1.0;
    windoww = texelFetch(Weight, (xy/(TILESIZE*2)), 0).r/sumweights;
    windoww = clamp(windoww,0.0,1.0);
    //windoww = clamp(windoww,0.00,1.0);

    //windoww = float(int(windoww*100.0))/100.0;
    //float outp = (((texelFetch(InputBuffer, aligned, 0).x))*float((windoww))+((texelFetch(OutputBuffer, (xy), 0).x))*float((1.0-windoww)));
    ivec2 outsize = ivec2(textureSize(OutputBuffer, 0));
    ivec2 state = xy%2;
    //vec2 inp = (vec2(0.5)-textureBicubicHardware(AlignVectors, (vec2(gl_FragCoord.xy))/vec2(textureSize(OutputBuffer, 0))).rg)*float(TILESIZE*8);
    /*vec2 inp = (texture(AlignVectors, (vec2(gl_FragCoord.xy))/vec2(textureSize(OutputBuffer, 0))).rg);
    if(abs(inp.r) > 0.00001) {
        inp.r = 1.0/inp.r;
    }
    if(abs(inp.g)  > 0.00001){
        inp.g = 1.0/inp.g;
    }*/
    //vec2 inp = (vec2(1.0)/(texture(AlignVectors, (vec2(gl_FragCoord.xy))/vec2(textureSize(OutputBuffer, 0))).rg));
    //vec2 inp = vec2(1.0)/vec2(texelFetch(AlignVectors, (xy/(TILESIZE*2)), 0).rg);
    ivec2 align = ivec2(texelFetch(AlignVectors, (xy/(TILESIZE*2)), 0).rg)-ivec2(16384);
    align = mirrorCoords((xy/2)+align,ivec4(0,0,(outsize-1)/2))*2 + state;
    //windoww/=float(number);
    //windoww/=4.0;
    //windoww*=alignk;
    //if(number == 1){
    //    Output = (texelFetch(OutputBuffer, xy, 0).x)/float(number)+((texelFetch(InputBuffer, (align), 0).x))*windoww;
    //} else
    //Output = (texelFetch(OutputBuffer, xy, 0).x)/sumweights+((texelFetch(InputBuffer, (align), 0).x))*windoww/sumweights;
    Output = mix(((texelFetch(OutputBuffer, xy, 0).x)), ((texelFetch(InputBuffer, (align), 0).x)), windoww);
}
