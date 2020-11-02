#version 300 es
precision mediump sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform float factor;
uniform vec3 neutralPoint;
out vec3 result;
uniform int yOffset;

float gammaInverse(float x) {
    return (x <= 0.0031308*12.92) ? x / 12.92 : pow((x + 0.055)/1.055,2.2);
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    result = texelFetch(InputBuffer, xy, 0).rgb;
    result.r = gammaInverse(result.r);
    result.g = gammaInverse(result.g);
    result.b = gammaInverse(result.b);
    result = clamp(result*neutralPoint,vec3(0.0),neutralPoint);
}
