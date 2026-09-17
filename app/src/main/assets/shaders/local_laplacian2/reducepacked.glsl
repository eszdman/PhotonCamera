precision highp float;
precision highp sampler2D;

uniform sampler2D InputBuffer; // packed (ANCHORS * inSize.x, inSize.y)
uniform ivec2 inSize;          // one column's size in the INPUT

out float Output;

// Gaussian reduction of every packed column independently; taps clamp
// inside their own column so anchors never mix.
float lumAt(ivec2 base, ivec2 q) {
    q = ivec2(clamp(base.x + q.x, base.x, base.x + inSize.x - 1),
              clamp(q.y, 0, inSize.y - 1));
    return texelFetch(InputBuffer, q, 0).r;
}

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    int wOut = (inSize.x + 1) / 2;
    int k = p.x / wOut;
    int x = p.x - k * wOut;
    ivec2 base = ivec2(k * inSize.x, 0);
    const float kernel[5] = float[5](1.0, 4.0, 6.0, 4.0, 1.0);
    float sum = 0.0;
    for (int y = -2; y <= 2; y++) {
        for (int x2 = -2; x2 <= 2; x2++) {
            sum += lumAt(base, ivec2(2 * x + x2, 2 * p.y + y)) * kernel[x2 + 2] * kernel[y + 2];
        }
    }
    Output = sum / 256.0;
}
