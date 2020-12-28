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

out vec4 result;

#import gaussian
#import interpolation
float laplace(sampler2D tex, vec4 mid, ivec2 xyCenter) {
    vec4 left = texelFetch(tex, xyCenter - ivec2(1, 0), 0),
    right = texelFetch(tex, xyCenter + ivec2(1, 0), 0),
    top = texelFetch(tex, xyCenter - ivec2(0, 1), 0),
    bottom = texelFetch(tex, xyCenter + ivec2(0, 1), 0);

    return distance(4.f * mid, left + right + top + bottom);
}

float stddev(vec4 XYZ) {
    float avg = (XYZ.r + XYZ.g + XYZ.b + XYZ.a) / 4.;
    vec4 diff = XYZ - avg;
    diff *= diff;
    return sqrt((diff.r + diff.g + diff.b + diff.a) / 4.);
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    // If this is the lowest layer, start with zero.
    //if(useUpsampled == 2) mpy = 2.0;
    vec4 base = (useUpsampled)
    //? texelFetch(upsampled, xyCenter, 0).xyz
    ? textureBicubic(upsampled, vec2(gl_FragCoord.xy)/vec2(upscaleIn))
    : vec4(0.);
    // How are we going to blend these two?
    vec4 normal = texelFetch(normalExpoDiff, xyCenter, 0);
    vec4 high = texelFetch(highExpoDiff, xyCenter, 0);

    // To know that, look at multiple factors.
    vec4 midNormal = texelFetch(normalExpo, xyCenter, 0);
    vec4 midHigh = texelFetch(highExpo, xyCenter, 0);

    float normalWeight = 1000.;
    float highWeight = 1000.;

    // Factor 1: Well-exposedness.
    vec4 midNormalToAvg = sqrt(unscaledGaussian(midNormal - 0.35, 0.50));
    vec4 midHighToAvg = sqrt(unscaledGaussian(midHigh - 0.35, 0.50));

    normalWeight *= midNormalToAvg.r * midNormalToAvg.g * midNormalToAvg.b * midNormalToAvg.a;
    highWeight *= midHighToAvg.r * midHighToAvg.g * midHighToAvg.b * midHighToAvg.a;

    // Factor 2: Contrast.
    float laplaceNormal = laplace(normalExpo, midNormal, xyCenter);
    float laplaceHigh = laplace(highExpo, midHigh, xyCenter);

    normalWeight *= sqrt(laplaceNormal + 0.01);
    highWeight *= sqrt(laplaceHigh + 0.01);

    // Factor 3: Saturation.
    float normalStddev = stddev(midNormal);
    float highStddev = stddev(midHigh);

    normalWeight *= sqrt(normalStddev + 0.01);
    highWeight *= sqrt(highStddev + 0.01);

    float blend = highWeight / (normalWeight + highWeight); // [0, 1]
    result = base + mix(normal, high, blend);
}
