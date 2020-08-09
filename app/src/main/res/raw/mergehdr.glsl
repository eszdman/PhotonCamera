#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBufferLow;
uniform sampler2D InputBufferHigh;
uniform int yOffset;
out vec4 Output;
#define PI (3.1415926535)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec4 low = texelFetch(InputBufferLow, (xy), 0);
    vec4 high = texelFetch(InputBufferHigh, (xy), 0);
    //float avrbr = (low.r+low.g+low.b+high.r+high.g+high.b)/6.0;
    float avrbr = (high.r+high.g+high.b)/3.0;
    //float weight = 1.0 + cos(avrbr*PI*2.0);
    float weight = 1.15;
    high *=weight;
    low*=(2.0-weight);
    //high = clamp(high,0.0,1.0);
    Output = (low+high)/2.f;
}
