

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
    vec3 xyz3 = vec3(XYZ.r,(XYZ.g+XYZ.b)/2.0,XYZ.a);
    float avg = (xyz3.r + xyz3.g + xyz3.b) / 3.;
    vec3 diff = xyz3 - avg;
    diff *= diff;
    return sqrt((diff.r + diff.g + diff.b) / 3.);
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    // If this is the lowest layer, start with zero.
    //if(useUpsampled == 2) mpy = 2.0;
    vec4 base = (useUpsampled)
    //? texelFetch(upsampled, xyCenter, 0).xyz
    ? textureBicubicHardware(upsampled, vec2(gl_FragCoord.xy)/vec2(upscaleIn))
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

    vec3 midNormalToAvg = sqrt(unscaledGaussian(vec3(midNormal.r,(midNormal.g+midNormal.b)/2.0,midNormal.a) - 0.35, 0.50));
    vec3 midHighToAvg = sqrt(unscaledGaussian(vec3(midHigh.r,(midHigh.g+midHigh.b)/2.0,midHigh.a) - 0.35, 0.50));

    normalWeight *= midNormalToAvg.r * midNormalToAvg.g * midNormalToAvg.b;
    highWeight *= midHighToAvg.r * midHighToAvg.g * midHighToAvg.b;

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
