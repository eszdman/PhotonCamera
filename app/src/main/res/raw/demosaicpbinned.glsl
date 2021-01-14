#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform vec4 blackLevel;
uniform sampler2D GainMap;
uniform int yOffset;
out vec3 Output;
#import interpolation
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec4 gains = textureBicubicHardware(GainMap, vec2(xy)/vec2(textureSize(InputBuffer, 0)));
    xy*=2;
    Output.g = float(texelFetch(InputBuffer, (xy+ivec2(1,0)), 0).x)+float(texelFetch(InputBuffer, (xy+ivec2(0,1)), 0).x);
    Output.r = float(texelFetch(InputBuffer, (xy+ivec2(0,0)), 0).x);
    Output.b = float(texelFetch(InputBuffer, (xy+ivec2(1,1)), 0).x);
    Output.g/=2.0;
    Output.r = gains.r*(Output.r-blackLevel.r);
    Output.g = ((gains.g+gains.b)/2.)*(Output.g-(blackLevel.g+blackLevel.b)/2.);
    Output.b = gains.a*(Output.b-blackLevel.a);
    Output/=(1.0-blackLevel.g);
}