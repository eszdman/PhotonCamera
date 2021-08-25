//GLSL port by William Malo ( https://github.com/williammalo )
float hsluv_yToL(float Y){
    return Y <= 0.0088564516790356308 ? Y * 903.2962962962963 : 116.0 * pow(Y, 1.0 / 3.0) - 16.0;
}
vec3 xyztoluv(vec3 tuple){
    float X = tuple.x;
    float Y = tuple.y;
    float Z = tuple.z;

    float L = hsluv_yToL(Y);

    float div = 1./dot(tuple,vec3(1,15,3));

    return vec3(
    1.,
    (52. * (X*div) - 2.57179),
    (117.* (Y*div) - 6.08816)
    ) * L;
}