
precision mediump float;
precision mediump usampler2D;
uniform usampler2D InputBuffer;
uniform int yOffset;
uniform int CfaPattern;
out float Output;
#define size1 (1.5)
#define MSIZE1 3
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
uint getPix(ivec2 coords){
    uint outp = uint(0);
    //for(int l = 0; l<4; l++){
    //    outp+=uint(texelFetch(InputBuffer, (coords+ivec2(l/2,l%2)), 0).x);
    //}
    outp=uint(texelFetch(InputBuffer, (coords+ivec2(0,0)), 0).x)+uint(texelFetch(InputBuffer, (coords+ivec2(1,1)), 0).x);
    return outp;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    xy*=2;
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    const int kSize = (MSIZE1-1)/2;
    float kernel[MSIZE1];
    float mask = 0.0;
    float pdfsize = 0.0;
    float cur = float(getPix(xy));
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), 1.5);
    for (int i=-kSize; i <= kSize; ++i){
        for (int j=-kSize; j <= kSize; ++j){
            float pdf = kernel[kSize+j]*kernel[kSize+i];
            mask+=(float(getPix(xy+ivec2(i,j)))*pdf);
            pdfsize+=pdf;
        }
    }
    mask/=pdfsize;
    Output = cur-mask;
}
