#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer22;
uniform sampler2D MainBuffer22;
uniform sampler2D InputBuffer88;
uniform sampler2D MainBuffer88;
uniform sampler2D InputBuffer32;
uniform sampler2D MainBuffer32;
uniform sampler2D InputBuffer128;
uniform sampler2D MainBuffer128;
uniform sampler2D AlignVectors;
uniform int yOffset;
#define MIN_NOISE 0.1f
#define MAX_NOISE 0.7f
#define TILESIZE (32)
out float Output;
#import interpolation
#import coords
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 outsize = vec2(textureSize(MainBuffer22, 0))/2.0;
    ivec2 align = ivec2(texture(AlignVectors, vec2(gl_FragCoord.xy)/outsize).rg*float(TILESIZE)*256.0);
    vec2 aligned = vec2(mirrorCoords(xy*2 + align,ivec4(0,0,ivec2(outsize*2.0))))/(outsize*2.0);
    Output =
    sqrt(abs(textureBicubicHardware(InputBuffer128,aligned).a-textureBicubicHardware(MainBuffer128,vec2(gl_FragCoord.xy)*2.0).a))*
    sqrt(abs(textureBicubicHardware(InputBuffer32,aligned).a-textureBicubicHardware(MainBuffer32,vec2(gl_FragCoord.xy)*2.0).a))*
    sqrt(abs(textureBicubicHardware(InputBuffer88,aligned).a-textureBicubicHardware(MainBuffer88,vec2(gl_FragCoord.xy)*2.0).a))*
    sqrt(abs(textureBicubicHardware(InputBuffer22,aligned).a-textureBicubicHardware(MainBuffer22,vec2(gl_FragCoord.xy)*2.0).a));
    Output = textureBicubicHardware(MainBuffer22,aligned).a*0.1;
}
