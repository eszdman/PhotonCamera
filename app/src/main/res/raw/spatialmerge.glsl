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
out float Output;
#define TILESIZE (32)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 tilexy = vec2(xy - TILESIZE*(xy/TILESIZE));
    ivec2 align = ivec2(texelFetch(AlignVectors, (xy/TILESIZE), 0).xy);
    vec2 alignf = vec2(align);
    float dist = (alignf.x*alignf.x+alignf.y*alignf.y)/(float(rawsize.x)*0.05);
    tilexy/=float(TILESIZE);
    tilexy-=0.5;
    float dist2 = (tilexy.x*tilexy.x+tilexy.y*tilexy.y)/2.0;
    float windoww = 1.0 - (dist+0.7)*dist2 - dist*2.0;
    windoww = clamp(windoww,0.0,1.0);
    float outp = (float(texelFetch(InputBuffer, (xy+align), 0).x)*windoww+float(texelFetch(OutputBuffer, (xy), 0).x)*(1.0-windoww));
    Output = outp;
}
