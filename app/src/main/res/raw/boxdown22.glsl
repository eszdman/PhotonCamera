#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    xy*=2;
    float outp = 0.0;
    outp+=texelFetch(InputBuffer, (xy), 0).x;
    outp+=texelFetch(InputBuffer, (xy+ivec2(0,1)), 0).x;
    outp+=texelFetch(InputBuffer, (xy+ivec2(1,0)), 0).x;
    outp+=texelFetch(InputBuffer, (xy+ivec2(1,1)), 0).x;
    outp/=4.0;
    Output = outp;
}
