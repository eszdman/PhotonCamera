precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;
uniform float size;
uniform float strength;
out vec4 Output;
//#define depthMin (0.012)
#define depthMin (0.006)
#define depthMax (0.890)
#define colour (0.2)
#define size1 (1.1)
#define MSIZE1 3
#define kSize ((MSIZE1-1)/2)
#define SAVEGREEN 0
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 mask = vec3(0.0);
    float kernel[MSIZE1];
    float pdfsize = 0.0;
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), size1);
    for (int i=-kSize; i <= kSize; ++i){
        for (int j=-kSize; j <= kSize; ++j){
            float pdf = kernel[kSize+j]*kernel[kSize+i];
            Output.rgb+=vec3(texelFetch(InputBuffer, (xy+ivec2(i,j)), 0).rgb)*pdf*2.0;
            //mask+=vec3(texelFetch(InputBuffer, (xy+ivec2(i*2,j*2)), 0).rgb)*pdf*0.3;
            pdfsize+=pdf;
        }
    }
    Output/=pdfsize*2.0;
    #if SAVEGREEN == 1
    Output.a = texelFetch(InputBuffer, xy, 0).g;
    #endif
}