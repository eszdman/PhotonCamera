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
    ivec2 align = ivec2(texelFetch(AlignVectors, (xy/TILESIZE), 0).xy);
    float outp = (float(texelFetch(InputBuffer, (xy+align), 0).x)*0.2+float(texelFetch(OutputBuffer, (xy), 0).x)*0.8);
    Output = outp;
}
