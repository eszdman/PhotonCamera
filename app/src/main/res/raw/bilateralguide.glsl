#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D NoiseMap;
uniform sampler2D ToneMap;
#define M_PI 3.1415926535897932384626433832795
#define TONEMAPED 0
#define KERNEL 1
#define ISOFACTOR (0.5)
#define SIZE (1.0,1.0)
#define STR (1.0)
#define SMOOTHING (1)
#define LANCZOS 0
#define sinc(x) (sin(x) / (x+0.0001))
#import interpolation
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define distribute(x,dev,sigma) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))
#define distribute2(x,dev) (1.0-abs(x-dev)*STR)
#define pdf(x) (exp(-0.5*x*x/(STR*STR))/STR)
#define pdf2(x) (sinc((x)*3.0)/(STR*3.0))
float nlmeans(ivec2 coords) {
    float processed = 0.0;
    float weights = 0.0;
    float noisefactor = clamp((textureBicubicHardware(NoiseMap, vec2(gl_FragCoord.xy)/vec2(SIZE)).r)*0.55*ISOFACTOR,0.0005,1.0);
    noisefactor*=noisefactor;
    noisefactor*=0.6;
    for(int i = -KERNEL; i <= KERNEL; i++) {
        for(int j = -KERNEL; j <= KERNEL; j++) {
            ivec2 sxy = ivec2(i,j);
            sxy = clamp(sxy-SMOOTHING,0,KERNEL);
            #if LANCZOS == 1
            float dist = pdf2(float(sxy.x)/float(KERNEL))*pdf2(float(sxy.y)/float(KERNEL));
            #else
            float dist = pdf(float(sxy.x)/float(KERNEL))*pdf(float(sxy.y)/float(KERNEL));
            #endif
            ivec2 patchCoord = coords + ivec2(i, j);
            float sigma = (0.01*0.5 + noisefactor*0.25);
            float w;
            #if TONEMAPED == 1
            w = distribute((texelFetch(ToneMap,coords,0).r),
                    (texelFetch(ToneMap,  patchCoord,0).r), sigma)*dist;
            #else
            w = distribute(luminocity(texelFetch(InputBuffer, coords,0).rgb), luminocity(texelFetch(InputBuffer, patchCoord,0).rgb), sigma)*dist;
            #endif
            processed += w * luminocity(texelFetch(InputBuffer, patchCoord,0).rgb);
            weights += w;
        }
    }
    //processed = clamp(processed,0.0,10000.0);
    //weights = clamp(weights,0.0,10000.0);
    if(abs(weights) > 0.0001){
        return ((processed) / (weights));
    } else return luminocity(texelFetch(InputBuffer, coords,0).rgb);
}

//////////////////////////////////////////////////////////////////////////////////
out vec3 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 xyz = (texelFetch(InputBuffer, xy,0).rgb)+0.001;
    float br = luminocity(xyz);
    xyz/=br;
    br = nlmeans(xy);
    Output = clamp(xyz*br - 0.002,0.0,1.0);
}