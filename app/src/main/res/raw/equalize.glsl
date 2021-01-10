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
#define EPS (0.035)
#define EPSAMP (3.0)
#define BL (0.02,0.02,0.02)
#define BLAVR (0.02)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    sRGB=(sRGB-vec3(BL))/(1.0-BLAVR);
    vec3 ch = XYZtoxyY(sRGB);
    float HistEq = texture(Histogram, vec2(1.0/512.0 + ch.z*(1.0-1.0/256.0), 0.5f)).r;
    HistEq = clamp(HistEq,0.0,5.0);
    float factor = 1.0;
    factor*=1.0-abs(0.5-ch.z)*1.0;
    factor*=clamp((ch.z-EPS)*EPSAMP,0.0,1.0);
    if(ch.z > EPS) ch.z = mix(ch.z,ch.z*pow(HistEq/ch.z,HistFactor),factor);
    ch.z = pow(ch.z,Equalize);
    sRGB = xyYtoXYZ(ch);

    Output = clamp(sRGB,EPS,1.0);
}
