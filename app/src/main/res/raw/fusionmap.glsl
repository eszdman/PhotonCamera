#version 300 es
precision highp sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform sampler2D BrBuffer;
uniform float factor;
uniform vec3 neutralPoint;
out float result;
uniform int yOffset;
#define DH (0.0)
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
float gammaInverse(float x) {
    return x*x;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    float br = texelFetch(BrBuffer, xy, 0).r;
    float br2 = texelFetch(InputBuffer, xy, 0).r;
    br2 = gammaInverse(br2);
    br2+=DH;
    br = gammaInverse(br);
    br+=DH;
    result=(factor*(br2/br))/10.0;
    result = clamp(result,0.0,1.0);
}
