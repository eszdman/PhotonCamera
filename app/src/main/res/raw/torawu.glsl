#version 300 es
precision highp float;
precision mediump usampler2D;
uniform usampler2D InputBuffer;
uniform float whitelevel;
uniform int yOffset;

out uint Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    uint rawpart = uint(floor((float(texelFetch(InputBuffer, (xy), 0).x))*whitelevel));
    Output = (rawpart);
}