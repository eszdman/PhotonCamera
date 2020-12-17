#version 300 es

precision highp float;
precision mediump sampler2D;

uniform sampler2D upsampled;
uniform bool useUpsampled;

// Weighting is done using these.
uniform sampler2D normalExpo;

// Blending is done using these.
uniform sampler2D normalExpoDiff;

uniform ivec2 upscaleIn;

uniform float factorMid;
uniform float factorHigh;

uniform vec3 neutralPoint;

out vec3 result;

#import gaussian
#import interpolation
vec3 fullEncode(sampler2D tex,ivec2 xyCenter, float factor);
float laplace(sampler2D tex, vec3 mid, ivec2 xyCenter,float factor) {
    vec3 left = fullEncode(tex, xyCenter - ivec2(1, 0),factor),
    right = fullEncode(tex, xyCenter + ivec2(1, 0),factor),
    top = fullEncode(tex, xyCenter - ivec2(0, 1),factor),
    bottom = fullEncode(tex, xyCenter + ivec2(0, 1),factor);

    return distance(4.f * mid, left + right + top + bottom);
}
float stddev(vec3 XYZ) {
    float avg = (XYZ.x + XYZ.y + XYZ.z) / 3.f;
    vec3 diff = XYZ - avg;
    diff *= diff;
    return sqrt((diff.x + diff.y + diff.z) / 3.f);
}
float gammaEncode(float x) {
    if(x>1.0) return x;
    return (x <= 0.0031308) ? x * 12.92 : 1.055 * pow(float(x), (1.f/2.5)) - 0.055;
}
vec3 fullEncode(sampler2D tex,ivec2 xyCenter, float factor){
    vec3 outp = texelFetch(tex,xyCenter,0).rgb;
    outp = clamp(outp*factor,vec3(0.0),neutralPoint)+0.0001;
    outp/=neutralPoint;
    float br = (outp.r+outp.g+outp.b)/3.0;
    outp/=br;
    br = gammaEncode(br);
    outp*=br;
    return outp;
}
vec3 fullEncode(vec3 inp, float factor){
    vec3 outp = clamp(inp*factor,vec3(0.0),neutralPoint)+0.0001;
    outp/=neutralPoint;
    float br = (outp.r+outp.g+outp.b)/3.0;
    outp/=br;
    br = gammaEncode(br);
    outp*=br;
    return outp;
}
void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    // If this is the lowest layer, start with zero.
    vec3 base = useUpsampled
    //? texelFetch(upsampled, xyCenter, 0).xyz
    ? textureBicubic(upsampled, vec2(gl_FragCoord.xy)/vec2(upscaleIn)).xyz
    : vec3(0.f);

    vec3 InitUnexp = texelFetch(normalExpo, xyCenter, 0).xyz;
    vec3 normalExp = fullEncode(InitUnexp,factorMid);
    vec3 highExp = fullEncode(InitUnexp,factorHigh);
    vec3 high;
    vec3 normal;
    if(useUpsampled){
        normal = fullEncode(texelFetch(normalExpoDiff, xyCenter, 0).xyz,factorMid);
        if((highExp.r+highExp.g+highExp.b)/3.0 >= 10.97){
            high = vec3(0.0);
        } else {
            high = normal*factorHigh;
        }
    } else {
        normal = normalExp;
        high = highExp*factorHigh;
    }

    float normalWeight = 1000.f;
    float highWeight = 1000.f;

    // Factor 1: Well-exposedness.
    vec3 midNormalToAvg = sqrt(unscaledGaussian(normalExp - 0.3f, 0.5f));
    vec3 midHighToAvg = sqrt(unscaledGaussian(highExp - 0.3f, 0.5f));

    normalWeight *= midNormalToAvg.x * midNormalToAvg.y * midNormalToAvg.z;
    highWeight *= midHighToAvg.x * midHighToAvg.y * midHighToAvg.z;

    // Factor 2: Contrast.
    float laplaceNormal = laplace(normalExpo, normalExp, xyCenter,factorMid);
    float laplaceHigh = laplace(normalExpo, highExp, xyCenter,factorHigh);

    //normalWeight *= sqrt(laplaceNormal + 0.1f);
    //highWeight *= sqrt(laplaceHigh + 0.1f);

    // Factor 3: Saturation.
    float normalStddev = stddev(normalExp);
    float highStddev = stddev(highExp);

    normalWeight *= sqrt(normalStddev + 0.1f);
    highWeight *= sqrt(highStddev + 0.1f);

    float blend = highWeight / (normalWeight + highWeight); // [0, 1]
    result = base + mix(normal, high, blend);
}
