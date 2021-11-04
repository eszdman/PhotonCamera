
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;
out float Output;
#define size1 (1.2)
#define MSIZE1 3
#define KSIZE ((MSIZE1-1)/2)
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    float mask = 0.0;
    float pdfsize = 0.0;
    float kernel[MSIZE1];
    for (int j = 0; j <= KSIZE; ++j) kernel[KSIZE+j] = kernel[KSIZE-j] = normpdf(float(j), size1);
    for (int i=-KSIZE; i <= KSIZE; ++i){
        for (int j=-KSIZE; j <= KSIZE; ++j){
            float pdf = kernel[KSIZE+j]*kernel[KSIZE+i];
            float inp = texelFetch(InputBuffer, (xy+ivec2(i,j)), 0).x;
            if(abs(inp) > 1.0/1000.0){
                mask+=inp*pdf;
                pdfsize+=pdf;
            }
        }
    }
    mask/=pdfsize;
    Output = mask;
}
