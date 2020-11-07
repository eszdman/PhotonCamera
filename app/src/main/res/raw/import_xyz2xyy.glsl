vec3 XYZtoxyY(vec3 XYZ) {
    vec3 result = vec3(0.345703f, 0.358539f, XYZ.y);
    float sum = XYZ.x + XYZ.y + XYZ.z;
    if (sum > 0.0001f) {
        result.xy = XYZ.xy / sum;
    }
    return result;
}
