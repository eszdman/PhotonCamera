#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer22;
uniform sampler2D MainBuffer22;
uniform int yOffset;
#define MIN_NOISE 0.1f
#define MAX_NOISE 0.7f
float boxdown22(ivec2 xy, sampler2D inp){
    return
    (float(texelFetch(inp, (xy           ), 0).x)+
    float(texelFetch(inp, (xy+ivec2(1,0)), 0).x)+
    float(texelFetch(inp, (xy+ivec2(0,1)), 0).x)+
    float(texelFetch(inp, (xy+ivec2(1,1)), 0).x));
}
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    xy*=2;
    float inp = boxdown22(xy,InputBuffer22)/4.0;
    float target = boxdown22(xy,MainBuffer22)/4.0;
    //Output = abs(inp-target);
    Output = smoothstep(MIN_NOISE, MAX_NOISE, abs(inp-target));
}
