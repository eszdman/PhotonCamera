#version 300 es
precision highp sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform float factor;
uniform vec3 neutralPoint;
out float result;
uniform int yOffset;
#define DR (1.4)
#define DH (0.0)
/*float gammaInverse(float x) {
    return (x <= 0.0031308*12.92) ? x / 12.92 : pow((x)/1.055,DR);
}*/
float gammaInverse(float x) {
    return pow((x),DR);
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 shift = xy%2;
    xy/=2;
    int cnt = shift.x+shift.y*2;
    vec4 tmp = texelFetch(InputBuffer, xy, 0);
    float br = (tmp.r+tmp.g+tmp.b+tmp.a)/4.0;
    //00 0
    //10 1
    //01 2
    //11 3
    tmp/=br;
    br = gammaInverse(br);
    br+=DH;
    //result = clamp(result*neutralPoint,vec3(0.0),neutralPoint);
    tmp*=neutralPoint.rggb*factor*br;
    result = clamp(tmp[cnt],0.0,1.0);
}
