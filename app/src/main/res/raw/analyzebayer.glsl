#version 300 es
precision highp sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
out float Output;
#define SAMPLING (1)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float inp;
    ivec2 xy2 = (xy-ivec2(xy.x%3,0))*SAMPLING;
    if(xy.x%3 == 0)
    inp = texelFetch(InputBuffer, xy2, 0).r;
    else if(xy.x%3 == 1)
    inp = (texelFetch(InputBuffer, xy2+ivec2(0,1), 0).r+ texelFetch(InputBuffer, xy2+ivec2(1,0), 0).r)/2.0;
    else if(xy.x%3 == 2)
    inp = texelFetch(InputBuffer, xy2+ivec2(1,1), 0).r;
    Output = inp;
}
