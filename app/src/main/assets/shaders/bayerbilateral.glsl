precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
#define NOISES 0.0
#define NOISEO 0.0
#define VALUETOSPATIAL 30.5
#define MSIZE 3
#define KSIZE (MSIZE-1)/2
#define INTENSE (1.0)
#import bayer
#import gaussian
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 fact = (xy)%2;
    /*if(fact.x+fact.y != 1){
        Output = float(texelFetch(InputBuffer, xy, 0).r);
        return;
    }*/
    vec2[9] outp = bayer9(xy,InputBuffer);
    float center = outp[4].r;
    float minV = 1.0;
    float maxV = 0.0;
    float br = 0.0;
    for(int i =0; i<9;i++){
        float cur = outp[i].r;
        minV = min(cur,minV);
        maxV = max(cur,maxV);
        br +=cur;
    }
    br/=9.0;
    float dmax = 1.0 - maxV;
    float W;
    if(dmax < minV){
        W = dmax/maxV;
    } else {
        W = minV/maxV;
    }
    float N = sqrt(br*NOISES*(INTENSE*2.2 + 1.0)/2.0 + NOISEO*INTENSE*2.2);
    float sigmaV = N;
    float sigmaS = 3.5;
    float sum = 0.0;
    float outSum = 0.0;
    for (int i=0; i <MSIZE*MSIZE; i++){
        float val = outp[i].r;
        float k = unscaledGaussian(outp[i].g,sigmaS)*unscaledGaussian(center-val,sigmaV);
        outSum+=val*k;
        sum+=k;
    }
    if (sum < 0.0001f) {
        Output = outSum;
    } else {
        Output = outSum/sum;
    }
    Output = clamp(0.0,1.0,Output);
}
