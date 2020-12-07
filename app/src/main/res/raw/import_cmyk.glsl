vec3 cmyk2rgb (vec4 cmyk) {
    float invK = 1.0 - cmyk.w;
    vec3 rgbo = vec3(1.0)-min(vec3(1.0),cmyk.rgb*invK + cmyk.w);
    return clamp(rgbo, 0.0, 1.0);
}

vec4 rgb2cmyk (vec3 rgb) {
    float k = min(1.0 - rgb.r, min(1.0 - rgb.g, 1.0 - rgb.b));
    float invK = 1.0 - k;
    vec4 cmyk;
    cmyk.a = k;
    if (invK != 0.0) {
        cmyk.rgb = (vec3(1.0) - rgb - k)/(vec3(invK));
    }
    return clamp(cmyk, 0.0, 1.0);
}