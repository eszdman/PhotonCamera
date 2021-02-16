#version 300 es
precision highp sampler2D;
precision highp float;
uniform float Equalize;
uniform float HistFactor;
uniform sampler2D Histogram;
uniform sampler2D Equalizing;
uniform sampler2D InputBuffer;
out vec3 Output;
//#import interpolation
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#import xyytoxyz
#import xyztoxyy
#define EPS (0.0008)
#define EPS2 (0.0004)
#define EPSAMP (3.0)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    sRGB = clamp(sRGB,0.0,1.0);
    float br = luminocity(sRGB);
    sRGB=sRGB/br;
    float HistEq = texture(Histogram, vec2(1.0/512.0 + br*(1.0-1.0/256.0), 0.5f)).r;
    //Limit eq
    HistEq = clamp(HistEq,0.0,5.0);

    //Equalization factor
    float factor = 1.0;
    factor*=1.0-clamp(br-0.6,0.0,0.4)/0.4;
    //factor*=1.0-clamp(br-0.4,0.0,0.4)/0.3;
    factor = clamp(factor,0.0,1.0);


    if(br > EPS) br = mix(br,br*pow(HistEq/br,HistFactor),factor);
    //br = mix(HistEq,br*pow(HistEq/br,HistFactor),br);
    //br=HistEq;
    //if(br > EPS)
    //br = mix(br,HistEq,factor);
    br = texture(Equalizing, vec2(1.0/512.0 + br*(1.0-1.0/256.0), 0.5f)).r;
    //br = pow(br,Equalize);

    //Undersaturate shadows
    float undersat = max(0.12-br,0.0)*1.5/0.12;
    sRGB += (sRGB.r+sRGB.g+sRGB.b)*undersat/3.0;
    sRGB /= luminocity(sRGB);

    sRGB*=br;
    Output = clamp(sRGB,0.0,1.0);
}
