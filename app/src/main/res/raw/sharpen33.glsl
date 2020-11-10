#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform float size;
uniform float strength;
out vec3 Output;
//#define depthMin (0.012)
#define depthMin (0.006)
#define depthMax (0.890)
#define colour (0.2)
#define size1 (1.1)
#define MSIZE1 3
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
vec3 blur(ivec2 coords){
    ivec2 xy = coords;
    vec3 mask = vec3(0.0);
    const int kSize = (MSIZE1-1)/2;
    float kernel[MSIZE1];
    float pdfsize = 0.0;
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size1);
    for (int i=-kSize; i <= kSize; ++i){
        for (int j=-kSize; j <= kSize; ++j){
            float pdf = kernel[kSize+j]*kernel[kSize+i];
            mask+=vec3(texelFetch(InputBuffer, (xy+ivec2(i,j)), 0).rgb)*pdf*2.0;
            //mask+=vec3(texelFetch(InputBuffer, (xy+ivec2(i*2,j*2)), 0).rgb)*pdf*0.3;
            pdfsize+=pdf;
        }
    }
    mask/=pdfsize*2.0;
    return mask;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 mask = vec3(0.0);
    vec3 cur = (texelFetch(InputBuffer, (xy), 0).rgb);
    //vec3 cur = blur(xy);
    const int kSize = (MSIZE1-1)/2;
    float kernel[MSIZE1];
    float pdfsize = 0.0;
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size);
    for (int i=-kSize; i <= kSize; ++i){
       for (int j=-kSize; j <= kSize; ++j){
           float pdf = kernel[kSize+j]*kernel[kSize+i];
           //mask+=vec3(texelFetch(InputBuffer, (xy+ivec2(i,j)), 0).rgb)*pdf*1.7;
           //mask+=vec3(texelFetch(InputBuffer, (xy+ivec2(i*2,j*2)), 0).rgb)*pdf*0.3;
           mask+=blur((xy+ivec2(i,j)))*pdf*1.65;
           mask+=blur((xy+ivec2(i*2,j*2)))*pdf*.35;
           pdfsize+=pdf;
       }
    }
    mask/=pdfsize*2.0;
    mask =(blur(xy)-mask);
    //mask=clamp(mask,-depthMax,depthMax);
    if(abs(mask.r+mask.b+mask.g) < depthMin) mask*=0.;
    //if(abs(cur.r+cur.g+cur.b) > colour*3.) cur+=mask;
    //else {
    //cur-=mask;
    cur+=(mask.r+mask.g+mask.b)*((strength*4.0/3.));
    //}
    cur = clamp(cur,0.0,1.0);
    Output = cur;
}