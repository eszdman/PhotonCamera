#version 300 es

precision highp float;
precision highp sampler2D;

#define TARGET_Z 0.6f
#define GAUSS_Z 0.5f

uniform sampler2D upsampled;
uniform bool useUpsampled;

// Weighting is done using these.
uniform sampler2D normalExpo;
uniform sampler2D highExpo;
uniform int level;

// Blending is done using these.
uniform sampler2D normalExpoDiff;
uniform sampler2D highExpoDiff;

uniform ivec2 upscaleIn;

out vec4 result;
#import gaussian
#import sigmoid
#import interpolation

float compress(float z, int lvl) {
    return z / (0.05f * sqrt(sqrt(float(11 - lvl) * abs(z))) + 1.f);
}
vec4 compress(vec4 z, int lvl) {
    return z / (0.05f * sqrt(sqrt(float(11 - lvl) * abs(z))) + 1.f);
}
float applyGamma(float x) {
    if (abs(x) < 0.001) {
        return x;
    }
    return sign(x) * pow(abs(x), 1.f / 1.6f);
}
vec4 applyGamma(vec4 x) {
    x.r = applyGamma(x.r);
    x.g = applyGamma(x.g);
    x.b = applyGamma(x.b);
    x.a = applyGamma(x.a);
    return x;
}
void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    // If this is the lowest layer, start with zero.
    vec4 base = (useUpsampled)
    //? texelFetch(upsampled, xyCenter, 0).xyz
    ? textureBicubic(upsampled, vec2(gl_FragCoord.xy)/vec2(upscaleIn))
    : vec4(0.);

    // How are we going to blend these two?
    vec4 blendUnderVal = texelFetch(normalExpoDiff, xyCenter, 0);
    vec4 blendOverVal = texelFetch(highExpoDiff, xyCenter, 0);

    // Look at result to compute weights.
    vec4 gaussUnderVal = texelFetch(normalExpo, xyCenter, 0);
    vec4 gaussOverVal = texelFetch(highExpo, xyCenter, 0);

    float underWeight = 1000.;
    float highWeight = 1000.;

    vec4 gaussUnderValDev = sqrt(
    unscaledGaussian(applyGamma(gaussUnderVal) - applyGamma(TARGET_Z), GAUSS_Z) + 0.01
    );
    underWeight*=gaussUnderValDev.r*gaussUnderValDev.g*gaussUnderValDev.b*gaussUnderValDev.a;
    vec4 gaussOverValDev = sqrt(
    unscaledGaussian(applyGamma(gaussOverVal) - applyGamma(TARGET_Z), GAUSS_Z) + 0.01
    );
    highWeight*=gaussOverValDev.r*gaussOverValDev.g*gaussOverValDev.b*gaussOverValDev.a;
    float blend = highWeight / (underWeight + highWeight); // [0, 1]
    vec4 blendVal = mix(blendUnderVal, blendOverVal, blend);
    vec4 res = base + compress(blendVal, level);
    if (level == 0) {
        res = max(res / compress(1.f, 10), 0.f);
        res.r = sigmoid(res.r, 0.25f);
        res.g = sigmoid(res.g, 0.25f);
        res.b = sigmoid(res.b, 0.25f);
        res.a = sigmoid(res.a, 0.25f);
    }
    result = res;
}
