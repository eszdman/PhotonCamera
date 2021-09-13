#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D LookupTable;
uniform int yOffset;
out vec4 Output;
#define LUT 0
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
    highp vec3 newColor1 = texture(LookupTable, texPos1).rgb;
    highp vec3 newColor2 = texture(LookupTable, texPos2).rgb;
    highp vec3 newColor = (mix(newColor1, newColor2, fract(blueColor)));
    return newColor;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    Output.rgb = texelFetch(InputBuffer, (xy), 0).rgb;
    #if LUT == 1
    Output.rgb = lookup(Output.rgb);
    #endif
    Output.rgb/=(Output.r*0.299 + Output.g*0.587 + Output.b*0.114)*3.0;
    Output.a = 1.0;
    Output = clamp(Output,0.0,1.0);
}
