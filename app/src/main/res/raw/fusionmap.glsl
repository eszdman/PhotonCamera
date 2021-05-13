#version 300 es
precision highp sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform sampler2D BrBuffer;
uniform float factor;
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
    //br2*=br2;
    //br2=sqrt(br2);
    //br*=br;
    //br2 = gammaInverse(br2);
    //br2+=DH;
    //br = gammaInverse(br);
    //br+=DH+0.003;
    result=(((br)/(br2+0.00001)));
    result = clamp(result/50.0,0.0,1.0);
}
