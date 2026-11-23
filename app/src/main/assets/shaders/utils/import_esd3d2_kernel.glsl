//
// Created by eszdman on 23.11.2026.
//

// Four mirrored taps form one symmetric-nearest-neighbor group.
struct SteeredKernel {
    ivec2 center;
    vec3 raw, reference;
    vec2 tangent;
    float coherence, lumaVariance, chromaVariance;
};

struct FilterSum {
    vec3 luma, chroma;
    float lumaWeight, chromaWeight;
};

vec4 distances(mat4x3 colors, vec3 reference) {
    return vec4(length(abs(colors[0] - reference)), length(abs(colors[1] - reference)),
                length(abs(colors[2] - reference)), length(abs(colors[3] - reference)));
}

void filterGroup(SteeredKernel k, int i, int j, inout FilterSum sum) {
    mat4x3 colors = mat4x3(texelFetch(InputBuffer, k.center + ivec2( i,  j), 0).rgb,
                           texelFetch(InputBuffer, k.center + ivec2(-i, -j), 0).rgb,
                           texelFetch(InputBuffer, k.center + ivec2( i, -j), 0).rgb,
                           texelFetch(InputBuffer, k.center + ivec2(-i,  j), 0).rgb);
    vec4 d = distances(colors, k.reference);
    vec4 w = 1.0 - d*d / (d*d + k.lumaVariance);
#if ESD_CONSENSUS
    vec4 d0 = distances(colors, k.raw);
    w *= 1.0 - d0*d0 / (d0*d0 + k.lumaVariance);
#endif
    float floorWeight = min(min(min(w.x, w.y), w.z), w.w);
    vec4 relative = w - floorWeight;
    relative /= length(relative) + 0.000001;
    w *= relative;

    float projected = float(i)*k.tangent.x + float(j)*k.tangent.y;
    float spatial = mix(normpdf(float(i), KERNELSIZE) * normpdf(float(j), KERNELSIZE),
                        normpdf(projected, KERNELSIZE), k.coherence);
    sum.luma += spatial * colors * w;
    sum.lumaWeight += dot(vec4(spatial), w);
    if (k.chromaVariance != k.lumaVariance) {
        vec4 wc = 1.0 - d*d / (d*d + k.chromaVariance);
#if ESD_CONSENSUS
        wc *= 1.0 - d0*d0 / (d0*d0 + k.chromaVariance);
#endif
        vec4 relativeChroma = wc - floorWeight;
        relativeChroma /= length(relativeChroma) + 0.000001;
        wc *= relativeChroma;
        sum.chroma += spatial * colors * wc;
        sum.chromaWeight += dot(vec4(spatial), wc);
    }
}

void filterWindow(SteeredKernel k, inout FilterSum sum) {
#if ESD_PEEL_AXES
    // On each axis the four offsets address only two texels. Keep the center
    // in the first loop and accumulate every group in the original order.
    for (int j = 0; j <= kernelRadius; ++j)
        filterGroup(k, 0, j, sum);
    for (int i = 1; i <= kernelRadius; ++i) {
        filterGroup(k, i, 0, sum);
        for (int j = 1; j <= kernelRadius; ++j)
            filterGroup(k, i, j, sum);
    }
#else
    for (int i = 0; i <= kernelRadius; ++i)
        for (int j = 0; j <= kernelRadius; ++j)
            filterGroup(k, i, j, sum);
#endif
}
