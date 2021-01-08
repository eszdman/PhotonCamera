#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D NoiseMap;
uniform sampler2D ToneMap;
#define M_PI 3.1415926535897932384626433832795

#define TONEMAPED 0
#define KERNEL 1
#define ISOFACTOR (0.5)
#define SIZE (1.0,1.0)
#import interpolation
//#define luminocity(x) ((((x.r+x.g+x.b)/3.0))+0.001)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
/*float luminocity(vec3 color) {
    return (color.r+color.g+color.b)/3.0;
}*/
#define distribute(x,dev,sigma) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))
#define distribute2(x,dev,sigma) (1.0-abs(x-dev)*sigma)
float nlmeans(ivec2 coords) {
    float processed = 0.0;
    float weights = 0.0;
    float noisefactor = clamp((textureBicubicHardware(NoiseMap, vec2(gl_FragCoord.xy)/vec2(SIZE)).r)*0.55*ISOFACTOR,0.0005,1.0);
    noisefactor*=noisefactor;
    noisefactor*=0.6;
    #if TONEMAPED == 1
    vec2 nm = (vec2(textureSize(InputBuffer, 0)));
    #endif
    for(int i = -KERNEL; i <= KERNEL; i++) {
        for(int j = -KERNEL; j <= KERNEL; j++) {
            float dist = distribute2(float(i),0.0,1.0/float(KERNEL))*distribute2(float(j),0.0,1.0/float(KERNEL));
            ivec2 patchCoord = coords + ivec2(i, j);
            float sigma = (0.01*0.5 + noisefactor*0.25);
            float w;
            #if TONEMAPED == 1
            w = distribute((textureBicubicHardware(ToneMap, vec2(gl_FragCoord.xy)/nm).r), (textureBicubicHardware(ToneMap, vec2(patchCoord)/nm).r), sigma)*dist;
            #else
            w = distribute(luminocity(texelFetch(InputBuffer, coords,0).rgb), luminocity(texelFetch(InputBuffer, patchCoord,0).rgb), sigma)*dist;
            #endif
            processed += w * luminocity(texelFetch(InputBuffer, patchCoord,0).rgb);
            weights += w;
        }
    }
    return processed / weights;
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
    //float noisefactor = clamp(textureLinear(NoiseMap, vec2(xy)/vec2(size)).r,0.0005,0.6);
    //Output = vec3(noisefactor*1.9);
}