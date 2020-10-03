#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;
uniform float size;
out vec4 Output;
#define MSIZE1 5
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    const int kSize = (MSIZE1-1)/2;
    float kernel[MSIZE1];
    vec4 mask = vec4(0.0);
    float pdfsize = 0.0;
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size);
    for (int i=-kSize; i <= kSize; ++i){
        for (int j=-kSize; j <= kSize; ++j){
            float pdf = kernel[kSize+j]*kernel[kSize+i];
            mask+=vec4(texelFetch(InputBuffer, (xy+ivec2(i,j)), 0))*pdf;
            pdfsize+=pdf;
        }
    }
    mask/=pdfsize;
    Output = (vec4(texelFetch(InputBuffer, (xy), 0))-mask);
    //Output = vec4(texelFetch(InputBuffer, (xy), 0));
}
