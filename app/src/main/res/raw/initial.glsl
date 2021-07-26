#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D TonemapTex;
uniform sampler2D GammaCurve;
uniform sampler2D LookupTable;
uniform sampler2D FusionMap;

//uniform vec3 neutralPoint;
//uniform float saturation0;
//uniform float saturation;
#define CCT 0
//Color mat's
uniform mat3 sensorToIntermediate; // Color transform from XYZ to a wide-gamut colorspace
#if CCT != 1
uniform mat3 intermediateToSRGB; // Color transform from wide-gamut colorspace to sRGB
#endif
uniform vec4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve
uniform ivec4 activeSize;

//#define CUBE0 (10.0)
//#define CUBE1 (10.0)
//#define CUBE2 (10.0)
#if CCT == 1
uniform mat3 CUBE0;
uniform mat3 CUBE1;
uniform mat3 CUBE2;
#endif
out vec3 Output;
//#define x1 2.8114
//#define x2 -3.5701
//#define x3 1.6807
//CSEUS Gamma
//1.0 0.86 0.76 0.57 0.48 0.0 0.09 0.3
//0.999134635 0.97580 0.94892548 0.8547916 0.798550103 0.0000000 0.29694557 0.625511972
#define NEUTRALPOINT 0.0,0.0,0.0
#define SATURATION 0.0
#define SATURATION2 1.0
#define PI (3.1415926535)
#define DYNAMICBL (0.0, 0.0, 0.0)
#define PRECISION (64.0)
#define TINT (1.35)
#define TINT2 (1.0)
#define GAMMAX1 2.8586f
#define GAMMAX2 -3.1643f
#define GAMMAX3 1.2899f
#define TONEMAPX1 -0.15
#define TONEMAPX2 2.55
#define TONEMAPX3 -1.6
#define SATURATIONC 1.0
#define SATURATIONGAUSS 1.50
#define SATURATIONRED 0.7
#define EPS (0.0008)
#define FUSION 0
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define MINP 1.0
#import coords
#import interpolation
#import gaussian
float gammaEncode(float x) {
    //return 1.055 * sqrt(x+EPS) - 0.055;
    return (GAMMAX1*x+GAMMAX2*x*x+GAMMAX3*x*x*x);
}
float gammaEncode2(float x) {
    //return 1.055 * sqrt(x+EPS) - 0.055;
    return texture(GammaCurve,vec2(x - 1.0/1024.0,0.5)).r;
}

//Apply Gamma correction
vec3 gammaCorrectPixel(vec3 x) {
    //float br = (x.r+x.g+x.b)/3.0;
    //x/=br;
    //return x*(GAMMAX1*br+GAMMAX2*br*br+GAMMAX3*br*br*br);
    return (GAMMAX1*x+GAMMAX2*x*x+GAMMAX3*x*x*x);
}

vec3 gammaCorrectPixel2(vec3 rgb) {
    rgb.r = mix(gammaEncode(rgb.r),gammaEncode2(rgb.r),min(rgb.r*9.0,1.0));
    rgb.g = mix(gammaEncode(rgb.g),gammaEncode2(rgb.g),min(rgb.g*9.0,1.0));
    rgb.b = mix(gammaEncode(rgb.b),gammaEncode2(rgb.b),min(rgb.b*9.0,1.0));
    //rgb = gammaCorrectPixel(rgb);
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
    minmax = pow(minmax, vec2(3.f)) * toneMapCoeffs.x +
    pow(minmax, vec2(2.f)) * toneMapCoeffs.y +
    minmax * toneMapCoeffs.z +
    toneMapCoeffs.w;

    //minmax = mix(minmax, minmaxsin, 0.9f);

    // Rescale middle value
    float newMid;
    if (sorted.z == sorted.x) {
        newMid = minmax.y;
    } else {
        newMid = minmax.x + ((minmax.y - minmax.x) * (sorted.y - sorted.x) /
        (sorted.z - sorted.x));
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
vec3 saturate(vec3 rgb, float sat2, float sat) {
    float r = rgb.r;
    float g = rgb.g;
    float b = rgb.b;
    float br = (r+g+b)/3.0;
    float dfsat = mix(sat2,sat,br*br);
    vec3 hsv = rgb2hsv(vec3(rgb.r,rgb.g,rgb.b));
    /*if(hsv.g < 0.5-0.0){
        hsv.g *= mix(1.0,dfsat,hsv.g/(0.5-0.0));
    } else
    if(hsv.g > 0.5+0.0){
        hsv.g *= mix(dfsat,1.0,(0.7-hsv.g)/(0.5-0.0));
    }
    else
    //hsv.g *= mix(dfsat,1.0,abs(hsv.g-0.5)/0.1);
    hsv.g *= dfsat;*/
    hsv.g *= SATURATIONC+unscaledGaussian(abs(hsv.g),SATURATIONGAUSS)*(dfsat*1.07-1.0);
    rgb = hsv2rgb(hsv);
    rgb.r = mix((rgb.r+br)/2.0,rgb.r,SATURATIONRED);
    return rgb;
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
    //pRGB*=2.0;
    pRGB+=vec3(EPS);
    float br = pRGB.r+pRGB.g+pRGB.b;
    vec3 neutralPoint = vec3(NEUTRALPOINT);
    pRGB = clamp(pRGB, vec3(EPS), neutralPoint);
    #if CCT == 0
    mat3 corr = intermediateToSRGB;
    #endif
    #if CCT == 1
    mat3 corr;
    float br0 = ((pRGB.r+pRGB.g+pRGB.b))/(neutralPoint.r+neutralPoint.g+neutralPoint.b);
    if(br0 > 0.5){
        mat3 cub1 = mat3(CUBE1);
        mat3 cub2 = mat3(CUBE2);
        corr = cub1*(1.0-(br0-0.5)*2.0) + cub2*((br0-0.5)*2.0);
    } else {
        mat3 cub0 = mat3(CUBE0);
        mat3 cub1 = mat3(CUBE1);
        corr = cub0*(1.0-(br0-0.25)*4.0) + cub1*((br0-0.25)*4.0);
    }
    #endif
    pRGB = corr*sensorToIntermediate*pRGB;
    pRGB = clamp(pRGB,0.0,1.0);
    pRGB = max(pRGB-vec3(DYNAMICBL)/PRECISION,0.0);
    pRGB*=vec3(1.0)-vec3(DYNAMICBL)/PRECISION;

    //pRGB/=pRGB.r+pRGB.g+pRGB.b;
    //pRGB*=br*MINP;
    br = pRGB.r+pRGB.g+pRGB.b;
    br/=3.0;
    pRGB/=br;


    //ISO tint correction
    //pRGB = mix(vec3(pRGB.r*0.99*(TINT2),pRGB.g*(TINT),pRGB.b*1.025*(TINT2)),pRGB,clamp(br*10.0,0.0,1.0));

    //if(br>EPS){
    //float model = clamp((br-EPS)*TONEMAPAMP,0.0,1.0);
    //model*=model;
    //br=mix(br, pow(br,tonemapGain*br),model);
    //tonemapGain*=clamp((tonemapGain-1.0),0.0,50.0)*2.0 + 1.0;

    //br/=clamp((tonemapGain)-1.0,0.0,0.15)*1.0 + 1.0;
    //br*=4.0;

    //br*=(clamp(((tonemapGain)),1.00,8.0) - 1.0)*((tonemapGain*tonemapGain/16.0)*8.0000 + (tonemapGain/4.0)*-8.0000 + 1.0000) + 1.0;
    if(br < 0.99) br*=1.0 + (tonemapGain-1.0)*1.0;



    //br=mix(sqrt(br),br,0.7);
    //}
    //br=pow(br,tonemapGain);

    pRGB*=br;
    //pRGB*=mix(br,br*br*br*-0.75000000 + br*br*0.72500000 - br*1.02500000,br);
    //pRGB*=br*br*br*-0.75000000 + br*br*0.72500000 + br*1.02500000;
    pRGB = tonemap(pRGB);

    //pRGB = saturate(pRGB,br);
    pRGB = gammaCorrectPixel2(pRGB);
    pRGB = mix(pRGB*pRGB*pRGB*TONEMAPX3 + pRGB*pRGB*TONEMAPX2 + pRGB*TONEMAPX1,pRGB,min(pRGB*0.5+0.4,1.0));

    return pRGB;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy = mirrorCoords(xy,activeSize);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    vec3 t;
    //float tonemapGain = textureBicubic(FusionMap, vec2(gl_FragCoord.xy)/vec2(textureSize(InputBuffer, 0))).r*50.0;

    float tonemapGain = 1.f;
    #if FUSION == 1
    /*tonemapGain = textureBicubic(FusionMap, vec2(xy+ivec2(0,0))/vec2(textureSize(InputBuffer, 0))).r/(sRGB.r+sRGB.g+sRGB.b);
    t=texelFetch(InputBuffer, xy+ivec2(0,2), 0).rgb;
    tonemapGain += textureBicubic(FusionMap, vec2(xy+ivec2(0,2))/vec2(textureSize(InputBuffer, 0))).r/(t.r+t.g+t.b);
    t=texelFetch(InputBuffer, xy+ivec2(2,0), 0).rgb;
    tonemapGain += textureBicubic(FusionMap, vec2(xy+ivec2(2,0))/vec2(textureSize(InputBuffer, 0))).r/(t.r+t.g+t.b);
    t=texelFetch(InputBuffer, xy+ivec2(0,-2), 0).rgb;
    tonemapGain += textureBicubic(FusionMap, vec2(xy+ivec2(0,-2))/vec2(textureSize(InputBuffer, 0))).r/(t.r+t.g+t.b);
    t=texelFetch(InputBuffer, xy+ivec2(-2,0), 0).rgb;
    tonemapGain += textureBicubic(FusionMap,vec2(xy+ivec2(-2,0))/vec2(textureSize(InputBuffer, 0))).r/(t.r+t.g+t.b);
    tonemapGain = (tonemapGain/5.f)*(sRGB.r+sRGB.g+sRGB.b);*/
    /*
    t = sRGB;
    tonemapGain = textureBicubic(FusionMap, vec2(xy+ivec2(0,0))/vec2(textureSize(InputBuffer, 0))).r;
    t+=texelFetch(InputBuffer, xy+ivec2(0,1), 0).rgb;
    t+=texelFetch(InputBuffer, xy+ivec2(1,0), 0).rgb;
    t+=texelFetch(InputBuffer, xy+ivec2(0,-1), 0).rgb;
    t+=texelFetch(InputBuffer, xy+ivec2(-1,0), 0).rgb;
    tonemapGain = ((tonemapGain)/((t.r+t.g+t.b)/(5.0)))*(sRGB.r+sRGB.g+sRGB.b)*50.0;*/
    tonemapGain = texelFetch(FusionMap, ivec2(gl_FragCoord.xy/2.0), 0).r*50.0;
    #endif

    float br = (sRGB.r+sRGB.g+sRGB.b)/3.0;
    sRGB = applyColorSpace(sRGB,tonemapGain);
    sRGB = clamp(sRGB,0.0,1.0);
    //Rip Shadowing applied
    br = (clamp(br-0.0008,0.0,0.007)*(1.0/0.007));
    //br*= (clamp(3.0-sRGB.r+sRGB.g+sRGB.b,0.0,0.006)*(1.0/0.006));

    //sRGB = lookup(sRGB);
    float sat2 = SATURATION2;
    sat2*=br;
    sRGB = saturate(sRGB,sat2,SATURATION);
    Output = clamp(sRGB,0.0,1.0);

}