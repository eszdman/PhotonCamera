#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform usampler2D HotPixelMap;
ivec2 HotPixelMapSize;
uniform int yOffset;
uniform float PostRawSensivity;

out float Output;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    float rawIn = float(texelFetch(RawBuffer, (xy), 0).x)/65535.0;
    rawIn*=PostRawSensivity;
    rawIn = clamp(rawIn,0.0,1.0);
    Output = float(rawIn);
}