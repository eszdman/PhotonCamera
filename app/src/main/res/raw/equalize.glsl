#version 300 es
precision highp sampler2D;
precision highp float;
uniform float Equalize;
uniform float HistFactor;
uniform sampler2D Histogram;
uniform vec2 HistOffset;
uniform sampler2D InputBuffer;
out vec3 Output;
//#import interpolation
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define EPS (0.0001)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    float luma = luminocity(sRGB);
    if(luma > EPS){
        sRGB/=luma;
        float HistEq = texture(Histogram, vec2(luma, 0.5f)).r;
        float factor = 1.0;
        factor*=1.0-abs(0.5-luma)*1.5;
        luma = mix(luma,luma*pow(HistEq/luma,HistFactor),factor);
        luma = pow(luma,Equalize);
        sRGB*=luma;
    }
    Output = clamp(sRGB,EPS,1.0);
}
