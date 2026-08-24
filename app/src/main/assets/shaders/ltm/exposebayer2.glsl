
precision mediump sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform sampler2D InterpolatedCurve;
uniform sampler2D ShadowMap;
uniform sampler2D GainMap;
uniform float factor;
uniform vec3 neutral;
out vec4 result;
#define NEUTRALPOINT 1.0,1.0,1.0
#define DH (0.0)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define CURVE 0
#define INVERSE 0
#define STRLOW 1.0
#define STRHIGH 1.0
#define COMPRESSOR 0.0
#define UPPERLIM 2.5
// Overwritten via glProg.setDefine("FUSEWEIGHTED",true) on the fusion path:
// packs THREE exposures plus the blend factor — .r = base/curve exposure
// (anchored at luma 1.0, highlights), .g = max boost (anchored at 0.0,
// shadows), .b = mid boost (anchored at 0.5, midtones), .a = blendFactor t
// (neutral-exposure luma). t rides the pyramid as data and fusionbayer3
// derives the linear tent shares from it per level.
#define FUSEWEIGHTED 0
// Weight parameters (Mertens et al.): TARGET/WEXPOSIGMA = center and sigma of
// the well-exposedness Gaussian on the gamma-encoded scale (paper: 0.5, 0.2);
// LAPMIN = epsilon added to the per-exposure Laplacian contrast factor.
#define TARGET 0.5
#define WEXPOSIGMA 0.2
#define LAPMIN 0.01
#define PI (3.1415926535)
#import interpolation

float gammaEncode(float x) {
    return sqrt(x);
}
vec4 reinhard_extended(vec4 v, float max_white)
{
    vec4 numerator = v * (vec4(1.0f) + (v / vec4(max_white * max_white)));
    return numerator / (vec4(1.0f) + v);
}

float reinhard_extended(float v, float max_white)
{
    float numerator = v * (float(1.0f) + (v / float(max_white * max_white)));
    return numerator / (float(1.0f) + v);
}

float stddev(vec3 XYZ) {
    float avg = (XYZ.r + XYZ.g + XYZ.b) / 3.;
    vec3 diff = XYZ - avg;
    diff *= diff;
    return sqrt((diff.r + diff.g + diff.b) / 3. + 0.001);
}

vec3 brIn(vec4 inp, float factor2){
    float br2 = inp.r+inp.g+inp.b+inp.a;
    br2/=4.0;
    float gammaUse = 0.0;
    #if CURVE == 1
    float texinput = texture(InterpolatedCurve,vec2(br2,0.5)).r;
    factor2=mix(1.0,factor2,texinput);
    float shadowinput = texture(ShadowMap,vec2(br2,0.5)).r;
    //factor2=mix(1.0,factor2,1.0+shadowinput*COMPRESSOR);
    #endif
    inp *= factor2;
    //inp=clamp(reinhard_extended(inp*factor2,min(factor2,1.0)),0.0,1.0);

    return vec3(inp.r,(inp.g+inp.b)/2.0,inp.a);
}
vec3 brIn2(vec4 inp, float factor2){
    float br2 = inp.r+inp.g+inp.b+inp.a;
    br2/=4.0;
    #if CURVE == 1
    float texinput = texture(InterpolatedCurve,vec2(br2,0.5)).r;
    texinput = clamp(1.0-texinput,0.0,1.0);
    factor2=mix(1.0,factor2,texinput);
    #endif
    inp *= factor2;
    //inp=clamp(reinhard_extended(inp*factor2,min(factor2,1.0)),0.0,1.0);
    return vec3(inp.r,(inp.g+inp.b)/2.0,inp.a);
}

float convSin(float x){
    return 0.5 + 0.5*sin((2.0*x-1.0) * PI/2.0);
}

float contrastSin(float value, float contrast) {
    return mix(value,convSin(value),contrast);
}

float aces(float x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

float fetchClamped(ivec2 p, ivec2 inSize) {
    p = clamp(p, ivec2(0), inSize - ivec2(1));
    return texelFetch(InputBuffer, p, 0).r;
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    xyCenter*=2;
    ivec2 inSize = textureSize(InputBuffer, 0);
    vec4 gains = textureBicubicHardware(GainMap, vec2(xyCenter)/vec2(inSize));
    float gain = (gains.r + gains.g + gains.b + gains.a) / 4.0;
    // Gaussian smoothing of the INPUT (3x3, [1 2 1] outer product / 16),
    // applied per subpixel phase over neighboring quads: sampling the packed
    // 2x2 quads otherwise aliases, and every exposure and the blend factor
    // below derive from this smoothed data. The cross neighbors' quad lumas
    // are captured for the per-exposure Laplacian contrast factor.
    vec4 inp = vec4(0.0);
    float nU = 0.0, nD = 0.0, nW = 0.0, nE = 0.0;
    for (int dj = -1; dj <= 1; dj++) {
        for (int di = -1; di <= 1; di++) {
            ivec2 q = xyCenter + 2*ivec2(di, dj);
            float w = ((di == 0) ? 2.0 : 1.0) * ((dj == 0) ? 2.0 : 1.0);
            vec4 nq = vec4(fetchClamped(q, inSize),
                           fetchClamped(q + ivec2(1,0), inSize),
                           fetchClamped(q + ivec2(0,1), inSize),
                           fetchClamped(q + ivec2(1,1), inSize));
            inp += w * nq;
            float nl = clamp(luminocity(vec3(nq.r,(nq.g+nq.b)/2.0,nq.a)) * gain, 0.0, 1.0);
            if (di == 0 && dj == -1) nU = nl;
            else if (di == 0 && dj == 1) nD = nl;
            else if (di == -1 && dj == 0) nW = nl;
            else if (di == 1 && dj == 0) nE = nl;
        }
    }
    inp /= 16.0;
    inp *= gain;
    // Normalize the four Bayer planes before calculating exposure weights.
    // Without this, white-point differences are interpreted as brightness.
    inp /= max(neutral.rggb, vec4(1e-6));
    inp = clamp(inp, vec4(0.0), vec4(1.0));
    //inp = clamp(inp,vec4(0.0001),vec3(NEUTRALPOINT).rggb)/vec3(NEUTRALPOINT).rggb;

    #if FUSEWEIGHTED == 1
    // Three exposures anchored by the blend factor's tent shares in
    // fusionbayer3: 1.0 highlights (base/curve), 0.5 midtones, 0.0 shadows.
    vec3 v3 = brIn2(inp,STRLOW);
    float br = luminocity(v3);
    br = gammaEncode(br);
    result.r = clamp(br,0.0,1.0);
    v3 = brIn(inp,STRHIGH);
    br = luminocity(v3);
    br = gammaEncode(br);
    result.g = clamp((br),0.0,1.0);
    v3 = brIn(inp,mix(STRHIGH,1.0,0.5));
    br = luminocity(v3);
    br = gammaEncode(br);
    result.b = clamp((br),0.0,1.0);
    // blendFactor: per-exposure weight = well-exposedness (exp(-(L - TARGET)^2
    // / (2*sigma^2))) x contrast (4-tap Laplacian of the exposure mapping on
    // the neighbor quad lumas — clipped exposures lose contrast), per Mertens
    // et al. Combined as the normalized centroid over the exposure anchors
    // 0.0 (shadows, .g), 0.5 (midtones, .b), 1.0 (highlights, .r). t stays in
    // [0,1] for the tent shares in fusionbayer3; NOT divided by 4.
    vec3 factors = vec3(STRLOW, STRHIGH, mix(STRHIGH,1.0,0.5));
    float lC = luminocity(vec3(inp.r,(inp.g+inp.b)/2.0,inp.a));
    vec3 lap = vec3(LAPMIN);
    for (int e = 0; e < 3; e++) {
        float f = factors[e];
        float c = gammaEncode(clamp(lC*f,0.0,1.0));
        float u = gammaEncode(clamp(nU*f,0.0,1.0));
        float d = gammaEncode(clamp(nD*f,0.0,1.0));
        float wst = gammaEncode(clamp(nW*f,0.0,1.0));
        float est = gammaEncode(clamp(nE*f,0.0,1.0));
        lap[e] += abs(4.0*c - u - d - wst - est);
    }
    vec3 wexp = exp(-pow(result.rgb - TARGET, vec3(2.0)) / (2.0*WEXPOSIGMA*WEXPOSIGMA));
    vec3 w = wexp * lap;
    result.a = (w.x + 0.5*w.z) / (w.x + w.y + w.z);
    result.rgb /= 4.0;
    #else
    vec3 v3 = brIn2(inp,STRLOW);
    float br = luminocity(v3);
    //br = clamp(br-DH,0.0,1.0);
    //br = mix(gammaEncode(br),br,0.1);
    br = gammaEncode(br);
    result.r = clamp(br,0.0,1.0);
    v3 = brIn(inp,STRHIGH);
    //float highLim = mix(STRHIGH,1.0,0.25);
    float highLim = UPPERLIM;
    //v3 = vec3(inp.r,(inp.g+inp.b)/2.0,inp.a)*STRHIGH;
    br = luminocity(v3);
    br = gammaEncode(br);
    //br = mix(br,gammaEncode(br),clamp(br-1.0,0.0,0.6));
    result.g = clamp(br,0.0,highLim);
    v3 = brIn(inp,mix(STRHIGH,1.0,0.5));
    br = luminocity(v3);
    br = gammaEncode(br);
    //br = mix(br,gammaEncode(br),clamp(br-1.0,0.0,0.6));
    result.b = clamp(br,0.0,highLim);
    v3 = brIn(inp,mix(STRHIGH,1.0,0.25));
    br = luminocity(v3);
    br = gammaEncode(br);
    //br = mix(br,gammaEncode(br),clamp(br-1.0,0.0,0.6));
    result.a = clamp(br,0.0,highLim);
    result /= 4.0;
    #endif
}
