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
#define SHARPSIZE 5
#define SHARPSIZEKER 3.0
#define SHARPSTR 1.0
float normpdf(in float x, in float sigma){
    x/=sigma;
    return exp(-0.5*x*x);
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 mask = vec3(0.0);
    vec3 cur = (texelFetch(InputBuffer, (xy), 0).rgb);
    float pdfsize = 0.0;
    for (int i=-SHARPSIZE; i <= SHARPSIZE; ++i){
        float pdf2 = normpdf(float(i), float(SHARPSIZEKER));
        for (int j=-SHARPSIZE; j <= SHARPSIZE; ++j){
            float pdf = normpdf(float(j), float(SHARPSIZEKER))*pdf2;
            mask+=vec3(texelFetch(InputBuffer, (xy+ivec2(i, j)), 0).rgb)*pdf;
            pdfsize+=pdf;
        }
    }
    mask/=pdfsize;
    mask = cur-mask;

    cur+=(mask.r+mask.g+mask.b)*(float(SHARPSTR)/3.0);
    Output = clamp(cur,0.0,1.0);
}
