#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D TonemapTex;
uniform sampler2D LookupTable;
uniform sampler2D FusionMap;
uniform vec3 neutralPoint;
uniform float gain;
uniform float saturation;
//Color mat's
uniform mat3 sensorToIntermediate; // Color transform from XYZ to a wide-gamut colorspace
uniform mat3 intermediateToSRGB; // Color transform from wide-gamut colorspace to sRGB
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve
uniform ivec4 activeSize;
#define PI (3.1415926535)
#define DYNAMICBL (0.0, 0.0, 0.0)
#define PRECISION (64.0)
#define TINT (1.35)
#define TINT2 (1.0)
out vec3 Output;
//#define x1 2.8114
//#define x2 -3.5701
//#define x3 1.6807
//CSEUS Gamma
//1.0 0.86 0.76 0.57 0.48 0.0 0.09 0.3
//0.999134635 0.97580 0.94892548 0.8547916 0.798550103 0.0000000 0.29694557 0.625511972
#define x1 2.8586f
#define x2 -3.1643f
#define x3 1.2899f
#define EPS (0.0008)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#import coords
#import interpolation
float gammaEncode2(float x) {
    return 1.055 * sqrt(x+EPS) - 0.055;
}
//Apply Gamma correction
vec3 gammaCorrectPixel(vec3 x) {
    float br = (x.r+x.g+x.b)/3.0;
    x/=br;
    return x*(x1*br+x2*br*br+x3*br*br*br);
}

vec3 gammaCorrectPixel2(vec3 rgb) {
    rgb.x = gammaEncode2(rgb.x);
    rgb.y = gammaEncode2(rgb.y);
    rgb.z = gammaEncode2(rgb.z);
    return rgb;
}
vec3 lookup(in vec3 textureColor) {
    textureColor = clamp(textureColor, 0.0, 1.0);

    highp float blueColor = textureColor.b * 63.0;

    highp vec2 quad1;
    quad1.y = floor(floor(blueColor) / 8.0);
    quad1.x = floor(blueColor) - (quad1.y * 8.0);

    highp vec2 quad2;
    quad2.y = floor(ceil(blueColor) / 8.0);
    quad2.x = ceil(blueColor) - (quad2.y * 8.0);

    highp vec2 texPos1;
    texPos1.x = (quad1.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.r);
    texPos1.y = (quad1.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.g);

    highp vec2 texPos2;
    texPos2.x = (quad2.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.r);
    texPos2.y = (quad2.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.g);

    highp vec3 newColor1 = textureBicubicHardware(LookupTable, texPos1).rgb;
    highp vec3 newColor2 = textureBicubicHardware(LookupTable, texPos2).rgb;

    highp vec3 newColor = (mix(newColor1, newColor2, fract(blueColor)));
    return newColor;
}
#define TONEMAP_GAMMA (1.5)
float tonemapSin(float ch) {
    return ch < 0.0001f
    ? ch
    : 0.5f - 0.5f * cos(pow(ch, 1.0/TONEMAP_GAMMA) * PI);
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
#define TONEMAP_CONTRAST (1.3)
vec3 brightnessContrast(vec3 value, float brightness, float contrast)
{
    return (value - 0.5) * contrast + 0.5 + brightness;
}
#define TONEMAPSWITCH (0.05)
#define TONEMAPAMP (1.0)
vec3 applyColorSpace(vec3 pRGB,float tonemapGain){
    /*float grmodel = clamp(pRGB.g-0.8,0.0,0.2)*5.0;
    grmodel*=grmodel;
    float br = pRGB.r+pRGB.g+pRGB.b;
    br/=3.0;
    br = mix(br,min(pRGB.r,pRGB.b),grmodel);
    pRGB*=br;*/

    pRGB = clamp(pRGB+vec3(EPS), vec3(EPS), neutralPoint);
    pRGB = clamp(intermediateToSRGB*sensorToIntermediate*pRGB,0.0,1.0);
    pRGB = tonemap(pRGB);
    pRGB-=vec3(DYNAMICBL)/PRECISION;
    pRGB*=vec3(1.0)-vec3(DYNAMICBL)/PRECISION;
    float br = pRGB.r+pRGB.g+pRGB.b;
    br/=3.0;
    pRGB/=br;
    //ISO tint correction
    //pRGB = mix(vec3(pRGB.r*0.99*(TINT2),pRGB.g*(TINT),pRGB.b*1.025*(TINT2)),pRGB,clamp(br*10.0,0.0,1.0));

    //float tonebl = 1.0/(1.0+exp(-5.0*TONEMAP_CONTRAST*(-0.5)));
    //float tonewl = 1.0/(1.0+exp(-5.0*TONEMAP_CONTRAST*(0.5)));
    //br = 1.0/(1.0+exp(-5.0*TONEMAP_CONTRAST*(br-0.5)));
    //br-=tonebl;
    //br/=(tonewl-tonebl);
    //br = 0.8*(1.0-exp(-7.0*pow(br,1.26)))/(1.0-exp(-7.0));

    /*if(tonemapGain-1.0 > 0.0){
        tonemapGain=(tonemapGain-1.0)*1.5 + 1.0;
    } else {
        tonemapGain=(tonemapGain-1.0)*-0.5 + 1.0;
    }*/

    //br=mix(br,sqrt(br),tonemapGain-1.0);

    //if(br>EPS){
        float model = clamp((br-EPS)*TONEMAPAMP,0.0,1.0);
        //model*=model;
        //br=mix(br, pow(br,tonemapGain*br),model);
    //tonemapGain*=clamp((tonemapGain-1.0),0.0,50.0)*2.0 + 1.0;
    br*=clamp((tonemapGain)-1.0,0.0,0.15)*1.0 + 1.0;
    //br*=4.0;
    br*=clamp(((tonemapGain)),1.00,50.0)*1.0;
    //}
    //br=pow(br,tonemapGain);

    pRGB*=br;
    //float brmodel = clamp(br-0.8,0.0,0.2)*5.0;
    //brmodel*=brmodel;
    //pRGB=mix(pRGB*1.67,pRGB,brmodel);




    //pRGB*=tonemapGain;
    //pRGB*=exposing;

    pRGB = gammaCorrectPixel2(pRGB);
    //pRGB = mix(pRGB,tonemap(pRGB),clamp(abs(1.0-tonemapGain)/2.0,0.0,1.0));
    //return brightnessContrast(pRGB,0.0,1.018);
    return pRGB;
    //return gammaCorrectPixel2(brightnessContrast((clamp(intermediateToSRGB*pRGB,0.0,1.0)),0.0,1.018));
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
    hsv.g = clamp(hsv.g*(saturation),0.,1.0);
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
    xy = mirrorCoords(xy,activeSize);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    float tonemapGain = textureBicubic(FusionMap, vec2(gl_FragCoord.xy)/vec2(textureSize(InputBuffer, 0))).r*50.0;
    //tonemapGain = mix(1.f,tonemapGain,1.5);

    float br = (sRGB.r+sRGB.g+sRGB.b)/3.0;
    sRGB = applyColorSpace(sRGB,tonemapGain);
    sRGB = clamp(sRGB,0.0,1.0);
    //Rip Shadowing applied
    br = (clamp(br-0.0004,0.0,0.002)*(1.0/0.002));
    br*= (clamp(3.0-sRGB.r+sRGB.g+sRGB.b,0.0,0.006)*(1.0/0.006));
    //sRGB = lookup(sRGB);
    sRGB = saturate(sRGB,br);
    sRGB = clamp(sRGB,EPS,1.0);
    Output = sRGB;
}