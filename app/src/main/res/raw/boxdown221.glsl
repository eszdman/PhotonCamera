#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D GainMap;
uniform int CfaPattern;
uniform int yOffset;
out float Output;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    xy*=2;
    Output = (float(texelFetch(InputBuffer, (xy), 0).r)
    +float(texelFetch(InputBuffer, (xy+ivec2(1,0)), 0).r)
    +float(texelFetch(InputBuffer, (xy+ivec2(0,1)), 0).r)
    +float(texelFetch(InputBuffer, (xy+ivec2(1,1)), 0).r))/4.0;
}
