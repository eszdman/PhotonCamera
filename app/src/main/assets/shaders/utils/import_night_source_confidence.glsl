// Source-domain tests, before exposure scaling or alignment interpolation.
// alterTexture contains same-color channels in packed Bayer quads.
uniform float nightSensorS;
uniform float nightSensorO;
float nightMaximum(vec4 value) { return max(max(value.r, value.g), max(value.b, value.a)); }
float nightMinimum(vec4 value) { return min(min(value.r, value.g), min(value.b, value.a)); }
vec3 nightSourceConfidence(ivec2 position) {
    ivec2 size = imageSize(alterTexture);
    if (any(lessThan(position, ivec2(0))) || any(greaterThanEqual(position, size))) return vec3(0.0);
    vec4 center = imageLoad(alterTexture, position);
    vec4 leftValue = imageLoad(alterTexture, clamp(position + ivec2(-1, 0), ivec2(0), size - 1));
    vec4 rightValue = imageLoad(alterTexture, clamp(position + ivec2(1, 0), ivec2(0), size - 1));
    vec4 topValue = imageLoad(alterTexture, clamp(position + ivec2(0, -1), ivec2(0), size - 1));
    vec4 bottomValue = imageLoad(alterTexture, clamp(position + ivec2(0, 1), ivec2(0), size - 1));
    vec4 lowValue = min(min(leftValue, rightValue), min(topValue, bottomValue));
    vec4 highValue = max(max(leftValue, rightValue), max(topValue, bottomValue));
    vec4 meanValue = (leftValue + rightValue + topValue + bottomValue) * .25;
    vec4 sigma = sqrt(max(meanValue * nightSensorS + nightSensorO, vec4(1e-10)));
    vec4 excursion = max(max(center - highValue, lowValue - center), vec4(0.0));
    // Very conservative isolated hot/dead-site mask; edges retain wide neighbor ranges.
    vec4 anomaly = smoothstep(8.0 * sigma + .002, 12.0 * sigma + .004, excursion);
    float defectConfidence = 1.0 - nightMaximum(anomaly);
    float headroom = 1.0 - smoothstep(.90, .995, nightMaximum(center));
    return vec3(headroom, defectConfidence, 1.0);
}
