#version 300 es
precision highp sampler2D;
precision highp float;
uniform int samplingFactor;
uniform sampler2D InputBuffer;
out vec4 Output;
#import xyztoxyy
void main() {
    ivec2 xy = samplingFactor * ivec2(gl_FragCoord.xy);
    vec3[9]inp;
    for (int i = 0; i < 9; i++) {
        inp[i] = XYZtoxyY(texelFetch(InputBuffer, xy + 2*ivec2((i % 3) - 1, (i / 3) - 1), 0).rgb);
    }
    vec3 mean, sigma;
    for (int i = 0; i < 9; i++) {
        mean += inp[i];
    }
    mean /= 9.f;
    for (int i = 0; i < 9; i++) {
        vec3 diff = mean - inp[i];
        sigma += diff * diff;
    }

    float z = XYZtoxyY(texelFetch(InputBuffer, xy, 0).rgb).z;
    Output = vec4(sqrt(sigma / 9.f), z);
}
