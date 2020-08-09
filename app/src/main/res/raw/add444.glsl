#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D InputBuffer2;
uniform int yOffset;
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    Output = texelFetch(InputBuffer, (xy), 0).x+texelFetch(InputBuffer2, (xy), 0).x;
}
