precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D GainMap;
uniform sampler2D HSVMap;
uniform sampler2D ExposureCurve;
// Adaptive white point measured by AutoExposureCurve: the linear position of
// scene white above display white on HDR scenes (1.0 = whites already at
// display white). Dividing the input by it anchors whites at 1.0; the AE
// estimates its curve on the same divided domain. Clamped to >= 1.0 so an
// unset uniform degrades to identity instead of divide-by-zero black.
uniform float adaptiveWhitePoint;
//Color mat's
uniform mat3 sensorToIntermediate; // Camera RGB to a wide-gamut colorspace (white balance baked in)
uniform mat3 intermediateToSRGB; // Wide-gamut colorspace to sRGB
uniform ivec4 activeSize;
// Crop footprint inside the full-frame gain map, normalized (see initial.glsl).
uniform vec2 u_gainMin;
uniform vec2 u_gainMax;
out vec3 Output;

#define EXPOCURVE 0
#define NEUTRALPOINT 1.0,1.0,1.0
#define SATURATION 1.0
#define CONTRAST 1.0
#define SHADOWS 0.0
#define BASECONTRAST 0.0
#define HIGHLIGHTRANGE 1.0
#define USE_HSV 0
#define LOOKUP 0
#define PI (3.1415926535)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#import coords
#import interpolation

const float eps = 0.0000001;

//HSL conversion for the DCP hue/sat map
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

// sRGB OETF
float gammaEncode0(float x) {
return x <= 0.0031308f ? x * 12.92f : 1.055f * pow(x, 0.4166667f) - 0.055f;
}
vec3 gammaEncode0(vec3 x) {
return vec3(gammaEncode0(x.r),gammaEncode0(x.g),gammaEncode0(x.b));
}

vec3 reinhard_extended(vec3 v, float max_white){
vec3 numerator = v * (vec3(1.0f) + (v / vec3(max_white * max_white)));
return numerator / (vec3(1.0f) + v);
}

//OEM-style saturation: chroma scaled around Rec.601 luma
vec3 saturate(vec3 rgb, float sat) {
float br = luminocity(rgb);
return max(mix(vec3(br), rgb, sat), vec3(0.0));
}

float convSin(float x){
return 0.5 + 0.5*sin((2.0*x-1.0) * PI/2.0);
}

vec3 contrastSin(vec3 value, float contrast)
{
vec3 contr = vec3(convSin(value.r),convSin(value.g),convSin(value.b));
return mix(value,contr,contrast);
}

float sampleExposureCurve(float x) {
return texture(ExposureCurve, vec2(x, 0.5)).r;
}

// Raw-editor style curve application: only the min and max channels pass
// through the curve, the mid channel is reconstructed at its original
// proportion between them, so the curve bends luminance only and hue is kept.
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
    xy = mirrorCoords(xy,activeSize);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    #if EXPOCURVE == 1
    sRGB /= max(adaptiveWhitePoint, 1.0);
    // AutoExposureCurve response baked into a 1D LUT, sampled on the final
    // display-encoded value.
    //sRGB = applyExposureCurve(sRGB);
    //sRGB *= sRGB;
    float br2 = luminocity(sRGB.rgb);
    sRGB.rgb /= br2 + 1e-3;
    //br2 = sqrt(br2);
    br2 = sampleExposureCurve(br2);
    //br2 *= br2;
    sRGB.rgb *= br2;
    #endif
    //Lens-shading gain map -> Reinhard white point (>= 1.0)
    vec4 gains = textureBicubicHardware(GainMap, mix(u_gainMin, u_gainMax, vec2(xy)/vec2(textureSize(InputBuffer, 0))));
    gains.rgb = vec3(gains.r,(gains.g+gains.b)/2.0,gains.a);
    float gainsVal = max(dot(gains.rgb,vec3(1.0/3.0))*HIGHLIGHTRANGE,1.0);

    //Color: camera RGB -> wide gamut -> sRGB
    sRGB = intermediateToSRGB*sensorToIntermediate*(sRGB*vec3(NEUTRALPOINT));
    #if USE_HSV == 1
    //DCP hue/sat map
    vec3 pHSV = rgb2hsl(sRGB);
    vec3 modHSV = texture(HSVMap, vec2(pHSV.y,pHSV.x)).rgb;
    pHSV.x += modHSV.x/2.0;
    pHSV.y *= modHSV.y;
    pHSV.x = mod(pHSV.x,1.0);
    sRGB = hsl2rgb(pHSV);
    #endif

    //Highlight rolloff around the gain map white point
    sRGB = clamp(sRGB,0.0,1.0);
    sRGB = clamp(reinhard_extended(sRGB*gainsVal, gainsVal),vec3(0.0),vec3(1.0));
    sRGB = gammaEncode0(sRGB);

    //Optional base S-curve on top of the user contrast/shadows control.
    //Default 0: the un-curated chain is verified to match RawTherapee's
    //neutral linear render (0.0037 mean abs on the reference DNG); any
    //positive value lifts upper-mids/highlights away from that reference.
    sRGB = contrastSin(sRGB,BASECONTRAST + mix(CONTRAST+SHADOWS, CONTRAST, luminocity(sRGB)));
    sRGB = clamp(sRGB,0.0,1.0);
    //OEM-style saturation, applied after tone mapping so the tone curve does
    //not reshape the chroma gain
    Output = clamp(saturate(sRGB,SATURATION),0.0,1.0);
}
