#version 300 es

precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D GainMap;
uniform float start;
uniform float avrbr;
uniform float intens;
uniform ivec2 size;
uniform int yOffset;
out vec4 Output;
#import interpolation
void main() {
    vec2 xy = vec2(gl_FragCoord.xy);
    xy+=vec2(0,yOffset);
    float mapbr = length(textureLinear(GainMap,vec2(xy)/vec2(size)))/avrbr;
    vec2 inxy = vec2(xy)/vec2(size);
    inxy-=0.5;
    //float len = length(inxy)/0.707106781186;
    //len=1.0+clamp((len-start),0.0,1.0)*intens;

    //Output = texture(InputBuffer,0.5 + inxy*len);
    Output = textureBicubic(InputBuffer,0.5 + inxy/(0.9 + mapbr*0.09));
}
