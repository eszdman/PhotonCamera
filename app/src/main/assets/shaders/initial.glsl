precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D TonemapTex;
uniform sampler2D GammaCurve;
uniform sampler2D LookupTable;
uniform sampler2D FusionMap;
uniform sampler2D IntenseCurve;
uniform sampler2D GainMap;
uniform sampler2D HSVMap;
uniform sampler2D PostLut;
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
out vec4 Output;
//#define x1 2.8114
//#define x2 -3.5701
//#define x3 1.6807
//CSEUS Gamma
//1.0 0.86 0.76 0.57 0.48 0.0 0.09 0.3
//0.999134635 0.97580 0.94892548 0.8547916 0.798550103 0.0000000 0.29694557 0.625511972
#define INSIZE 1,1
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
#define FUSIONGAIN 1.0
#define FUSION 0
#define ULTRAHDR 0
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define MINP 1.0
#define NOISEO 0.0
#define NOISES 0.0
#define LUT 0
#define CONTRAST 1.0
#define SHADOWS 0.0
#define USE_HSV 0
#define POSTLUT 0
#define POSTLUTSIZE 64.0
#define POSTLUTSIZETILES 8.0
#define FUSIONNORM 64.0
#define VIGNETTE 0.0
#define LTMMIX 0.0
#import coords
#import interpolation
#import gaussian


vec3 postlookup(in vec3 textureColor) {
    textureColor = clamp(textureColor, 0.0, 1.0);

    //0.0 - 63.0
    highp float blueColor = textureColor.b * (float(POSTLUTSIZE)-1.0); //63.0;

    highp vec2 quad1;
    quad1.y = floor(floor(blueColor) / POSTLUTSIZETILES);
    quad1.x = floor(blueColor) - (quad1.y * POSTLUTSIZETILES);

    highp vec2 quad2;
    quad2.y = floor(ceil(blueColor) / POSTLUTSIZETILES);
    quad2.x = ceil(blueColor) - (quad2.y * POSTLUTSIZETILES);

    highp vec2 texPos1;
    texPos1.x = (quad1.x / POSTLUTSIZETILES) + 0.5/(POSTLUTSIZE*POSTLUTSIZETILES) + ((1.0/(POSTLUTSIZETILES) - 1.0/(POSTLUTSIZE*POSTLUTSIZETILES)) * textureColor.r);
    texPos1.y = (quad1.y / POSTLUTSIZETILES) + 0.5/(POSTLUTSIZE*POSTLUTSIZETILES) + ((1.0/(POSTLUTSIZETILES) - 1.0/(POSTLUTSIZE*POSTLUTSIZETILES)) * textureColor.g);

    highp vec2 texPos2;
    texPos2.x = (quad2.x / POSTLUTSIZETILES) + 0.5/(POSTLUTSIZE*POSTLUTSIZETILES) + ((1.0/(POSTLUTSIZETILES) - 1.0/(POSTLUTSIZE*POSTLUTSIZETILES)) * textureColor.r);
    texPos2.y = (quad2.y / POSTLUTSIZETILES) + 0.5/(POSTLUTSIZE*POSTLUTSIZETILES) + ((1.0/(POSTLUTSIZETILES) - 1.0/(POSTLUTSIZE*POSTLUTSIZETILES)) * textureColor.g);

    //Tile 1
    highp vec3 newColor1 = texture(PostLut, texPos1).rgb;
    //Tile 2
    highp vec3 newColor2 = texture(PostLut, texPos2).rgb;

    highp vec3 newColor = (mix(newColor1, newColor2, fract(blueColor)));
    return newColor;
}
vec3 tricubiclookup(in vec3 xyzIn){
    float res = float(POSTLUTSIZE)-1.0;
    xyzIn*=res;
    vec3 floating = fract(xyzIn);
    vec3 inv = floor(xyzIn) + floating*floating*(3.-2.*floating);
    return postlookup((inv-.5) / res);
}

float gammaEncode(float x) {
    //return 1.055 * sqrt(x+EPS) - 0.055;
    return (GAMMAX1*x+GAMMAX2*x*x+GAMMAX3*x*x*x);
}
float gammaEncode0(float x) {
return x <= 0.0031308f ? x * 12.92f : 1.055f * pow(x, 0.4166667f) - 0.055f;
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

    highp vec3 newColor1 = texture(LookupTable, texPos1).rgb;
    highp vec3 newColor2 = texture(LookupTable, texPos2).rgb;

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
/*
vec3 tonemap(vec3 rgb, float gain) {
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
    //vec4 toneMapCoeffs = vec4(-0.7836f, 0.8469f, 0.943f, 0.0209f);
    minmax = pow(minmax, vec2(3.f)) * toneMapCoeffs.x +
    pow(minmax, vec2(2.f)) * toneMapCoeffs.y +
    minmax * toneMapCoeffs.z +
    toneMapCoeffs.w;
    minmax *= gain;
    //minmax.r = texture(TonemapTex,vec2(minmax.r,0.5f)).r;
    //minmax.g = texture(TonemapTex,vec2(minmax.g,0.5f)).r;

    //minmax = mix(minmax, minmaxsin, 0.9f);

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
}*/

vec3 tonemap(vec3 rgb, float gain) {
    float r = rgb.r;
    float g = rgb.g;
    float b = rgb.b;

    float min_val = min(r, min(g, b));
    float max_val = max(r, max(g, b));
    float mid_val = dot(rgb, vec3(1.0)) - min_val - max_val;

    vec2 minmax_in = vec2(min_val, max_val);
    vec2 minmax = minmax_in * minmax_in * minmax_in * toneMapCoeffs.x +
        minmax_in * minmax_in * toneMapCoeffs.y +
        minmax_in * toneMapCoeffs.z +
        toneMapCoeffs.w;
    minmax *= gain;

    float new_min = minmax.x;
    float new_max = minmax.y;

    float denom = max_val - min_val;
    float yprog = (mid_val - min_val) / (denom + 1e-10);
    float new_mid = new_min + (new_max - new_min) * yprog;

    // Branchless assignment using nested mix for each channel
    float new_r = mix(mix(new_mid, new_max, float(r == max_val)), new_min, float(r == min_val));
    float new_g = mix(mix(new_mid, new_max, float(g == max_val)), new_min, float(g == min_val));
    float new_b = mix(mix(new_mid, new_max, float(b == max_val)), new_min, float(b == min_val));

    return vec3(new_r, new_g, new_b);
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
vec3 hsv2rgb_smooth( in vec3 c ) {
    vec3 rgb = clamp( abs(mod(c.x*6.0+vec3(0.0,4.0,2.0),6.0)-3.0)-1.0, 0.0, 1.0 );
    rgb = rgb*rgb*(3.0-2.0*rgb); // cubic smoothing
    return c.z * mix( vec3(1.0), rgb, c.y);
}
const float eps = 0.0000001;

vec3 hsl2rgb( in vec3 c ) {
    vec3 rgb = clamp( abs(mod(c.x*6.0+vec3(0.0,4.0,2.0),6.0)-3.0)-1.0, 0.0, 1.0 );
    return c.z + c.y * (rgb-0.5)*(1.0-abs(2.0*c.z-1.0));
}
vec3 rgb2hsl( vec3 col ){
    float minc = min( col.r, min(col.g, col.b) );
    float maxc = max( col.r, max(col.g, col.b) );
    vec3  mask = step(col.grr,col.rgb) * step(col.bbg,col.rgb);
    vec3 h = mask * (vec3(0.0,2.0,4.0) + (col.gbr-col.brg)/(maxc-minc + eps)) / 6.0;
    return vec3( fract( 1.0 + h.x + h.y + h.z ),              // H
    (maxc-minc)/(1.0-abs(minc+maxc-1.0) + eps),  // S
    (minc+maxc)*0.5 );                           // L
}

float reinhard_mono(float v, float max_white) {
    float numerator = v * (float(1.0f) + (v / float(max_white * max_white)));
    return numerator / (float(1.0f) + v);
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
    //hsv.g *= dfsat;
    hsv.g = reinhard_mono(hsv.g*dfsat, max(1.0,dfsat*0.7));
    //hsv.g *= SATURATIONC+unscaledGaussian(abs(hsv.g),SATURATIONGAUSS)*(dfsat*1.07-1.0);
    rgb = hsv2rgb(hsv);
    rgb.r = mix((rgb.r+br)/2.0,rgb.r,SATURATIONRED);
    return rgb;
}
#define TONEMAPSWITCH (0.05)
#define TONEMAPAMP (1.0)

vec3 reinhard_extended(vec3 v, float max_white){
    vec3 numerator = v * (vec3(1.0f) + (v / vec3(max_white * max_white)));
    return numerator / (vec3(1.0f) + v);
}

vec3 reinhard_extended(vec3 v, vec3 max_white){
    vec3 numerator = v * (vec3(1.0f) + (v / vec3(max_white * max_white)));
    return numerator / (vec3(1.0f) + v);
}
float reinhard_extended(float v, float max_white){
    float numerator = v * (float(1.0f) + (v / float(max_white * max_white)));
    return numerator / (float(1.0f) + v);
}

vec3 applyColorSpace(vec3 pRGB, float tonemapGain, float gainsVal, out float linearLum){
    vec3 neutralPoint = vec3(NEUTRALPOINT);
    //pRGB = clamp(reinhard_extended(pRGB*tonemapGain,max(1.0,tonemapGain)), vec3(0.0), neutralPoint);
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
    pRGB = corr*sensorToIntermediate*(pRGB*neutralPoint);
    linearLum = luminocity(pRGB);
    vec3 pHSV = rgb2hsl(pRGB);
    #if USE_HSV == 1

    vec3 modHSV = texture(HSVMap, vec2(pHSV.y,pHSV.x)).rgb;
    pHSV.x += modHSV.x/2.0;
    pHSV.y *= modHSV.y;
    pRGB.z *= modHSV.z;
    pHSV.x = mod(pHSV.x,1.0);
    pRGB = hsl2rgb(pHSV);
    #endif
    float br = (pRGB.r+pRGB.g+pRGB.b)/3.0;
    //pRGB /= br;
    //float vignetteFactor = mix(0.0,VIGNETTE,clamp(br*100.0 - 0.01,0.0,1.0));
    float noise = sqrt(NOISES + NOISEO + 1e-8);
    //float vignetteFactor = smoothstep(0.0,min(noise, 0.1),br)*VIGNETTE;
    float vignetteFactor = (br*br/(br*br+noise*noise))*VIGNETTE;
    gainsVal = mix(float(1.0), gainsVal, 1.0);
    //br = clamp(reinhard_extended(br*gainsVal,max(1.0,gainsVal)),0.0,1.0);
    pRGB = clamp(pRGB*mix(tonemapGain,1.0,LTMMIX), 0.0,1.0);
    //pRGB = clamp(reinhard_extended(pRGB*tonemapGain,max(1.0,tonemapGain)),vec3(0.0),vec3(1.0));

    pRGB = clamp(reinhard_extended(pRGB*gainsVal,max(1.0,gainsVal)),vec3(0.0),vec3(1.0));
    //pRGB = clamp(pRGB*tonemapGain*gainsVal,vec3(0.0),vec3(1.0));

    //ISO tint correction
    //pRGB = mix(vec3(pRGB.r*0.99*(TINT2),pRGB.g*(TINT),pRGB.b*1.025*(TINT2)),pRGB,clamp(br*10.0,0.0,1.0));

    //pRGB = saturate(pRGB,br);

    pRGB = gammaCorrectPixel2(pRGB);
    pRGB = tonemap(pRGB, mix(1.0,tonemapGain,LTMMIX));
    pRGB = mix(pRGB*pRGB*pRGB*TONEMAPX3 + pRGB*pRGB*TONEMAPX2 + pRGB*TONEMAPX1,pRGB,min(pRGB*0.8+0.55,1.0));

    return pRGB;
}

float getGain(vec2 coordsShift){
    vec2 fusionSize = vec2(textureSize(FusionMap, 0));
    vec2 inputSize = vec2(textureSize(InputBuffer, 0));
    vec2 baseCoord = (gl_FragCoord.xy + coordsShift) / inputSize;
    float ingain = texture(FusionMap, baseCoord).r;
    //float ingain = texelFetch(FusionMap, xy, 0).r;
    /*if(ingain > 0.0){
        ingain = 1.0/ingain;
    } else ingain = -ingain;*/
    return ingain*FUSIONGAIN;
}
float getLm(ivec2 coordsShift){
    vec3 inrgb = texelFetch(InputBuffer, coordsShift, 0).rgb;
    return inrgb.r+inrgb.g+inrgb.b;
}

float convSin(float x){
    return 0.5 + 0.5*sin((2.0*x-1.0) * PI/2.0);
}

vec3 contrastSin(vec3 value, float contrast)
{
    vec3 contr = vec3(convSin(value.r),convSin(value.g),convSin(value.b));
    return mix(value,contr,contrast);
}

float aces(float x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy = mirrorCoords(xy,activeSize);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    vec3 t;
    //float tonemapGain = textureBicubic(FusionMap, vec2(gl_FragCoord.xy)/vec2(textureSize(InputBuffer, 0))).r*50.0;

    float tonemapGain = 1.f;
    #if FUSION == 1
        /*
    float tempV = 0.0;
    float minG,maxG;
    float minImg, maxImg;
    ivec2 xy2 = xy/2;
    float brInitial = 0.f;
    float brCorrected = 0.f;
    float blurInitial = 0.f;
    float blurCorrected = 0.f;
    for(int i = -1; i<=2;i++){
        for(int j = -1; j<=2;j++){
            //vec4 gains = textureBicubicHardware(GainMap, vec2(xy+ivec2(i,j))/vec2(textureSize(InputBuffer, 0)));
            float v = dot(texelFetch(InputBuffer, xy+ivec2(i,j), 0).rgb/vec3(NEUTRALPOINT), vec3(0.299, 0.587, 0.114));
            //v *= (gains.r+gains.g+gains.b+gains.a)/4.0;
            blurInitial += v;
            blurCorrected += v*getGain(vec2(ivec2(i,j)));
        }
    }

    float detail = dot(texelFetch(InputBuffer, xy, 0).rgb/vec3(NEUTRALPOINT),vec3(0.299, 0.587, 0.114))-blurInitial;
    float brightening = blurCorrected/(blurInitial+EPS);
    float corrected = blurCorrected + detail*brightening;
    tonemapGain =  corrected/(dot(texelFetch(InputBuffer, xy, 0).rgb/vec3(NEUTRALPOINT),vec3(0.299, 0.587, 0.114))+EPS);
    tonemapGain = mix(1.0,tonemapGain,texture(IntenseCurve, vec2(dot(sRGB.rgb,vec3(1.0/3.0)),0.0)).r);*/
    // guide filtering for gains
    /*
    float sigma = 1.0;
    float Z = 0.0;
    float gain = 0.0;
    for (int i = -3; i <= 3; i++) {
        for (int j = -3; j <= 3; j++) {
            vec3 color = texelFetch(InputBuffer, xy + ivec2(i, j), 0).rgb;
            float w = exp(-dot(color - sRGB, color - sRGB) / (2.0 * sigma * sigma));
            Z += w;
            gain += w * getGain(vec2(i, j));
        }
    }
    tonemapGain = gain / Z;
    */
    // Guided upsampling with Gaussian-weighted linear model
    /*float momentX = 0.0, momentY = 0.0, momentX2 = 0.0, momentXY = 0.0;
    float ws = 0.0;
    const float sigma = 1.2;
    const float sigmaSq2 = 2.0 * sigma * sigma;
    for (int i = -2; i <= 2; i++) {
        for (int j = -2; j <= 2; j++) {
            // Average lightness over 2x2 block to match FusionMap resolution
            vec2 offset = vec2(float(i*2), float(j*2));
            float lightness = 0.0;
            lightness += dot(texelFetch(InputBuffer, xy + ivec2(i*2, j*2), 0).rgb, vec3(1.0/3.0));
            lightness += dot(texelFetch(InputBuffer, xy + ivec2(i*2+1, j*2), 0).rgb, vec3(1.0/3.0));
            lightness += dot(texelFetch(InputBuffer, xy + ivec2(i*2, j*2+1), 0).rgb, vec3(1.0/3.0));
            lightness += dot(texelFetch(InputBuffer, xy + ivec2(i*2+1, j*2+1), 0).rgb, vec3(1.0/3.0));
            lightness *= 0.25;
            float gain = getGain(offset);
            
            // Gaussian weight based on spatial distance
            float w = exp(-float(i*i + j*j) / sigmaSq2);
            
            momentX += lightness * w;
            momentY += gain * w;
            momentX2 += lightness * lightness * w;
            momentXY += lightness * gain * w;
            ws += w;
        }
    }
    float invWs = 1.0 / ws;
    float meanX = momentX * invWs;
    float meanY = momentY * invWs;
    float covXY = momentXY * invWs - meanX * meanY;
    float varX = momentX2 * invWs - meanX * meanX;
    // Handle zero variance case with epsilon for stability
    float a = covXY / (max(varX, 0.0) + 0.0001);
    float b = meanY - a * meanX;*/
    vec2 gainAB = texture(FusionMap, vec2(gl_FragCoord.xy)/vec2(textureSize(InputBuffer, 0))).rg * FUSIONGAIN;
    tonemapGain =  gainAB.r * ((luminocity(sRGB))) + gainAB.g;
    //tonemapGain = mix(1.0,tonemapGain,texture(IntenseCurve, vec2(dot(sRGB.rgb,vec3(1.0/3.0)),0.0)).r);
    //tonemapGain = max(tonemapGain, 0.5);
    #endif
    float br = (sRGB.r+sRGB.g+sRGB.b)/3.0;
    vec4 gains = textureBicubicHardware(GainMap, vec2(xy)/vec2(textureSize(InputBuffer, 0)));
    gains.rgb = vec3(gains.r,(gains.g+gains.b)/2.0,gains.a);
    float gainsVal = dot(gains.rgb,vec3(1.0/3.0));
    float linearLum = 0.0;
    sRGB = applyColorSpace(sRGB, tonemapGain, gainsVal, linearLum);
    //sRGB = vec3(tonemapGain);
    #if LUT == 1
    //sRGB = lookup(sRGB);
    #endif
    //Rip Shadowing applied
    //br = (clamp(br-0.0008,0.0,0.007)*(1.0/0.007));
    //br*= (clamp(3.0-sRGB.r+sRGB.g+sRGB.b,0.0,0.006)*(1.0/0.006));


    //float sat2 = SATURATION2;
    //sat2*=br;
    sRGB = saturate(sRGB,SATURATION2,SATURATION);
    sRGB = contrastSin(sRGB,mix(CONTRAST+SHADOWS, CONTRAST, luminocity(sRGB)));
    #if ULTRAHDR == 1
     // The alpha channel carries the pre-tonemap linear HDR luminance. The gain
     // map stage compares it against the displayed base in linear sRGB space;
     // only positive differences recover compressed highlights. Shadows and
     // neutral regions remain unchanged by the gain map.
    Output = vec4(clamp(sRGB,0.0,1.0), linearLum);
    #else
    Output = vec4(clamp(sRGB,0.0,1.0), 0.0);
    #endif
    #if POSTLUT == 1
        Output.rgb = postlookup(Output.rgb);
    #endif
}
