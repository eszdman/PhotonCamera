#version 300 es

precision highp float;
precision mediump sampler2D;

uniform sampler2D upsampled;
uniform bool useUpsampled;

// Weighting is done using these.
uniform sampler2D normalExpo;
uniform sampler2D highExpo;

// Blending is done using these.
uniform sampler2D normalExpoDiff;
uniform sampler2D highExpoDiff;

out vec3 result;

#import gaussian

float laplace(sampler2D tex, float mid, ivec2 xyCenter) {
    float left = texelFetch(tex, xyCenter - ivec2(1, 0), 0).y,
    right = texelFetch(tex, xyCenter + ivec2(1, 0), 0).y,
    top = texelFetch(tex, xyCenter - ivec2(0, 1), 0).y,
    bottom = texelFetch(tex, xyCenter + ivec2(0, 1), 0).y;

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
    ? texelFetch(upsampled, xyCenter, 0).xyz
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
    float laplaceNormal = laplace(normalExpo, midNormal.y, xyCenter);
    float laplaceHigh = laplace(highExpo, midHigh.y, xyCenter);

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
