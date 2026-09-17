precision highp float;
precision highp sampler2D;

/*
 * Motion V2 headroom tone mapping.
 *
 * Values below 0.50 linear are untouched; above that a log-shaped shoulder
 * spreads the remaining display range according to the physical scene
 * headroom (sceneWhite). Compression is one scalar acting on a
 * max-RGB/luminance guide so channel ratios (hue) survive the shoulder, and
 * only the final part of the headroom converges to neutral display white.
 *
 * This stage replaces Initial+AutoExposure for the SDR base render: the
 * exposure multiplier arrives from the LinearExposure node (linear-histogram
 * percentiles), color is matrix-only (sensor -> ProPhoto -> sRGB with
 * white-point WB), and the lens shading GainMap plus the
 * ExposureFusionBayer2 FusionMap are applied as linear gains before the
 * curve. No cubes/CLUTs/LUTs.
 */

uniform sampler2D InputBuffer;
uniform sampler2D GainMap;
uniform sampler2D FusionMap;
//Color mat's
uniform mat3 sensorToIntermediate; // Color transform from sensor to a wide-gamut colorspace
uniform mat3 intermediateToSRGB; // Color transform from wide-gamut colorspace to sRGB
uniform float displayGain; // Linear exposure multiplier from LinearExposure
uniform float sceneWhite; // Scene headroom, 0.90*displayGain clamped to [1, sceneWhiteMax]
uniform float outputExposureScale; // Global output exposure (~-0.32 EV at 0.80)
uniform ivec4 activeSize;
// Tiled rendering origin (image coords of this tile's row 0) and the full
// input size for map UVs. (0,0)+input size on the legacy path: identical.
uniform ivec2 u_tileOrigin;
uniform vec2 u_fullSize;

#define NEUTRALPOINT 0.0,0.0,0.0
#define FUSION 0
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))

#import coords
#import interpolation

out vec4 Output;

float max3(vec3 v) {
    return max(v.r,max(v.g,v.b));
}

float luminance(vec3 c) {
    return dot(c,vec3(0.2126,0.7152,0.0722));
}

float srgbEncode(float x) {
    x=max(x,0.0);
    return x<=0.0031308
            ? 12.92*x
            : 1.055*pow(x,1.0/2.4)-0.055;
}

vec3 srgbEncode(vec3 x) {
    return vec3(
            srgbEncode(x.r),
            srgbEncode(x.g),
            srgbEncode(x.b));
}

/*
 * Bounded log-luma unsharp mask, faded out in deep shadows and highlights to
 * avoid noise, halos and highlight-edge exaggeration. The window reuses the
 * center pixel's exposure/white point (lens shading and fusion maps are low
 * frequency); the log residual itself is invariant to that scale.
 */
float localLogLumaMean(ivec2 xy, float exposure, vec3 neutralPoint) {
    ivec2 sz=textureSize(InputBuffer,0);
    float sum=0.0;
    float wsum=0.0;
    for(int oy=-2;oy<=2;oy++) {
        for(int ox=-2;ox<=2;ox++) {
            ivec2 p=clamp(xy+ivec2(ox,oy),ivec2(0),sz-ivec2(1));
            vec3 wb=max(texelFetch(InputBuffer,p,0).rgb,vec3(0.0))*neutralPoint*exposure;
            float y=max(luminance(wb),0.0);
            float r2=float(ox*ox+oy*oy);
            float w=exp(-0.55*r2);
            sum+=w*log(1.0e-4+y);
            wsum+=w;
        }
    }
    return sum/max(wsum,1.0e-6);
}

/* Linear below 0.50; above, allocate the remaining display range by a
 * log-shaped shoulder over the available scene headroom. The white point is
 * per-pixel: scaled by the same local gain (lens shading, fusion map) as the
 * exposure, so the local sensor white maps exactly to display white. */
float mapHeadroomLuminance(float y, float whitePoint) {
    const float start=0.50;
    if(y<=start) return y;

    float x=clamp(
            (y-start)/max(whitePoint-start,1.0e-6),
            0.0,
            1.0);

    const float logShape=6.0;
    float shaped=
            log(1.0+logShape*x)
            /log(1.0+logShape);

    /* Map to the inverse of the output exposure so the final SDR endpoint
     * can actually reach 1.0 after the global scale. */
    float preScaleDisplayWhite=1.0/max(outputExposureScale,1.0e-6);
    return start+(preScaleDisplayWhite-start)*shaped;
}

/* Chroma-preserving headroom compression: the curve acts on one scalar guide
 * (max of luminance and max RGB) and rescales all channels uniformly. */
vec3 mapExtendedLinearHeadroom(vec3 rgb, float whitePoint) {
    rgb=max(rgb,vec3(0.0));
    float y=max(luminance(rgb),0.0);
    float peak=max3(rgb);
    float guide=max(y,peak);
    if(guide<=1.0e-7) return rgb;

    float mappedGuide=mapHeadroomLuminance(guide,whitePoint);
    vec3 mapped=rgb*(mappedGuide/guide);

    /* Real colour is retained through most of the shoulder; only the final
     * part of the physical headroom converges to neutral display white. */
    const float start=0.50;
    float headroomPosition=clamp(
            (guide-start)/max(whitePoint-start,1.0e-6),
            0.0,
            1.0);
    float neutralMix=smoothstep(0.82,1.0,headroomPosition);
    return mix(mapped,vec3(mappedGuide),neutralMix);
}

/* sRGB cannot encode a channel above 1.0. If one saturated channel still
 * exceeds the display gamut, shrink chroma uniformly around white instead of
 * independently clipping R/G/B. */
vec3 fitDisplayGamut(vec3 rgb) {
    rgb=max(rgb,vec3(0.0));
    float peak=max3(rgb);
    if(peak<=1.0) return rgb;

    vec3 hueSafe=rgb/max(peak,1.0e-6);
    float overflow=clamp((peak-1.0)/0.25,0.0,1.0);
    return mix(hueSafe,vec3(1.0),smoothstep(0.0,1.0,overflow));
}

void main() {
    ivec2 xy=ivec2(gl_FragCoord.xy);
    // Tiled rendering: mirror in absolute image coords, then shift back to
    // tile-relative buffer rows (legacy path: origin is zero, identical).
    xy=mirrorCoords(xy+u_tileOrigin,activeSize)-u_tileOrigin;
    vec3 inColor=max(texelFetch(InputBuffer,xy,0).rgb,vec3(0.0));

    /* Fusion map guided local gain: same 5x5 linear-model fit as the
     * previous Initial stage. */
    float tonemapGain=1.0;
    #if FUSION == 1
    vec4 moments=vec4(0.0);
    ivec2 sz=textureSize(InputBuffer,0);
    for(int i=-2;i<=2;i++) {
        for(int j=-2;j<=2;j++) {
            ivec2 p=clamp(xy+ivec2(i,j),ivec2(0),sz-ivec2(1));
            vec2 offset=vec2(float(i),float(j));
            float lightness=dot(texelFetch(InputBuffer,p,0).rgb,vec3(1.0/3.0));
            float gain=texture(FusionMap,(gl_FragCoord.xy+vec2(u_tileOrigin)+offset)/u_fullSize).r;
            moments+=vec4(lightness,gain,lightness*lightness,lightness*gain);
        }
    }
    moments*=1.0/25.0;
    float meanX=moments.x;
    float meanY=moments.y;
    float covXY=moments.w-meanX*meanY;
    float varX=moments.z-meanX*meanX;
    float a=covXY/(max(varX,0.0)+3e-04);
    tonemapGain=a*luminocity(inColor)+(meanY-a*meanX);
    #endif
    /* Guard the fit: a negative slope would flip the pixel sign, a runaway
     * slope would blow the local exposure. */
    tonemapGain=clamp(tonemapGain,0.0,3.0);

    /* Lens shading gain: same bicubic sampling as the previous Initial stage. */
    vec4 gains=textureBicubicHardware(GainMap,(vec2(xy)+vec2(u_tileOrigin))/u_fullSize);
    gains.rgb=vec3(gains.r,(gains.g+gains.b)/2.0,gains.a);
    float gainsVal=dot(gains.rgb,vec3(1.0/3.0));

    vec3 neutralPoint=vec3(NEUTRALPOINT);
    float localGain=gainsVal*tonemapGain;
    float exposure=displayGain*localGain;
    vec3 wb=inColor*neutralPoint*exposure;

    /* Pre-tone local contrast (motionv2 reference-safe microcontrast). */
    float y=max(luminance(wb),0.0);
    if(y>1.0e-7) {
        float detail=log(1.0e-4+y)-localLogLumaMean(xy,exposure,neutralPoint);
        detail=clamp(detail,-0.20,0.20);
        float shadowGate=smoothstep(0.025,0.12,y);
        float highlightGate=1.0-smoothstep(0.55,0.92,y);
        float gate=shadowGate*highlightGate;
        wb*=exp(0.42*gate*detail);
    }

    /* The lens/fusion local gain also scales the shoulder's white point, so
     * locally-flat-fielded highlights keep the same highlight rolloff instead
     * of stacking above the global headroom and clipping to flat white. */
    float whitePoint=max(sceneWhite*clamp(localGain,0.25,4.0),0.55);

    vec3 linearSrgb=intermediateToSRGB*sensorToIntermediate*wb;
    linearSrgb=mapExtendedLinearHeadroom(linearSrgb,whitePoint);
    linearSrgb*=outputExposureScale;
    linearSrgb=fitDisplayGamut(linearSrgb);

    Output=vec4(clamp(srgbEncode(linearSrgb),vec3(0.0),vec3(1.0)),1.0);
}
