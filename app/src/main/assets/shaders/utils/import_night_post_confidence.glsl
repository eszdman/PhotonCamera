#if NIGHT_CONFIDENCE
uniform highp sampler2D nightUncertaintyMap;
uniform ivec2 nightRawSize;
uniform float nightNominalReduction;
float nightVarianceRatio(vec2 uv) {
    // Manual bilinear sampling avoids requiring float32 linear-filter extensions.
    vec2 position = uv * vec2(nightRawSize) / 16.0 - .5;
    ivec2 corner = ivec2(floor(position));
    vec2 fraction = fract(position);
    ivec2 limit = textureSize(nightUncertaintyMap, 0) - 1;
    float a = texelFetch(nightUncertaintyMap, clamp(corner, ivec2(0), limit), 0).g;
    float b = texelFetch(nightUncertaintyMap, clamp(corner + ivec2(1,0), ivec2(0), limit), 0).g;
    float c = texelFetch(nightUncertaintyMap, clamp(corner + ivec2(0,1), ivec2(0), limit), 0).g;
    float d = texelFetch(nightUncertaintyMap, clamp(corner + ivec2(1,1), ivec2(0), limit), 0).g;
    return clamp(mix(mix(a,b,fraction.x), mix(c,d,fraction.x), fraction.y), .001, 1.0);
}
float nightNoiseMultiplier(vec2 uv) {
    // Undo the global stack reduction, then apply measured local reduction.
    return clamp(nightVarianceRatio(uv) * nightNominalReduction, .05, max(1.0, nightNominalReduction));
}
float nightSharpenMultiplier(vec2 uv) {
    return .25 + .75 * sqrt(max(0.0, 1.0 - nightVarianceRatio(uv)));
}
#else
float nightNoiseMultiplier(vec2 uv) { return 1.0; }
float nightSharpenMultiplier(vec2 uv) { return 1.0; }
#endif
