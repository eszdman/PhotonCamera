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
#import coords
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float edges[25];
    float MIN = 1.0;
    float MAX = 0.0;
    for(int i = -2; i<=2;i++){
        for(int j = -2; j<=2;j++){
            vec4 temp = texelFetch(BlurBuffer, mirrorCoords2(xy+ivec2(i,j), ivec2(INSIZE)), 0);
            edges[(i+2)*5 + j + 2] = temp.g;
            MIN = min(temp.g,MIN);
            MAX = max(temp.g,MAX);
        }
    }
    float dmax = 1.0 - MAX;
    float W;
    if(dmax < MIN){
        W = dmax/MAX;
    } else {
        W = MIN/MAX;
    }
    float ksum = 0.0;
    vec3 center = texelFetch(InputBuffer, (xy), 0).rgb;
    for(int i = -2; i<=2;i++){
        for (int j = -2; j<=2;j++){
            float k = normpdf(float(i), SHARPSIZE)*normpdf(float(j), SHARPSIZE);
            if (i == 12) continue;
            Output+=edges[(i+2)*5 + j + 2]*k;
            ksum+=k;
        }
    }
    W=sqrt(W)*-strength/ksum;
    W = max(W,-0.90/ksum);
    Output = (Output*W + center.g)/(W*ksum + 1.0) - center.g + center.rgb;
}
