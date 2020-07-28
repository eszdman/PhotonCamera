#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform sampler2D InputBuffer;

uniform int yOffset;
out vec4 Output;
#define size1 (1.2)
#define MSIZE1 3
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    xy*=4;
    const int kSize = (MSIZE1-1)/2;
    float kernel[MSIZE1];
    float pdfsize = 0.0;
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), 1.5);


}
