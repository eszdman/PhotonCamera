#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;
out float Output;
#define size1 (1.5)
#define MSIZE1 5
vec3 getCol(in ivec2 xy){
    vec3 inp = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    //return inp/((inp.r+inp.g+inp.b+0.0001));
    return normalize(inp);
}
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    const int kSize = (MSIZE1-1)/2;
    float kernel[MSIZE1];
    vec3 mask = vec3(0.0);
    float pdfsize = 0.0;
    vec3 lenp = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    vec3 cur = getCol(xy);
    float len = (lenp.r+lenp.g+lenp.b)/(cur.r+cur.g+cur.b);
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size1);
    for (int i=-kSize; i <= kSize; ++i){
        for (int j=-kSize; j <= kSize; ++j){
            float pdf = kernel[kSize+j]*kernel[kSize+i];
            mask+=getCol(xy+ivec2(i,j))*pdf;
            pdfsize+=pdf;
        }
    }
    mask/=pdfsize;
    vec3 outp = (cur-mask);
    Output = clamp(float(outp.r+outp.g*0.5+outp.b)*len*5.0,0.0,1.0);
}
