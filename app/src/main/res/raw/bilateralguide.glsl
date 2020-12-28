#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D NoiseMap;
uniform int kernel;
uniform float isofactor;
uniform ivec2 size;
uniform ivec2 tpose;
#define M_PI 3.1415926535897932384626433832795
#define NRMPY (0.55)
#define EFFECTMIN (0.0005)
#define EFFECTMAX (1.0)
#define SIGMAK (0.01*0.5)
#define NOISEFACTOR (0.6)
#define FACTORK (0.35)
//#define sigma (0.05)
//const int kernel = 6;
const int window = 2;
#import interpolation
//#define luminocity(x) ((((x.r+x.g+x.b)/3.0))+0.001)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
/*float luminocity(vec3 color) {
    return (color.r+color.g+color.b)/3.0;
}*/
#define distribute(x,dev,sigma) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))

float nlmeans(ivec2 coords) {
    float processed = 0.0;
    float weights = 0.0;
    float noisefactor = clamp((textureBicubic(NoiseMap, vec2(coords)/vec2(size)).r)*NRMPY*isofactor,EFFECTMIN,EFFECTMAX);
    noisefactor*=noisefactor;
    noisefactor*=NOISEFACTOR;
    for(int i = -kernel; i < kernel; i++) {
        for(int j = -kernel; j < kernel; j++) {
            ivec2 patchCoord = coords + ivec2(i, j);
            //float w = comparePatches(patchCoord, coords,0.01*0.5 + noisefactor*0.35);
            float sigma = (SIGMAK + noisefactor*FACTORK);
            float w = distribute(luminocity(texelFetch(InputBuffer, coords,0).rgb),
            luminocity(texelFetch(InputBuffer,patchCoord,0).rgb),sigma);
            w/=((2.0 * float(window) + 1.0) * (2.0 * float(window) + 1.0));
            processed += w * luminocity(texelFetch(InputBuffer, patchCoord,0).rgb);
            weights += w;
        }
    }
    return processed / weights;
}

//////////////////////////////////////////////////////////////////////////////////
out vec3 Output;
uniform int yOffset;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 xyz = (texelFetch(InputBuffer, xy,0).rgb)+0.001;
    float br = luminocity(xyz);
    xyz/=br;
    br = nlmeans(xy);
    Output = clamp(xyz*br - 0.002,0.0,1.0);
    //float noisefactor = clamp(textureLinear(NoiseMap, vec2(xy)/vec2(size)).r,0.0005,0.6);
    //Output = vec3(noisefactor*1.9);
}