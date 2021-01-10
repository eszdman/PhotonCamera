#version 300 es
precision highp sampler2D;
precision highp float;
uniform float Equalize;
uniform float HistFactor;
uniform sampler2D Histogram;
uniform sampler2D InputBuffer;
out vec3 Output;
//#import interpolation
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define EPS (0.0005)
#define BL (0.02,0.02,0.02)
#define BLAVR (0.02)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    float luma = (sRGB.r+sRGB.g+sRGB.b)/3.0;
    if(luma > EPS){
        sRGB/=luma;
        float HistEq = texture(Histogram, vec2(1.0/512.0 + luma*(1.0-1.0/256.0), 0.5f)).r;
        HistEq = clamp(HistEq,0.0,5.0);
        float factor = 1.0;
        factor*=1.0-abs(0.5-luma)*1.0;
        luma = mix(luma,luma*pow(HistEq/luma,HistFactor),factor);
        luma = pow(luma,Equalize);
        sRGB*=luma;
        sRGB=(sRGB-vec3(BL))/(1.0-BLAVR);
    }
    Output = clamp(sRGB,EPS,1.0);
}
