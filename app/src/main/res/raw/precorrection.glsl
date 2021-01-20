#version 300 es
precision highp float;
precision mediump usampler2D;
uniform usampler2D InputBuffer;
uniform float WhiteLevel;
uniform int yOffset;
out float Output;
#define BL (0.0)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    float outp = ((float(texelFetch(InputBuffer, (xy), 0).x)-BL)/WhiteLevel);
    //outp = clamp(outp,0.0,1.0);
    Output = outp;
}