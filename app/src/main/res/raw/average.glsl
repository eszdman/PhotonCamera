#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D InputBuffer;
uniform usampler2D InputBuffer2;
uniform int unlimitedcount;
uniform vec4 blackLevel;
uniform int yOffset;
out uint Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    //xy.x*=2;
    xy+=ivec2(0,yOffset);
    float rawpart = float(texelFetch(InputBuffer, (xy), 0).x)-(blackLevel.g+0.5);
    float rawpart2 = float(texelFetch(InputBuffer2, (xy), 0).x)-(blackLevel.g+0.5);
    Output = uint(((rawpart*float((unlimitedcount-1))) +(rawpart2))/float(unlimitedcount) + blackLevel.g+0.5);
}