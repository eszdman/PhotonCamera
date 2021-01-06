#version 300 es
precision highp sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform sampler2D BayerBuffer;
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
    ivec2 shift = xy%2;
    float bayer = texelFetch(BayerBuffer, xy, 0).r;
    xy/=2;
    int cnt = shift.x+shift.y*2;
    float br = texelFetch(BrBuffer, xy, 0).r;
    float br2 = texelFetch(InputBuffer, xy, 0).r;
    //00 0
    //10 1
    //01 2
    //11 3

    //bayer/=br;
    br2 = gammaInverse(br2);
    br2+=DH;
    br = gammaInverse(br);
    br+=DH;
    //result = clamp(result*neutralPoint,vec3(0.0),neutralPoint);
    //tmp*=neutralPoint.rggb*factor*br;
    bayer*=factor*(br2/br);
    result = clamp(bayer,0.0,1.0);
}
