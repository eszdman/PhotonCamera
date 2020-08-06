#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform float whitelevel;
uniform usampler2D HotPixelMap;
uniform ivec2 HotPixelMapSize;
uniform int yOffset;
uniform float PostRawSensivity;

out uint Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    //xy.x*=2;
    xy+=ivec2(0,yOffset);
    uint rawpart = uint(texelFetch(RawBuffer, (xy), 0).x);
    float flpart = float(rawpart)/whitelevel;
    flpart =clamp(flpart*PostRawSensivity*whitelevel,0.0,65535.0);
    Output = int(flpart);
}