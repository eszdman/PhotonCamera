#version 300 es

precision highp float;
precision highp sampler2D;

uniform sampler2D upsampled;
uniform bool useUpsampled;

// Weighting is done using these.
uniform sampler2D normalExpo;
uniform sampler2D highExpo;

// Blending is done using these.
uniform sampler2D normalExpoDiff;
uniform sampler2D highExpoDiff;

uniform ivec2 upscaleIn;

out vec3 result;

#import gaussian
#import interpolation
float laplace(sampler2D tex, vec3 mid, ivec2 xyCenter) {
    vec3 left = texelFetch(tex, xyCenter - ivec2(1, 0), 0).rgb,
    right = texelFetch(tex, xyCenter + ivec2(1, 0), 0).rgb,
    top = texelFetch(tex, xyCenter - ivec2(0, 1), 0).rgb,
    bottom = texelFetch(tex, xyCenter + ivec2(0, 1), 0).rgb;

    return distance(4.f * mid, left + right + top + bottom);
}

float stddev(vec3 XYZ) {
    float avg = (XYZ.x + XYZ.y + XYZ.z) / 3.f;
    vec3 diff = XYZ - avg;
    diff *= diff;
    return sqrt((diff.x + diff.y + diff.z) / 3.f);
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    // If this is the lowest layer, start with zero.
    vec3 base = useUpsampled
    //? texelFetch(upsampled, xyCenter, 0).xyz
    ? textureBicubic(upsampled, vec2(gl_FragCoord.xy)/vec2(upscaleIn)).xyz
    : vec3(0.f);

    // How are we going to blend these two?
    vec3 normal = texelFetch(normalExpoDiff, xyCenter, 0).xyz;
    vec3 high = texelFetch(highExpoDiff, xyCenter, 0).xyz;

    // To know that, look at multiple factors.
    vec3 midNormal = texelFetch(normalExpo, xyCenter, 0).xyz;
    vec3 midHigh = texelFetch(highExpo, xyCenter, 0).xyz;

    float normalWeight = 1000.f;
    float highWeight = 1000.f;

    // Factor 1: Well-exposedness.
    vec3 midNormalToAvg = sqrt(unscaledGaussian(midNormal - 0.3f, 0.5f));
    vec3 midHighToAvg = sqrt(unscaledGaussian(midHigh - 0.3f, 0.5f));

    normalWeight *= midNormalToAvg.x * midNormalToAvg.y * midNormalToAvg.z;
    highWeight *= midHighToAvg.x * midHighToAvg.y * midHighToAvg.z;

    // Factor 2: Contrast.
    float laplaceNormal = laplace(normalExpo, midNormal, xyCenter);
    float laplaceHigh = laplace(highExpo, midHigh, xyCenter);

    normalWeight *= sqrt(laplaceNormal + 0.1f);
    highWeight *= sqrt(laplaceHigh + 0.1f);

    // Factor 3: Saturation.
    float normalStddev = stddev(midNormal);
    float highStddev = stddev(midHigh);

    normalWeight *= sqrt(normalStddev + 0.1f);
    highWeight *= sqrt(highStddev + 0.1f);

    float blend = highWeight / (normalWeight + highWeight); // [0, 1]
    result = base + mix(normal, high, blend);
}
