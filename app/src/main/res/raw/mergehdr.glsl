#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBufferLow;
uniform sampler2D InputBufferHigh;
uniform int yOffset;
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    Output = (texelFetch(InputBuffer, (xy), 0).x+texelFetch(InputBuffer2, (xy), 0).x)/2.f;
}
