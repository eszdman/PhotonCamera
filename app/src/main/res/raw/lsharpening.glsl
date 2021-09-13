#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D BlurBuffer;
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
float normpdf(in float x, in float sigma){return exp(-0.5*x*x/(sigma*sigma));}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float edges[25];
    float MIN = 1.0;
    float MAX = 0.0;
    float avr = 0.0;
    for(int i = -2; i<=2;i++){
        for(int j = -2; j<=2;j++){
            vec4 temp = texelFetch(BlurBuffer, mirrorCoords2(xy+ivec2(i,j), ivec2(INSIZE)), 0);
            edges[(i+2)*5 + j + 2] = temp.g;
            MIN = min(temp.a,MIN);
            MAX = max(temp.a,MAX);
            avr+=temp.a;
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
        float k0 = normpdf(float(i), SHARPSIZE);
        for (int j = -2; j<=2;j++){
            float br = edges[(i+2)*5 + j + 2];
            float k = k0*normpdf(float(j), SHARPSIZE);
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
    W = max(W,-0.90/ksum);
    float W2 = 1.0-normpdf(Output.g/ksum - center.g,N);
    W*=W2;

    Output = (Output*W - center.g*W*ksum)/(W*ksum + 1.0) + center.rgb;
}
