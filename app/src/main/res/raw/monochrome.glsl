#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform int yOffset;
uniform int WhiteLevel;
out vec4 Output;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    Output = vec4(clamp(float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel),0.,1.));
}
