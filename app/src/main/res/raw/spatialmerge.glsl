#version 300 es
precision highp float;
precision highp sampler2D;
precision mediump isampler2D;
precision mediump usampler2D;
uniform sampler2D OutputBuffer;

uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;

uniform sampler2D InputBuffer22;
uniform sampler2D MainBuffer22;

uniform sampler2D SpatialWeights;
uniform sampler2D AlignVectors;
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
    float windoww = 1.0;
    //windoww-=windoww*dist*(dist2);
    windoww = clamp(windoww,0.00,1.0);

    //windoww = float(int(windoww*100.0))/100.0;
    //float outp = (((texelFetch(InputBuffer, aligned, 0).x))*float((windoww))+((texelFetch(OutputBuffer, (xy), 0).x))*float((1.0-windoww)));
    ivec2 outsize = ivec2(textureSize(OutputBuffer, 0));
    ivec2 state = xy%2;
    vec2 inp = (vec2(0.5)-texture(AlignVectors, (vec2(gl_FragCoord.xy))/vec2(textureSize(OutputBuffer, 0))).rg)*float(TILESIZE)*16.0;
    //vec2 inp = vec2(texelFetch(AlignVectors, (xy/(TILESIZE*2)), 0).rg)*float(TILESIZE)*256.0;
    ivec2 align = ivec2(inp.rg/2.0);//-ivec2(TILESIZE*4-TILESIZE/2,TILESIZE*2);
    align = mirrorCoords((xy/2)+align,ivec4(0,0,(outsize-1)/2))*2 + state;
    float weight = float(texture(SpatialWeights, vec2(gl_FragCoord.xy)/vec2(outsize)).x);
    if(number != 0){
        windoww/=float(number);
        //windoww/=4.0;
        //windoww*=alignk;
        Output = mix(((texelFetch(OutputBuffer, xy, 0).x)), ((texelFetch(InputBuffer, (align), 0).x)), windoww);

        /*align = ivec2(texture(AlignVectors, vec2(gl_FragCoord.xy+vec2(0,1))/vec2(textureSize(OutputBuffer, 0))).rg*float(TILESIZE)*16.0/2.0);
        align = mirrorCoords((xy/2)+align,ivec4(0,0,(ivec2(textureSize(OutputBuffer, 0))-1)/2))*2 + state;
        Output = mix(Output, ((texelFetch(InputBuffer, (align), 0).x)), windoww);

        align = ivec2(texture(AlignVectors, vec2(gl_FragCoord.xy+vec2(1,0))/vec2(textureSize(OutputBuffer, 0))).rg*float(TILESIZE)*16.0/2.0);
        align = mirrorCoords((xy/2)+align,ivec4(0,0,(ivec2(textureSize(OutputBuffer, 0))-1)/2))*2 + state;
        Output = mix(Output, ((texelFetch(InputBuffer, (align), 0).x)), windoww);

        align = ivec2(texture(AlignVectors, vec2(gl_FragCoord.xy+vec2(0,-1))/vec2(textureSize(OutputBuffer, 0))).rg*float(TILESIZE)*16.0/2.0);
        align = mirrorCoords((xy/2)+align,ivec4(0,0,(ivec2(textureSize(OutputBuffer, 0))-1)/2))*2 + state;
        Output = mix(Output, ((texelFetch(InputBuffer, (align), 0).x)), windoww);

        align = ivec2(texture(AlignVectors, vec2(gl_FragCoord.xy+vec2(-1,0))/vec2(textureSize(OutputBuffer, 0))).rg*float(TILESIZE)*16.0/2.0);
        align = mirrorCoords((xy/2)+align,ivec4(0,0,(ivec2(textureSize(OutputBuffer, 0))-1)/2))*2 + state;
        Output = mix(Output, ((texelFetch(InputBuffer, (align), 0).x)), windoww);*/
        //if(align.x > 500) outp = 1.0;
    } else {
        Output = ((texelFetch(InputBuffer, (align), 0).x));
    }
    //vec2 inpp = vec2(texelFetch(AlignVectors, (xy/(TILESIZE*2)), 0).rg)*float(TILESIZE)*256.0;

    //vec2 inpp = (vec2(0.5)-texture(AlignVectors, (vec2(gl_FragCoord.xy))/vec2(textureSize(OutputBuffer, 0))).rg);
    //Output = clamp((abs(inpp.x)+abs(inpp.y))/float(2),0.0,1.0);

    //Output = float(texelFetch(OutputBuffer, (xy), 0).x);
    //Output = texelFetch(InputBuffer22, xy/2, 0).x;
    //Output = weight*1.0;
    //Output = (((texelFetch(InputBuffer22, xy, 0).r))+((texelFetch(InputBuffer22, xy, 0).b)))/2.0;
}
