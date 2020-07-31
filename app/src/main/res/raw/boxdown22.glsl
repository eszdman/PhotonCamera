#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform sampler2D InputBuffer;

uniform int yOffset;
out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    xy*=2;
    Output+=vec4(texelFetch(InputBuffer, (xy), 0));
    Output+=vec4(texelFetch(InputBuffer, (xy+ivec2(0,1)), 0));
    Output+=vec4(texelFetch(InputBuffer, (xy+ivec2(1,0)), 0));
    Output+=vec4(texelFetch(InputBuffer, (xy+ivec2(1,1)), 0));
    Output/=4.0;
}
