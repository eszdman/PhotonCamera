#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D InputBuffer2;
uniform int unlimitedcount;
uniform int yOffset;
out float Output;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    Output = float(
    mix(
    float(texelFetch(InputBuffer2, (xy), 0).x)
    ,
    float(texelFetch(InputBuffer, (xy), 0).x)
    , 1.f/float(unlimitedcount)
    )
    );
}