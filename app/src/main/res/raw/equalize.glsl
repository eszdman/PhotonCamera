#version 300 es
precision highp sampler2D;
precision highp float;
uniform float Equalize;
uniform sampler2D InputBuffer;
uniform sampler2D LookupTable;
uniform sampler2D PostCurve;
out vec3 Output;
#import interpolation
float Encode(float x) {
    return (x <= 0.0031308) ? x * 12.92 : 1.055 * pow(float(x), (1.f/1.8)) - 0.055;
}
vec3 lookup(in vec3 textureColor) {
    textureColor = clamp(textureColor, 0.0, 1.0);

    highp float blueColor = textureColor.b * 63.0;

    highp vec2 quad1;
    quad1.y = floor(floor(blueColor) / 8.0);
    quad1.x = floor(blueColor) - (quad1.y * 8.0);

    highp vec2 quad2;
    quad2.y = floor(ceil(blueColor) / 8.0);
    quad2.x = ceil(blueColor) - (quad2.y * 8.0);

    highp vec2 texPos1;
    texPos1.x = (quad1.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.r);
    texPos1.y = (quad1.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.g);

    highp vec2 texPos2;
    texPos2.x = (quad2.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.r);
    texPos2.y = (quad2.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.g);

    highp vec3 newColor1 = textureBicubicHardware(LookupTable, texPos1).rgb;
    highp vec3 newColor2 = textureBicubicHardware(LookupTable, texPos2).rgb;

    highp vec3 newColor = (mix(newColor1, newColor2, fract(blueColor)));
    return newColor;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 sRGB = texelFetch(InputBuffer, xy, 0).rgb;
    vec3 dLook = lookup(sRGB);
    //float br = (sRGB.r+sRGB.g+sRGB.b)/3.0;
    //br = clamp(br,0.0,1.0);
    //vec3 exp = sRGB/br;
    Output = mix(dLook,sRGB,Equalize);
    Output = clamp(Output,0.0001,1.0);
    //float pcurve = textureCubic(PostCurve,vec2((Output.r+Output.g+Output.b)/3.0,0.5)).r;
    //Output.rgb/=(Output.r+Output.g+Output.b)/3.0;
    //Output = clamp(Output*pcurve,0.0,1.0);
}
