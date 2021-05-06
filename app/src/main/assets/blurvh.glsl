#version 300 es
#define SIZE 0.5
#define KSIZE 1
#define INSIZE 1
#define tvar vec2
#define tscal float
#define TSAMP sampler2D
#define INP INP;
#define coordstp(x,y) (ivec2(x,y))
precision mediump TSAMP;
precision mediump tscal;
uniform TSAMP InputBuffer;

#import coords
out tvar Output;
float normpdf(in float x, in float sigma){
    return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float pdfsize = 0.0;
    tvar mask = tvar(0.0);
    for (int i=-KSIZE; i <= KSIZE; ++i){
        float pdf = normpdf(float(abs(i)), SIZE);
        INP
        mask+=inp*pdf;
        pdfsize+=pdf;
    }
    mask/=pdfsize;
    Output=mask;
}
