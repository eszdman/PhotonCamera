#version 300 es
precision highp float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform usampler2D InputBuffer;
uniform sampler2D GainMap;
uniform float MaxMap;
out uint Output;
uniform int yOffset;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    float outp = float(texelFetch(InputBuffer,xy,0).r)/(texelFetch(GainMap,xy,0).r*MaxMap);
    Output = uint(clamp(outp,0.0,65535.0));
}
