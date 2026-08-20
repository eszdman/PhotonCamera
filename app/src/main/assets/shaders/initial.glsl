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
#define FUSION 1
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
#define FUSIONCAP 8.0
#define FUSIONFLOOR 0.25
#define LTMCONTRASTBOOST 1.0
// Range gate for the guided-filter refit and strength of the affine refit
// deviation. The gate must be wide enough that the 5x5 window retains
// same-side statistics at an edge: if it is tightened past the point where
// only the center tap survives, the affine fit degenerates (ws -> 1 tap,
// a -> 0) and the output collapses to the raw 2x2-block-quantized map value,
// which applies the map's gain step misaligned from the true image edge by
// up to 2px - that is the sharp, blocky halo on branches. Edge rejection is
// already handled by the minLuma erosion, the range-gated overshoot clamp
// and the map-side darkCap, so this gate only needs to reject the bulk of
// the far side, not annihilate it.
#define LTMLUMASIGMA 0.12
#define LTMREFIT 0.35
#import coords
#import interpolation
#import gaussian

vec3 postlookup(in vec3 textureColor) {
    textureColor = clamp(textureColor, 0.0, 1.0);

    //0.0 - 63.0
    highp float blueColor = textureColor.b * (float(POSTLUTSIZE)-1.0); //63;
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

    //Tile1
    highp vec3 newColor1 = texture(PostLut, texPos1).rgb;
    //Tile2
    highp vec3 newColor2 = texture(PostLut, texPos2).rgb;

    return (mix(newColor1, newColor2, fract(blueColor)));
}
vec3 tricubiclookup(in vec3 xyzIn){
    float res = float(POSTLUTSIZE)-1.0;
    xyzIn*=res;
    vec3 floating = fract(xyzIn);
    vec3 inv = floor(xyzIn) + floating*floating*(3.-2.*floating);
    return postlookup((inv-.5) / res);
}

float gammaEncode(float x) { return (GAMMAX1*x+GAMMAX2*x*x+GAMMAX3*x*x*x); }
float gammaEncode0(float x) { return x <= 0.0031308f ? x * 12.92f : 1.055f * pow(x, 0.4166667f) - 0.055f; }
float gammaEncode2(float x) { return texture(GammaCurve,vec2(x - 1.0/1024.0,0.5)).r; }
vec3 gammaCorrectPixel(vec3 x) { return (GAMMAX1*x+GAMMAX2*x*x+GAMMAX3*x*x*x); }

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
    return (mix(newColor1, newColor2, fract(blueColor)));
}
#define TONEMAP_GAMMA (1.5)
float tonemapSin(float ch) { return ch < 0.0001f ? ch : 0.5f - 0.5f * cos(pow(ch, 1.0/TONEMAP_GAMMA) * PI); }
vec2 tonemapSin(vec2 ch) { return vec2(tonemapSin(ch.x), tonemapSin(ch.y)); }

vec3 tonemap(vec3 rgb, float gain) {
    float r = rgb.r; float g = rgb.g; float b = rgb.b;
    float min_val = min(r, min(g, b));
    float max_val = max(r, max(g, b));
    float mid_val = dot(rgb, vec3(1.0)) - min_val - max_val;
    vec2 minmax_in = vec2(min_val, max_val);
    vec2 minmax = minmax_in * minmax_in * minmax_in * toneMapCoeffs.x +
        minmax_in * minmax_in * toneMapCoeffs.y +
        minmax_in * toneMapCoeffs.z + toneMapCoeffs.w;
    minmax *= gain;
    float new_min = minmax.x; float new_max = minmax.y;
    float denom = max_val - min_val;
    float yprog = (mid_val - min_val) / (denom + 1e-10);
    float new_mid = new_min + (new_max - new_min) * yprog;
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
    rgb = rgb*rgb*(3.0-2.0*rgb);
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
    return vec3( fract( 1.0 + h.x + h.y + h.z ), (maxc-minc)/(1.0-abs(minc+maxc-1.0) + eps), (minc+maxc)*0.5 );
}
float reinhard_mono(float v, float max_white) {
    float numerator = v * (float(1.0f) + (v / float(max_white * max_white)));
    return numerator / (float(1.0f) + v);
}
vec3 saturate(vec3 rgb, float sat2, float sat) {
    float br = (rgb.r+rgb.g+rgb.b)/3.0;
    float dfsat = mix(sat2,sat,br*br);
    vec3 hsv = rgb2hsv(vec3(rgb.r,rgb.g,rgb.b));
    hsv.g = reinhard_mono(hsv.g*dfsat, max(1.0,dfsat*0.7));
    rgb = hsv2rgb(hsv);
    rgb.r = mix((rgb.r+br)/2.0,rgb.r,SATURATIONRED);
    return rgb;
}
vec3 reinhard_extended(vec3 v, float max_white){ vec3 n = v * (vec3(1.0f) + (v / vec3(max_white * max_white))); return n / (vec3(1.0f) + v); }
vec3 reinhard_extended(vec3 v, vec3 max_white){ vec3 n = v * (vec3(1.0f) + (v / vec3(max_white * max_white))); return n / (vec3(1.0f) + v); }
float reinhard_extended(float v, float max_white){ float n = v * (float(1.0f) + (v / float(max_white * max_white))); return n / (float(1.0f) + v); }

vec3 applyColorSpace(vec3 pRGB,float tonemapGain, float gainsVal){
    vec3 neutralPoint = vec3(NEUTRALPOINT);
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
    float noise = sqrt(NOISES + NOISEO + 1e-8);
    float vignetteFactor = (br*br/(br*br+noise*noise))*VIGNETTE;
    gainsVal = mix(float(1.0), gainsVal, 1.0);
    // Apply the LTM gain directly to the single color-transformed signal. The
    // earlier LP/HP split blurred the RAW InputBuffer but subtracted it from
    // this TRANSFORMED signal, so the detail term contained a spurious color
    // difference that was added back and slammed saturation. One consistent
    // signal has no such defect.
    vec3 pRGBBoosted = pRGB*mix(tonemapGain,1.0,LTMMIX);
    pRGB = clamp(reinhard_extended(pRGBBoosted, max(1.0, tonemapGain)), 0.0, 1.0);
    pRGB = clamp(reinhard_extended(pRGB*gainsVal,max(1.0,gainsVal)),vec3(0.0),vec3(1.0));
    pRGB = gammaCorrectPixel2(pRGB);
    pRGB = tonemap(pRGB, mix(1.0,tonemapGain,LTMMIX));
    pRGB = mix(pRGB*pRGB*pRGB*TONEMAPX3 + pRGB*pRGB*TONEMAPX2 + pRGB*TONEMAPX1, pRGB, min(pRGB*0.8+0.55,1.0));
    return pRGB;
}

// FusionMap carries the bounded fused/base LTM gain ratio in .r, notch-
// filtered edge-aware in fusionmap.glsl so it contains no texel-scale pattern.
// getGain() samples it once per 2x2 output block, at the block center, so all
// four pixels in the block share one gain prior and no even/odd interpolation
// phase can paint a 2px grid on tonal transitions. The UV is normalized
// against the full-res input size so half-res texel k covers exactly the
// output block [2k, 2k+2).
float getGain(ivec2 centerPos){
    ivec2 inputSize = textureSize(InputBuffer, 0);
    ivec2 blockBase = (centerPos / 2) * 2;
    ivec2 blockCenter = blockBase + ivec2(1, 1);
    vec2 uv = vec2(blockCenter) / vec2(inputSize);
    return texture(FusionMap, uv).r * FUSIONGAIN;
}
ivec2 clampInputPos(ivec2 pos, ivec2 inputSize) {
    return clamp(pos, ivec2(0), inputSize - ivec2(1));
}
float convSin(float x){ return 0.5 + 0.5*sin((2.0*x-1.0) * PI/2.0); }
vec3 contrastSin(vec3 value, float contrast){ vec3 contr = vec3(convSin(value.r),convSin(value.g),convSin(value.b)); return mix(value,contr,contrast); }
float aces(float x) { const float a=2.51,b=0.03,c=2.43,d=0.59,e=0.14; return clamp((x*(a*x+b))/(x*(c*x+d)+e),0.0,1.0); }

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy = mirrorCoords(xy,activeSize);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    float tonemapGain = 1.f;
    #if FUSION == 1
    // Edge-aware full-res application of the low-res gain map (guided filter).
    //
    // The map is sampled once per 2x2 output block (getGain above), so the
    // gain field carries no even/odd interpolation phase. A local affine gain
    // model gain = a*luma + b (He et al.) is then re-fit against full-res luma
    // in a 5x5 window. The window average washes out any residual pyramid
    // phase ripple left in the map (the grid on gradients), while the range
    // gate keeps the model on one side of a tonal edge (no halo). Each tap
    // reads the map at its own block center, so the windowed moments still see
    // the map's spatial variation without alternating gain phase between
    // even/odd pixels.
    float momentX = 0.0, momentY = 0.0, momentX2 = 0.0, momentXY = 0.0;
    float ws = 0.0;
    float localMinGain = FUSIONCAP;
    float localMaxGain = FUSIONFLOOR;
    const float sigma = 2.0;
    const float sigmaSq2 = 2.0 * sigma * sigma;
    const float lumaSigma = LTMLUMASIGMA;
    const float lumaSigmaSq2 = 2.0 * lumaSigma * lumaSigma;
    ivec2 inputSize = textureSize(InputBuffer, 0);
    float centerLightness = luminocity(sRGB);
    // Darkest luma in the window. Drives the gain ceiling so the shadow-lift
    // release is eroded 2px into bright regions adjacent to dark content:
    // no pixel near a dark object can lift, so no release ring can form.
    float minLuma = centerLightness;
    for (int i = -2; i <= 2; i++) {
        for (int j = -2; j <= 2; j++) {
            float lightness = luminocity(texelFetch(InputBuffer,
                                                    clampInputPos(xy + ivec2(i, j), inputSize),
                                                    0).rgb);
            minLuma = min(minLuma, lightness);
            float spatialWeight = exp(-float(i*i + j*j) / sigmaSq2);
            // Block-aligned map sample: every tap inside the same 2x2 block
            // reads the same texel, so the moments carry no even/odd phase.
            float gain = getGain(xy + ivec2(i, j));
            float lumaDelta = lightness - centerLightness;
            float rangeWeight = exp(-(lumaDelta * lumaDelta) / lumaSigmaSq2);
            // The overshoot clamp must only see gains from THIS side of a
            // tonal edge. Min/max over all taps admits the far side's gains
            // into the window, and the refit is then allowed to overshoot
            // toward them at the boundary - that overshoot is the halo.
            if (rangeWeight > 0.5) {
                localMinGain = min(localMinGain, gain);
                localMaxGain = max(localMaxGain, gain);
            }
            float w = spatialWeight * rangeWeight;
            momentX += lightness * w;
            momentY += gain * w;
            momentX2 += lightness * lightness * w;
            momentXY += lightness * gain * w;
            ws += w;
        }
    }
    // Degenerate-window guard: at a hard edge the range gate can reject every
    // tap but the center one, leaving ws ~ 1 tap's weight and a variance
    // of zero. The affine fit then produces a meaningless a and an unstable
    // mean; fall back to the raw block gain instead.
    if (ws < 0.01) {
        tonemapGain = getGain(xy);
        localMinGain = min(localMinGain, tonemapGain);
        localMaxGain = max(localMaxGain, tonemapGain);
    } else {
        float invWs = 1.0 / ws;
        float meanX = momentX * invWs;
        float meanY = momentY * invWs;
        float covXY = momentXY * invWs - meanX * meanY;
        float varX = momentX2 * invWs - meanX * meanX;
        float guideVariance = max(varX, 0.0);
        float varianceRegularizer = 0.0001 + 0.001 * max(meanX, 0.01);
        float a = clamp(covXY / (guideVariance + varianceRegularizer), -FUSIONCAP, FUSIONCAP);
        float guidedGain = meanY + (a * (centerLightness - meanX)) * LTMREFIT;
        tonemapGain = guidedGain;
    }
    // The affine refit must not overshoot the same-side map values actually
    // present in this window: that is exactly how over/under-shoots (halos)
    // appear.
    tonemapGain = clamp(tonemapGain, localMinGain, localMaxGain);
    // Two-sided gain ceiling.
    //
    // Dark side (minLuma): driven by the DARKEST luma in the window, not the
    // center pixel. A center-luma gate releases the shadow cap on
    // anti-aliased edge pixels (luma 0.15-0.45) while the adjacent dark
    // object stays capped, and that partial-release ring is the bright halo
    // around dark objects (TV on a wall, branches against sky). The window
    // minimum erodes the release 2px into the bright side, so the lift can
    // only engage in regions that contain no dark content at all. The
    // release band starts higher (0.20) and ends higher (0.55) so dark,
    // noisy shadows are not lifted into visibility.
    float darkCap = mix(FUSIONGAIN, FUSIONCAP,
            smoothstep(0.20, 0.55, minLuma));
    // Bright side (centerLightness): wherever the pixel itself is already
    // bright, fused/base > 1 is by definition cross-edge contamination from
    // a darker neighbor, never a legitimate lift - bright regions only ever
    // need compression. Capping lift at FUSIONGAIN there removes the wide,
    // soft glow that the erosion cannot reach. Gains below FUSIONGAIN pass
    // both ceilings untouched.
    float brightCap = mix(FUSIONCAP, FUSIONGAIN,
            smoothstep(0.40, 0.65, centerLightness));
    tonemapGain = min(tonemapGain, min(darkCap, brightCap));
    tonemapGain = clamp(tonemapGain, 0.25, FUSIONCAP);
    #endif
    vec4 gains = textureBicubicHardware(GainMap, vec2(xy)/vec2(textureSize(InputBuffer, 0)));
    gains.rgb = vec3(gains.r,(gains.g+gains.b)/2.0,gains.a);
    float gainsVal = dot(gains.rgb,vec3(1.0/3.0));
    sRGB = applyColorSpace(sRGB, tonemapGain, gainsVal);
    sRGB = saturate(sRGB,SATURATION2,SATURATION);
    sRGB = contrastSin(sRGB,mix(CONTRAST+SHADOWS, CONTRAST, luminocity(sRGB)) * LTMCONTRASTBOOST);
    Output = clamp(sRGB,0.0,1.0);
    #if POSTLUT == 1
        Output = postlookup(Output);
    #endif
}
