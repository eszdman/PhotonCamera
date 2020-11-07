float unscaledGaussian(float d, float s) {
    float interm = d / s;
    return exp(-0.5f * interm * interm);
}

vec3 unscaledGaussian(vec3 d, float s) {
    vec3 interm = d / s;
    return exp(-0.5f * interm * interm);
}

vec3 unscaledGaussian(vec3 d, vec3 s) {
    vec3 interm = d / s;
    return exp(-0.5f * interm * interm);
}
