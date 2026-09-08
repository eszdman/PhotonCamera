
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
#define NIGHT_CONFIDENCE 0
#import night_post_confidence
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    const int size = 2;
    const int size2 = (size*2 + 1);
    float edges[size2*size2];
    float MIN = 1.0;
    float MAX = 0.0;
    float avr = 0.0;

    for(int i = -size; i<=size;i++){
        for(int j = -size; j<=size;j++){
            float temp = dot(texelFetch(InputBuffer, mirrorCoords2(xy+ivec2(i,j), ivec2(INSIZE)), 0).rgb,vec3(0.1,0.8,0.1));
            edges[(i+size)*size2 + j + size] = temp;
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
    float c2 = edges[(size)*size2 + size];
    float sharp = 0.0;
    for(int i = -size; i<=size;i++){
        float k0 = pdf(float(i)/SHARPSIZE);
        for (int j = -size; j<=size;j++){
            float br0 = edges[(i+size)*size2 + j + size];
            float br1 = edges[(i+size)*size2 - j + size];
            float br2 = edges[(-i+size)*size2 + j + size];
            float br3 = edges[(-i+size)*size2 - j + size];
            //float k = k0*pdfSharp(float(j)/SHARPSIZE,1.0);
            if (i == 0 && j == 0) continue;
            // select symmetric nearest neighbour
            float w0 = abs(br0 - c2);
            float w1 = abs(br1 - c2);
            float w2 = abs(br2 - c2);
            float w3 = abs(br3 - c2);
            float mn = min(w0,min(w1,min(w2,w3)));
            w0 = mn == w0 ? 0.0 : 1.0;
            w1 = mn == w1 ? 0.0 : 1.0;
            w2 = mn == w2 ? 0.0 : 1.0;
            w3 = mn == w3 ? 0.0 : 1.0;
            float br = br0*w0 + br1*w1 + br2*w2 + br3*w3;
            float k = w0+w1+w2+w3;
            sharp+=br;
            ksum+=k;
        }
    }
    sharp+=0.0001;
    ksum+=0.0001;
    // W=sqrt(W);
    W = mix(SHARPMIN,SHARPMAX,W);
    W*=strength;
    W*=nightSharpenMultiplier((vec2(xy) + .5) / vec2(textureSize(InputBuffer, 0)));

    //float W2 = 1.0-pdf((Output.g/ksum - center.g)/N);
    //W*=W2;
    //W = max(W,-0.90/ksum);
    sharp = dot(center.rgb,vec3(0.1,0.8,0.1)) - sharp/ksum;
    // normalize using Wiener filter
    float sw = (sharp*sharp)/(sharp*sharp + N*N + 0.0001);
    sharp*=W*sw;
    Output = sharp + center.rgb;
    Output = clamp(Output,0.0,1.0);
}
