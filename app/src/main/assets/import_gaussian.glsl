float fastExp(float x){
    float s = 1.0+x;
    return s;
}
float fastExp(vec2 x){
    float s = 1.0+(x.x+x.y)/2.0;
    return s;
}
float fastExp(vec3 x){
    float s = 1.0+(x.x+x.y+x.z)/3.0;
    return s;
}

vec2 fastExp2(vec2 x){
    vec2 s = vec2(1.0)+x;
    return s;
}
vec3 fastExp3(vec3 x){
    vec3 s = vec3(1.0)+x;
    return s;
}

float unscaledGaussian(float d, float s) {
    float interm = d / s;
    return exp(-0.5f * interm * interm);
}
vec2 unscaledGaussian(vec2 d, float s) {
    vec2 interm = d / s;
    return exp(-0.5f * interm * interm);
}
vec3 unscaledGaussian(vec3 d, float s) {
    vec3 interm = d / s;
    return exp(-0.5f * interm * interm);
}
vec4 unscaledGaussian(vec4 d, float s) {
    vec4 interm = d / s;
    return exp(-0.5f * interm * interm);
}

vec3 unscaledGaussian(vec3 d, vec3 s) {
    vec3 interm = d / s;
    return exp(-0.5f * interm * interm);
}

float pdf(float d) {
    return 1.0/fastExp(d * d);
}
float pdf(vec2 d) {
    return 1.0/fastExp(d * d);
}
float pdf(vec3 d) {
    return 1.0/fastExp(d * d);
}
vec2 pdf2(vec2 d) {
    return vec2(1.0)/fastExp2(d * d);
}
vec3 pdf3(vec3 d) {
    return vec3(1.0)/fastExp3(d * d);
}

