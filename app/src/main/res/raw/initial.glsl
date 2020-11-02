#version 300 es
precision highp float;
precision mediump usampler2D;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D TonemapTex;
uniform vec3 neutralPoint;
uniform float gain;
uniform float saturation;
uniform int yOffset;
uniform float exposing;
//Color mat's
uniform mat3 sensorToIntermediate; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 intermediateToSRGB; // Color transform from wide-gamut colorspace to sRGB
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve
#define PI (3.1415926535)
out vec4 Output;
//#define x1 2.8114
//#define x2 -3.5701
//#define x3 1.6807
//CSEUS Gamma
//1.0 0.86 0.76 0.57 0.48 0.0 0.09 0.3
//0.999134635 0.97580 0.94892548 0.8547916 0.798550103 0.0000000 0.29694557 0.625511972
#define x1 2.8586f
#define x2 -3.1643f
#define x3 1.2899f
float gammaEncode2(float x) {
    return (x <= 0.0031308) ? x * 12.92 : 1.055 * pow(float(x), (1.f/gain)) - 0.055;
}
//Apply Gamma correction
vec3 gammaCorrectPixel(vec3 x) {
    return (x1*x+x2*x*x+x3*x*x*x);
}

vec3 gammaCorrectPixel2(vec3 rgb) {
    rgb.x = gammaEncode2(rgb.x);
    rgb.y = gammaEncode2(rgb.y);
    rgb.z = gammaEncode2(rgb.z);
    return rgb;
}
float tonemapSin(float ch) {
    return ch < 0.0001f
    ? ch
    : 0.5f - 0.5f * cos(pow(ch, 0.8f) * PI);
}

vec2 tonemapSin(vec2 ch) {
    return vec2(tonemapSin(ch.x), tonemapSin(ch.y));
}

vec3 tonemap(vec3 rgb) {
    vec3 sorted = rgb;

    float tmp;
    int permutation = 0;

    // Sort the RGB channels by value
    if (sorted.z < sorted.y) {
        tmp = sorted.z;
        sorted.z = sorted.y;
        sorted.y = tmp;
        permutation |= 1;
    }
    if (sorted.y < sorted.x) {
        tmp = sorted.y;
        sorted.y = sorted.x;
        sorted.x = tmp;
        permutation |= 2;
    }
    if (sorted.z < sorted.y) {
        tmp = sorted.z;
        sorted.z = sorted.y;
        sorted.y = tmp;
        permutation |= 4;
    }

    vec2 minmax;
    minmax.x = sorted.x;
    minmax.y = sorted.z;

    // Apply tonemapping curve to min, max RGB channel values
    vec2 minmaxsin = tonemapSin(minmax);
    minmax = pow(minmax, vec2(3.f)) * toneMapCoeffs.x +
    pow(minmax, vec2(2.f)) * toneMapCoeffs.y +
    minmax * toneMapCoeffs.z +
    toneMapCoeffs.w;

    //minmax.x*=texelFetch(TonemapTex,ivec2(int(minmax.x*255.0),0),0).x;
    //minmax.y*=texelFetch(TonemapTex,ivec2(int(minmax.y*255.0),0),0).x;
    minmax = mix(minmax, minmaxsin, 0.4f);

    // Rescale middle value
    float newMid;
    if (sorted.z == sorted.x) {
        newMid = minmax.y;
    } else {
        float yprog = (sorted.y - sorted.x) / (sorted.z - sorted.x);
        newMid = minmax.x + (minmax.y - minmax.x) * yprog;
    }

    vec3 finalRGB;
    switch (permutation) {
        case 0: // b >= g >= r
        finalRGB.r = minmax.x;
        finalRGB.g = newMid;
        finalRGB.b = minmax.y;
        break;
        case 1: // g >= b >= r
        finalRGB.r = minmax.x;
        finalRGB.b = newMid;
        finalRGB.g = minmax.y;
        break;
        case 2: // b >= r >= g
        finalRGB.g = minmax.x;
        finalRGB.r = newMid;
        finalRGB.b = minmax.y;
        break;
        case 3: // g >= r >= b
        finalRGB.b = minmax.x;
        finalRGB.r = newMid;
        finalRGB.g = minmax.y;
        break;
        case 6: // r >= b >= g
        finalRGB.g = minmax.x;
        finalRGB.b = newMid;
        finalRGB.r = minmax.y;
        break;
        case 7: // r >= g >= b
        finalRGB.b = minmax.x;
        finalRGB.g = newMid;
        finalRGB.r = minmax.y;
        break;
    }
    return finalRGB;
}
vec3 applyColorSpace(vec3 pRGB){
    pRGB = clamp(pRGB, vec3(0.0), neutralPoint);
    pRGB = sensorToIntermediate*pRGB;
    //pRGB = tonemap(pRGB);
    return gammaCorrectPixel2(gammaCorrectPixel(clamp(intermediateToSRGB*pRGB,0.0,1.0)));
}
// Source: https://lolengine.net/blog/2013/07/27/rgb-to-hsv-in-glsl
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.f, -1.f / 3.f, 2.f / 3.f, -1.f);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    return vec3(abs(q.z + (q.w - q.y) / (6.f * d + 1.0e-10)), d / (q.x + 1.0e-10), q.x);
}
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1., 2. / 3., 1. / 3., 3.);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6. - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0., 1.), c.y);
}
const float redcorr = 0.0;
const float bluecorr = 0.0;
vec3 saturate(vec3 rgb,float model) {
    float r = rgb.r;
    float g = rgb.g;
    float b = rgb.b;
    vec3 hsv = rgb2hsv(vec3(rgb.r-r*redcorr,rgb.g,rgb.b+b*bluecorr));
    //color wide filter
    hsv.g = clamp(hsv.g*(saturation*model),0.,1.0);
    rgb = hsv2rgb(hsv);
    //rgb.r+=r*redcorr*saturation;
    //rgb.g=clamp(rgb.g,0.0,1.0);
    //rgb.b-=b*bluecorr*saturation;
    //rgb = clamp(rgb, 0.0,1.0);
    //rgb*=(r+g+b)/(rgb.r+rgb.g+rgb.b);
    return rgb;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    float br = (sRGB.r+sRGB.g+sRGB.b)/3.0;
    sRGB = applyColorSpace(sRGB);
    //Rip Shadowing applied
    br = (clamp(br-0.0018,0.0,0.003)*(1.0/0.003));
    sRGB = saturate(sRGB,br);
    sRGB = clamp(sRGB,0.0,1.0);
    Output = vec4(sRGB.r,sRGB.g,sRGB.b,1.0);
}