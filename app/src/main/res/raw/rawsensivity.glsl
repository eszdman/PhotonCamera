#version 300 es
precision highp float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform float whitelevel;
uniform float sensivity;
uniform int yOffset;
out uint Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    uint rawpart = uint(texelFetch(RawBuffer, (xy), 0).x);
    float flpart = float(rawpart)/whitelevel;
    flpart =clamp(flpart*whitelevel*sensivity,0.0,65535.0);
    Output = uint(flpart);
}