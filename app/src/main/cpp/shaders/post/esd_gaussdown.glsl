//
// Created by eszdman on 21.11.2026.
//

// GLUtils.gaussdown(GLTexture, int) used by ESD3D2.
uniform sampler2D InputBuffer;
#define SCALE 2
out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) * SCALE;
    int stride = max(1, SCALE / 2);
    float sigma = 0.5 * float(SCALE) / float(stride);
    float kernel[5];
    for (int j=0; j<=2; ++j)
        kernel[2+j] = kernel[2-j] = 0.39894 * exp(-0.5*float(j*j)/(sigma*sigma)) / sigma;
    vec4 sum = vec4(0.0);
    float weights = 0.00001;
    for (int i=-2; i<=2; ++i) {
        for (int j=-2; j<=2; ++j) {
            float w = kernel[2+j] * kernel[2+i];
            sum += texelFetch(InputBuffer, xy+ivec2(i,j)*stride, 0) * w;
            weights += w;
        }
    }
    Output = sum / weights;
}
