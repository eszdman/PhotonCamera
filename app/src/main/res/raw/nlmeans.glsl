#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D NoiseMap;
uniform int kernel;
uniform ivec2 size;
uniform ivec2 tpose;
#define M_PI 3.1415926535897932384626433832795

//#define sigma (0.05)
//const int kernel = 6;
const int window = 2;
#import interpolation
#define luminocity(x) ((x.r+x.g+x.b)/3.0)
/*float luminocity(vec3 color) {
    return (color.r+color.g+color.b)/3.0;
}*/
#define distribute(x,dev) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))
/*float distribute(float x, float dev) {
    return exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma);
}*/

float comparePatches(ivec2 patch2, ivec2 original,float sigma) {
    float w = 0.0;
    for(int i = -window; i < window; i++) {
        for(int j = -window; j < window; j++) {
            ivec2 offset = ivec2(i, j);
            float pCurrent = luminocity(texelFetch(InputBuffer, patch2 + offset,0).rgb);
            float oCurrent = luminocity(texelFetch(InputBuffer, original + offset,0).rgb);
            w = distribute(pCurrent, oCurrent);
        }
    }
    return w / ((2.0 * float(window) + 1.0) * (2.0 * float(window) + 1.0));
}

float nlmeans(ivec2 coords) {
    float processed = 0.0;
    float weights = 0.0;
    float noisefactor = clamp((textureBicubic(NoiseMap, vec2(coords)/vec2(size)).r-0.02)*0.8,0.0005,0.6);
    for(int i = -kernel; i < kernel; i++) {
        for(int j = -kernel; j < kernel; j++) {
            ivec2 patchCoord = coords + ivec2(i, j);
            float w = comparePatches(patchCoord, coords,noisefactor);
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
    float br = (xyz.r+xyz.g+xyz.b)/3.0;
    xyz/=br;
    br = nlmeans(xy);
    Output = (xyz*br);
    //float noisefactor = clamp(textureLinear(NoiseMap, vec2(xy)/vec2(size)).r,0.0005,0.6);
    //Output = vec3(noisefactor*1.9);
}