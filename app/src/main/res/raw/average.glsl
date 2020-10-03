#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D InputBuffer;
uniform usampler2D InputBuffer2;
uniform int yOffset;
#define koeff (7)
out uint Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    //xy.x*=2;
    xy+=ivec2(0,yOffset);
    uint rawpart = uint(texelFetch(InputBuffer, (xy), 0).x);
    uint rawpart2 = uint(texelFetch(InputBuffer2, (xy), 0).x);
    Output = uint((rawpart*uint((koeff-1))) +(rawpart2))/uint(koeff);
}