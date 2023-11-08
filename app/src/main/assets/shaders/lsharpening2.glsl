
precision highp float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform float size;
uniform float strength;
out vec3 Output;
#define INSIZE 1,1
#define SHARPSIZE 1.25
#define SHIFT 0.5
#define SHARPMAX 1.0
#define SHARPMIN 0.5
#define NOISEO 0.0
#define NOISES 0.0
#define INTENSE 1.0
#import coords
#import gaussian
float pdfSharp(float i, float sig) {
    i/=sig;
    return 1.0/(1.0+i*i*i*i);
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float edges[25];
    float MIN = 1.0;
    float MAX = 0.0;
    float avr = 0.0;
    for(int i = -2; i<=2;i++){
        for(int j = -2; j<=2;j++){
            float temp = dot(texelFetch(InputBuffer, mirrorCoords2(xy+ivec2(i,j), ivec2(INSIZE)), 0).rgb,vec3(0.1,0.8,0.1));
            edges[(i+2)*5 + j + 2] = temp;
            MIN = min(temp,MIN);
            MAX = max(temp,MAX);
            avr+=temp;
        }
    }
    avr/=25.0;

    float dmax = 1.0 - MAX;
    float W;
    if(dmax < MIN){
        W = dmax/MAX;
    } else {
        W = MIN/MAX;
    }
    float ksum = 0.0;
    float N = sqrt(avr*NOISES*INTENSE + NOISEO*INTENSE); + 0.00001;
    vec3 center = texelFetch(InputBuffer, (xy), 0).rgb;
    for(int i = -2; i<=2;i++){
        float k0 = pdf(float(i)/SHARPSIZE);
        for (int j = -2; j<=2;j++){
            float br = edges[(i+2)*5 + j + 2];
            float k = k0*pdfSharp(float(j)/SHARPSIZE,1.0);
            if (i == 12) continue;
            Output+=br*k;
            ksum+=k;
        }
    }
    Output+=0.0001;
    ksum+=0.0001;
    W=sqrt(W);
    W = mix(SHARPMIN,SHARPMAX,W);
    W*=-strength/ksum;

    //float W2 = 1.0-pdf((Output.g/ksum - center.g)/N);
    //W*=W2;
    //W = max(W,-0.90/ksum);
    Output = (Output*W - dot(center.rgb,vec3(0.1,0.8,0.1))*W*ksum)/(W*ksum + 1.0) + center.rgb;
    Output = clamp(Output,0.0,1.0);
}
