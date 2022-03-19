
precision mediump sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform float factor;
uniform vec3 neutralPoint;
out vec3 result;
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
    result = texelFetch(InputBuffer, xy, 0).rgb;
    float br = (result.r+result.g+result.b)/3.0;
    result/=br;
    br = gammaInverse(br);
    //result.r = gammaInverse(result.r);
    //result.g = gammaInverse(result.g);
    //result.b = gammaInverse(result.b);

    br+=DH;
    //result+=DH;
    //result = clamp(result*neutralPoint,vec3(0.0),neutralPoint);
    result*=neutralPoint*factor*br;
    result = clamp(result,0.0,1.0);
}
