#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D RawBuffer;
uniform vec4 blackLevel;
uniform int yOffset;
out vec3 Output;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    Output = vec3(clamp(float(texelFetch(RawBuffer, (xy), 0).x),0.,1.)-(blackLevel.r+blackLevel.g+blackLevel.b+blackLevel.a)/4.0);
}
