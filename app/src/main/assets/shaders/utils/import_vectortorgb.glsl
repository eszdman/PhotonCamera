vec3 hsvtoxyzP(vec3 c)
{
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}
vec3 vectortorgb(vec2 V){
    float r = sqrt(V.x * V.x + V.y * V.y);
    float angle = atan(V.y, V.x);
    angle = angle / 2.0 / PI;
    vec3 hsv = hsvtoxyzP(vec3(angle, 1.0, clamp(r, 0.0, 1.0)));
    return hsv;
}
