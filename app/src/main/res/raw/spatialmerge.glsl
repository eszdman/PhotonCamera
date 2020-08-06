#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D OutputBuffer;
uniform sampler2D InputBuffer;
uniform sampler2D MainBuffer;
uniform sampler2D InputBuffer22;
uniform sampler2D MainBuffer22;
uniform usampler2D AlignVectors;
uniform int yOffset;
uniform uvec2 rawsize;
uniform int CfaPattern;
out float Output;
#define TILESIZE (128)
float firstdiag(in ivec2 xy, sampler2D Input){
    return float(texelFetch(Input, (xy), 0).x+texelFetch(Input, (xy+ivec2(1,1)), 0).x);
}
float seconddiag(in ivec2 xy, sampler2D Input){
    return float(texelFetch(Input, (xy+ivec2(0,1)), 0).x+texelFetch(Input, (xy+ivec2(1,0)), 0).x);
}
vec3 boxdowncol(in ivec2 xy, sampler2D Input){
    vec3 outp;
    if(CfaPattern == 1 || CfaPattern == 2){
        outp.r = float(texelFetch(Input, (xy), 0).x);
        outp.g = seconddiag(xy,Input)/2.0;
        outp.b = float(texelFetch(Input, (xy+ivec2(1,1)), 0).x);
    } else {
        outp.r = float(texelFetch(Input, (xy+ivec2(0,1)), 0).x);
        outp.g = firstdiag(xy,Input)/2.0;
        outp.b = float(texelFetch(Input, (xy+ivec2(1,0)), 0).x);
    }
    return outp;
}
vec3 boxdowncol44(in ivec2 xy, sampler2D Input){
    return (boxdowncol(xy,Input)+
    boxdowncol(xy+ivec2(2,0),Input)+
    boxdowncol(xy+ivec2(0,2),Input)+
    boxdowncol(xy+ivec2(2,2),Input))/4.0;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 tilexy = vec2(xy - TILESIZE*(xy/TILESIZE));
    ivec2 align = ivec2(texelFetch(AlignVectors, (xy/TILESIZE), 0).xy);
    vec2 alignf = vec2(align);
    float windoww = 0.5;
    windoww = clamp(windoww,0.0,1.0);
    float outp = (float(texelFetch(InputBuffer, (xy+align), 0).x)*windoww+float(texelFetch(OutputBuffer, (xy), 0).x)*(1.0-windoww));
    Output = outp;
}
