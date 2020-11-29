#version 300 es
uniform sampler2D InputBuffer;
uniform vec3 whitePoint;
uniform int yOffset;
out float Output;
#define size1 (0.7)
#define MSIZE1 3
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    const int kSize = (MSIZE1-1)/2;
    float kernel[MSIZE1];
    float mask = 0.0;
    float pdfsize = 0.0;
    if ((xy.x%2)+(xy.y%2) == 1){
        Output = texelFetch(InputBuffer, (xy), 0).x;
        return;
    }
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size1);
    for (int i=-kSize; i <= kSize; ++i){
        for (int j=-kSize; j <= kSize; ++j){
            ivec2 outpp = (xy+ivec2(i,j));
            float pdf = kernel[kSize+j]*kernel[kSize+i];
            float inp = texelFetch(InputBuffer, (xy+ivec2(i,j)), 0).x;
            if(i != 0 && j != 0)
                if ((outpp.x%2)+(outpp.y%2) == 1) continue;
            if((outpp.x%2) == 0 && (outpp.y%2) == 0){
                pdf*=whitePoint[0];
            } else {
                pdf*=whitePoint[2];
            }
            if(abs(inp) > 1.0/1000.0){
                mask+=inp*pdf;
                pdfsize+=pdf;
            }
        }
    }
    mask/=pdfsize;
    Output = mask;
}
