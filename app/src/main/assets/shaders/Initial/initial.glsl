precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D TonemapTex;
uniform sampler2D GammaCurve;
uniform sampler2D LookupTable;
uniform sampler2D FusionMap;
uniform sampler2D IntenseCurve;
uniform sampler2D ExposureCurve;
// Adaptive white point measured by AutoExposureCurve: the linear position of
// scene white above display white on HDR scenes (1.0 = whites already at
// display white). Dividing the input by it anchors whites at 1.0; the AE
// estimates its curve on the same divided domain.
uniform float adaptiveWhitePoint;
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
// Tiled rendering origin (image coords of this tile's row 0) and the full
// input size for map UVs. (0,0)+input size on the legacy path: identical.
uniform ivec2 u_tileOrigin;
uniform vec2 u_fullSize;

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
#define EXPOCURVE 0
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

// sRGB EOTF (display encoded -> linear). Must match the linearization used by
// the gain-map shader so the HDR rendition is consistent with the SDR base.
float srgbToLinear(float c) {
    c = clamp(c, 0.0, 1.0);
    if (c <= 0.04045) return c / 12.92;
    return pow((c + 0.055) / 1.055, 2.4);
}
vec3 srgbToLinear(vec3 c) {
    return vec3(srgbToLinear(c.r), srgbToLinear(c.g), srgbToLinear(c.b));
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

vec3 applyColorSpace(vec3 pRGB,float tonemapGain, float gainsVal){
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
    //br = clamp(reinhard_extended(br*gainsVal,max(1.0,gainsVal)),0.0,1.0f);
    //br = clamp(reinhard_extended(br*tonemapGain,max(1.0,tonemapGain)),0.0,1.0f);

    // --- SDR display path (unchanged) ---
    vec3 pRGBsdr = clamp(pRGB*mix(tonemapGain,1.0,LTMMIX), 0.0,1.0);
    pRGBsdr = clamp(reinhard_extended(pRGBsdr*gainsVal,max(1.0,gainsVal)),vec3(0.0),vec3(1.0));
    pRGBsdr = gammaCorrectPixel2(pRGBsdr);
    pRGBsdr = tonemap(pRGBsdr, mix(1.0,tonemapGain,LTMMIX));
    pRGBsdr = mix(pRGBsdr*pRGBsdr*pRGBsdr*TONEMAPX3 + pRGBsdr*pRGBsdr*TONEMAPX2 + pRGBsdr*TONEMAPX1, pRGBsdr, min(pRGBsdr*0.8+0.55,1.0));

    return pRGBsdr;
}

float getGain(vec2 coordsShift){
    vec2 fusionSize = vec2(textureSize(FusionMap, 0));
    vec2 inputSize = u_fullSize;
    vec2 baseCoord = (gl_FragCoord.xy + vec2(u_tileOrigin) + coordsShift) / inputSize;
    float ingain = texture(FusionMap, baseCoord).r;
    //float ingain = texelFetch(FusionMap, xy, 0).r;
    /*if(ingain > 0.0){
        ingain = 1.0/ingain;
    } else ingain = -ingain;*/
    return ingain;
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

// Soft clamp: identity below SOFTKNEE, smooth C1 rolloff towards 1.0 above it.
// Preserves highlight detail instead of hard-clipping to flat white.
#define SOFTKNEE 0.8
float softClip(float x) {
    x = max(x, 0.0);
    if (x <= SOFTKNEE) return x;
    float t = x - SOFTKNEE;
    float s = 1.0 - SOFTKNEE;
    return SOFTKNEE + s * t / (t + s);
}
vec3 softClip(vec3 v) {
    return vec3(softClip(v.r), softClip(v.g), softClip(v.b));
}

// ---------------------------------------------------------------------------
// Highlight desaturation (alternative to softClip above)
//
// Fixes per-channel clipping artifacts (colour rings on gradients, warm bands
// on warm->white transitions) caused by each channel reaching the ceiling at a
// different scene luminance.
//
// Phase 1 (DESAT_START .. 1.0): in-range roll toward neutral. The max channel
//   is preserved exactly, the lower channels are lifted toward it, so
//   saturation falls smoothly instead of snapping at the ceiling.
//   DESAT_AMOUNT keeps some colour in reserve for phase 2.
//
// Phase 2 (mx > 1.0): hue-preserving compression of over-range values down to
//   1.0. base = rgb/mx holds the ratios, K is the mx at which the dimmest
//   channel would also reach 1.0, and the smoothstep rolls the pixel to white
//   over that interval instead of clipping channel by channel.
//
// OKLAB_HUE_LOCK: a straight RGB line to white does not preserve *perceived*
//   hue - the Abney effect shifts it by up to ~9.5 deg (worst on orange), well
//   above the ~2-3 deg visibility threshold. With the lock enabled the roll
//   follows an arc that holds the Oklab hue angle constant. Costs two cube
//   roots and an atan per affected pixel; only pixels above DESAT_START pay
//   it. Set to 0 to fall back to the cheap straight line.
//
//   Caveat: the Oklab matrices assume linear sRGB. At this point in the
//   pipeline the signal has already been tone-mapped, so the transform is an
//   approximation rather than colorimetrically exact.
//
// DESAT_START   - luminance at which phase 1 begins.
// DESAT_AMOUNT  - how far phase 1 is allowed to pull toward neutral by mx=1.0.
// DESAT_DENSITY - shape of the ramp, not its endpoint. Higher values keep
//                 chroma near full for longer then fall off faster; 3.0-4.0
//                 is the practical ceiling (steeper slopes reintroduce the
//                 visible edge this fix exists to remove).
#define DESAT_START     0.92
#define DESAT_AMOUNT    0.6
#define DESAT_DENSITY   3.0
#define OKLAB_HUE_LOCK  1

#if OKLAB_HUE_LOCK
vec3 rgbToOklab(vec3 c) {
    float l = dot(c, vec3(0.4122214708, 0.5363325363, 0.0514459929));
    float m = dot(c, vec3(0.2119034982, 0.6806995451, 0.1073969566));
    float s = dot(c, vec3(0.0883024619, 0.2817188376, 0.6299787005));
    vec3 lms = pow(max(vec3(l, m, s), vec3(0.0)), vec3(1.0 / 3.0));
    return vec3(
        dot(lms, vec3(0.2104542553,  0.7936177850, -0.0040720468)),
        dot(lms, vec3(1.9779984951, -2.4285922050,  0.4505937099)),
        dot(lms, vec3(0.0259040371,  0.7827717662, -0.8086757660))
    );
}

vec3 oklabToRgb(vec3 lab) {
    float l_ = lab.x + 0.3963377774 * lab.y + 0.2158037573 * lab.z;
    float m_ = lab.x - 0.1055613458 * lab.y - 0.0638541728 * lab.z;
    float s_ = lab.x - 0.0894841775 * lab.y - 1.2914855480 * lab.z;
    vec3 lms = vec3(l_, m_, s_);
    lms = lms * lms * lms;
    return vec3(
        dot(lms, vec3( 4.0767416621, -3.3077115913,  0.2309699292)),
        dot(lms, vec3(-1.2684380046,  2.6097574011, -0.3413193965)),
        dot(lms, vec3(-0.0041960863, -0.7034186147,  1.7076147010))
    );
}
#endif

// Roll a colour toward white by amount s, holding the perceptual hue constant.
vec3 rollToWhite(vec3 base, float s) {
    vec3 straight = mix(base, vec3(1.0), s);

#if OKLAB_HUE_LOCK
    // Take lightness and chroma magnitude from the straight path, but re-impose
    // the original hue angle so the arc does not drift toward a neighbouring hue.
    vec3  labBase = rgbToOklab(max(base, vec3(1e-6)));
    float chBase  = length(labBase.yz);
    if (chBase < 1e-5) return straight;          // already neutral, nothing to hold

    vec3  lab = rgbToOklab(max(straight, vec3(1e-6)));
    float ch  = length(lab.yz);
    vec2  dir = labBase.yz / chBase;             // unit vector at the original hue
    lab.yz    = ch * dir;

    return oklabToRgb(lab);
#else
    return straight;
#endif
}

vec3 desaturateHighlights(vec3 rgb) {
    float mx = max(rgb.r, max(rgb.g, rgb.b));

    // Phase 1 - smooth approach to white below the ceiling.
    // Nothing has clipped here yet, so the ramp is shaped by DESAT_DENSITY to
    // give back as much of the still-intact chroma as smoothness allows.
    if (mx > DESAT_START) {
        float s1 = smoothstep(DESAT_START, 1.0, min(mx, 1.0));
        s1 = pow(s1, DESAT_DENSITY);
        rgb = rollToWhite(rgb / mx, s1 * DESAT_AMOUNT) * mx;
    }

    // Phase 2 - hue-preserving roll-off of over-range values.
    if (mx > 1.0) {
        vec3  base  = rgb / mx;
        float bmin  = min(base.r, min(base.g, base.b));
        // Clamp K away from 1.0: a perfectly neutral pixel gives bmin == 1.0,
        // which would collapse the smoothstep interval and yield NaN.
        float K     = max(1.0 / max(bmin, 1e-6), 1.02);
        float start = max(0.97, 1.0 - (K - 1.0) * 0.3);
        float s2    = smoothstep(start, K, mx);
        rgb = rollToWhite(base, s2);
    }

    return max(rgb, vec3(0.0));
}

float sampleExposureCurve(float x) {
    return texture(ExposureCurve, vec2(x, 0.5)).r;
}

// Raw-editor style curve application: only the min and max channels pass
// through the curve, the mid channel is reconstructed at its original
// proportion between them. A per-channel application of a non-linear curve
// changes the channel ratios and drifts hue on saturated colours; this keeps
// the in-pixel channel structure intact, so the curve bends luminance only.
vec3 applyExposureCurve(vec3 rgb) {
    float mn  = min(rgb.r, min(rgb.g, rgb.b));
    float mx  = max(rgb.r, max(rgb.g, rgb.b));
    float mid = dot(rgb, vec3(1.0)) - mn - mx;
    float newMn  = sampleExposureCurve(mn);
    float newMx  = sampleExposureCurve(mx);
    float y      = (mid - mn) / max(mx - mn, 1e-6);
    float newMid = newMn + (newMx - newMn) * y;
    return vec3(
        mix(mix(newMid, newMx, float(rgb.r == mx)), newMn, float(rgb.r == mn)),
        mix(mix(newMid, newMx, float(rgb.g == mx)), newMn, float(rgb.g == mn)),
        mix(mix(newMid, newMx, float(rgb.b == mx)), newMn, float(rgb.b == mn)));
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    // Tiled rendering: mirror in absolute image coords, then shift back to
    // tile-relative buffer rows (legacy path: origin is zero, identical).
    // Mirroring tile-relative coords reflects the first two rows of every
    // non-top tile.
    xy = mirrorCoords(xy + u_tileOrigin, activeSize) - u_tileOrigin;
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    #if EXPOCURVE == 1
    // Adaptive white point: divide the input by the measured scene white so
    // whites anchor at 1.0 before the SDR tone chain. Without it the chain's
    // clamps (applyColorSpace, the final output clamp) destroy the over-range
    // highlight detail before the exposure curve could fold it in.
    sRGB /= adaptiveWhitePoint;
    #endif
    vec3 t;
    //float tonemapGain = textureBicubic(FusionMap, vec2(gl_FragCoord.xy)/vec2(textureSize(InputBuffer, 0))).r*50.0;

    float tonemapGain = 1.f;
    #if FUSION == 1
    // Guided upsampling with Gaussian-weighted linear model
    //float momentX = 0.0, momentY = 0.0, momentX2 = 0.0, momentXY = 0.0;
    vec4 moments = vec4(0.0);
    const float sigma = 1.2;
    const float sigmaSq2 = 2.0 * sigma * sigma;
    for (int i = -2; i <= 2; i++) {
        for (int j = -2; j <= 2; j++) {
            // Average lightness over 2x2 block to match FusionMap resolution
            vec2 offset = vec2(float(i), float(j));
            float lightness = dot(texelFetch(InputBuffer, xy + ivec2(i, j), 0).rgb, vec3(1.0/3.0));
            //float gain = texelFetch(FusionMap, (xy + ivec2(i, j))/2, 0).r;//getGain(offset);
            float gain = getGain(offset);
            //momentX += lightness;
            //momentY += gain;
            //momentX2 += lightness * lightness;
            //momentXY += lightness * gain;
            moments += vec4(lightness, gain, lightness * lightness, lightness * gain);
        }
    }
    float invWs = 1.0 / 25.0;
    moments *= invWs; // Normalize by the number of samples (5x5)
    float meanX = moments.x;
    float meanY = moments.y;
    float covXY = moments.w - meanX * meanY;
    float varX = moments.z - meanX * meanX;
    // Handle zero variance case with epsilon for stability
    float a = covXY / (max(varX, 0.0) + 3e-04);
    vec2 gainAB = vec2(a, meanY - a * meanX);
    //vec2 gainAB = texture(FusionMap, vec2(gl_FragCoord.xy)/vec2(textureSize(InputBuffer, 0))).rg * FUSIONGAIN;
    tonemapGain =  gainAB.r * ((luminocity(sRGB))) + gainAB.g;
    //tonemapGain = mix(1.0,tonemapGain,texture(IntenseCurve, vec2(dot(sRGB.rgb,vec3(1.0/3.0)),0.0)).r);
    //tonemapGain = max(tonemapGain, 0.5);
    #endif
    float br = (sRGB.r+sRGB.g+sRGB.b)/3.0;
    vec4 gains = textureBicubicHardware(GainMap, (vec2(xy) + vec2(u_tileOrigin)) / u_fullSize);
    gains.rgb = vec3(gains.r,(gains.g+gains.b)/2.0,gains.a);
    float gainsVal = dot(gains.rgb,vec3(1.0/3.0));
    #if EXPOCURVE == 1
    // AutoExposureCurve response baked into a 1D LUT: gamma lift -> gain ->
    // extended Reinhard -> gamma lift -> adaptive highlight shoulder (the
    // former AutoExposure pass, fused). Applied min/max through the curve with
    // the mid channel interpolated in between (see applyExposureCurve).
    //sRGB.rgb = applyExposureCurve((sRGB.rgb));
    //sRGB *= sRGB;
    float br2 = luminocity(sRGB.rgb);
    sRGB.rgb /= br2 + 1e-3;
    //br2 = sqrt(br2);
    br2 = sampleExposureCurve(br2);
    //br2 *= br2;
    sRGB.rgb *= br2;
    #endif
    sRGB = applyColorSpace(sRGB,tonemapGain, gainsVal);
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
    //float noiseO = (NOISEO*NOISEO)*0.25;
    //noiseO = min(noiseO,0.25);
    //Output = clamp((sRGB-noiseO)/(vec3(1.0)-noiseO),0.0,1.0);
    //Output = softClip(sRGB);
    //Output = desaturateHighlights(sRGB);
    Output = clamp(sRGB,0.0,1.0);
    #if POSTLUT == 1
        Output = postlookup(Output);
    #endif


}
