precision highp float;
precision highp sampler2D;

#define INPUT_RGB 0

uniform sampler2D InputBuffer;
uniform ivec2 inputSize;
out float Output;

float luminanceAt(ivec2 p) {
    p = clamp(p, ivec2(0), inputSize - ivec2(1));
#if INPUT_RGB == 1
    return dot(texelFetch(InputBuffer, p, 0).rgb, vec3(0.2126, 0.7152, 0.0722));
#else
    return texelFetch(InputBuffer, p, 0).r;
#endif
}

void main() {
    // Gaussian reduction: separable [1 4 6 4 1] binomial kernel, then 2:1 decimation.
    ivec2 center = ivec2(gl_FragCoord.xy) * 2;
    const float kernel[5] = float[5](1.0, 4.0, 6.0, 4.0, 1.0);
    float sum = 0.0;
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            sum += luminanceAt(center + ivec2(x, y)) * kernel[x + 2] * kernel[y + 2];
        }
    }
    Output = sum / 256.0;
}
