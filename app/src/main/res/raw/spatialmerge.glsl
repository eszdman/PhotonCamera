#version 300 es
precision highp float;
precision mediump sampler2D;
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
#define TILESIZE (32)
#import interpolation
#import coords
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 tilexy = vec2(xy - TILESIZE*(xy/TILESIZE));
    tilexy/=float(TILESIZE);
    tilexy-=0.5;
    float dist = tilexy.x*tilexy.x+tilexy.y*tilexy.y;
    //ivec2 align = ivec2(texelFetch(AlignVectors, (xy/TILESIZE), 0).xy);
    vec2 xyInterp = vec2(xy)/float(TILESIZE);
    xyInterp/=vec2(alignsize);
    vec3 alignf = vec3(texture(AlignVectors, vec2(xy)/vec2(textureSize(OutputBuffer, 0))).rgb);
    //vec2 alignf = vec2(texelFetch(AlignVectors, xy/(TILESIZE*2),0).xy);
    ivec2 align = ivec2(alignf.xy*float(TILESIZE)*16.0/2.0);
    align = mirrorCoords(align,ivec4(0,0,(ivec2(textureSize(OutputBuffer, 0))-1)/2));
    align*=2;
    ivec2 aligned = (xy+align);
    aligned = clamp(aligned,ivec2(0,0),ivec2(rawsize));
    xyInterp = vec2(xy)/float(TILESIZE);

    xyInterp/=vec2(weightsize);
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
    windoww*=alignk;
    //windoww = float(int(windoww*100.0))/100.0;
    //float outp = (((texelFetch(InputBuffer, aligned, 0).x))*float((windoww))+((texelFetch(OutputBuffer, (xy), 0).x))*float((1.0-windoww)));
    if(number != 0){
        Output = mix(((texelFetch(OutputBuffer, xy, 0).x)), ((texelFetch(InputBuffer, (aligned), 0).x)), windoww);
        //if(align.x > 500) outp = 1.0;
    } else {
        Output = ((texelFetch(InputBuffer, (aligned), 0).x));
    }
    //Output = float(texelFetch(OutputBuffer, (xy), 0).x);
    //Output = texelFetch(InputBuffer22, aligned/2, 0).x;
    //Output = weight*10.0;
    //Output = abs(float(align.x+align.y));
}
