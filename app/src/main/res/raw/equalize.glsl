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
#import xyytoxyz
#import xyztoxyy
#define EPS (0.0008)
#define EPS2 (0.0004)
#define EPSAMP (3.0)
#define BL (0.02,0.02,0.02)
#define BLAVR (0.02)
#define TINT (1.35)
#define TINT2 (1.0)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    sRGB=(sRGB-vec3(BL))/(vec3(1.0)-vec3(BL));
    sRGB = clamp(sRGB,0.0,1.0);
    float br = luminocity(sRGB);
    sRGB=sRGB/br;
    //ISO tint correction
    sRGB = mix(vec3(sRGB.r*0.99*(TINT2),sRGB.g*(TINT),sRGB.b*1.025*(TINT2)),sRGB,clamp(br*5.0,0.0,1.0));
    float HistEq = texture(Histogram, vec2(1.0/512.0 + br*(1.0-1.0/256.0), 0.5f)).r;
    HistEq = clamp(HistEq,0.0,5.0);
    float factor = 1.0;
    factor*=1.0-abs(0.5-br)*1.0;
    factor*=clamp((br-EPS)*EPSAMP,0.0,1.0);
    if(br > EPS) br = mix(br,br*pow(HistEq/br,HistFactor),factor);
    br = pow(br,Equalize);
    sRGB*=br;
    Output = clamp(sRGB,EPS,1.0);
}
