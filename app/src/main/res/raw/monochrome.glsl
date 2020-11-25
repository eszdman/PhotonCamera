#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform int yOffset;
uniform int WhiteLevel;
out vec3 Output;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    Output = vec3(clamp(float(texelFetch(RawBuffer, (xy), 0).x),0.,1.));
}
