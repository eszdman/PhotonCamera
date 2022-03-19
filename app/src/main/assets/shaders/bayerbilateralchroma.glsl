precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
#define NOISES 0.0
#define NOISEO 0.0
#define VALUETOSPATIAL 30.5
#define MSIZE 5
#define KSIZE (MSIZE-1)/2
#define INTENSE (1.0)
#import bayer
#import gaussian
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 fact = (xy)%2;
    if(fact.x+fact.y != 1){
        Output = float(texelFetch(InputBuffer, xy, 0).r);
        return;
    }
    float center = float(texelFetch(InputBuffer, xy, 0).r);
    float minV = 1.0;
    float maxV = 0.0;
    float br = 0.0;
    float sum0;
    for(int i =-KSIZE;i<=KSIZE;i++){
        for(int j =-KSIZE;j<=KSIZE;j++){
            br += float(texelFetch(InputBuffer, xy+2*ivec2(i,j), 0).r);
            sum0+=1.0;
        }
    }
    br/=sum0;
    float N = sqrt(br*NOISES*(INTENSE*1.5 + 1.0)/2.0 + NOISEO*INTENSE*1.5);
    float sigmaV = N;
    float sigmaS = 3.5;
    float sum = 0.0;
    float outSum = 0.0;
    for(int i =-KSIZE;i<=KSIZE;i++){
        float k0 = unscaledGaussian(float(i),sigmaS);
        for(int j =-KSIZE;j<=KSIZE;j++){
            float val = float(texelFetch(InputBuffer, xy+2*ivec2(i,j), 0).r);
            float k = k0*unscaledGaussian(float(j),sigmaS)*unscaledGaussian(center-val,sigmaV);
            outSum+=val*k;
            sum+=k;
        }
    }
    if (sum < 0.0001f) {
        Output = outSum;
    } else {
        Output = outSum/sum;
    }
    Output = clamp(0.0,1.0,Output);
}
