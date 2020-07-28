#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;
uniform float size;
uniform float strength;
out vec4 Output;
//#define depthMin (0.012)
#define depthMin (0.006)
#define depthMax (0.790)
#define colour (0.2)
#define size1 (1.2)
#define MSIZE1 5
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec4 mask = vec4(0.0);
    vec4 cur = (texelFetch(InputBuffer, (xy), 0));
    const int kSize = (MSIZE1-1)/2;
    float kernel[MSIZE1];
    float pdfsize = 0.0;
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size*2.0);
    for (int i=-kSize; i <= kSize; ++i){
       for (int j=-kSize; j <= kSize; ++j){
           float pdf = kernel[kSize+j]*kernel[kSize+i];
           mask+=vec4(texelFetch(InputBuffer, (xy+ivec2(i,j)), 0))*pdf*1.5;
           mask+=vec4(texelFetch(InputBuffer, (xy+ivec2(i*2,j*2)), 0))*pdf*0.5;
           pdfsize+=pdf;
       }
    }
    mask/=pdfsize*2.0;
    mask =(cur-mask);
    mask=clamp(mask,-depthMax,depthMax);
    if(abs(mask.r+mask.b+mask.g) < depthMin) mask*=0.;
    mask*=strength*2.0;
    if(abs(cur.r+cur.g+cur.b) > colour*3.) cur+=mask;
    else {
        cur+=(mask.r+mask.g+mask.b)/3.;
    }
    Output = cur;
}