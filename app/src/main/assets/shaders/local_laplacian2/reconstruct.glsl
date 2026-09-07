precision highp float;
precision highp sampler2D;

#define FINE_RGB 0
#define FINAL_OUTPUT 0

uniform sampler2D FineBuffer;
uniform sampler2D CoarseBuffer;
uniform sampler2D ReconstructedBuffer;
// 2D remap LUT: u = intensity of the value being remapped, v = anchor
// intensity.  Bilinear filtering linearly blends the two neighbouring anchor
// curves, which is exactly the interpolation between discrete-anchor
// remapped pyramids of the paper.
uniform sampler2D RemapLut;
uniform ivec2 coarseSize;

#if FINAL_OUTPUT == 1
out vec4 Output;
#else
out float Output;
#endif

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

// The LUT's anchor axis: row k holds the curve for anchor
// gamma_k = (k + 0.5) / rows.  Because bilinear row centers sit exactly at
// (k + 0.5) / rows, the anchor texture coordinate of a pixel is its own
// Gaussian value and CLAMP_TO_EDGE clamps the interpolation weights.

float remappedCoarse(ivec2 q, float anchor) {
    float g = texelFetch(CoarseBuffer, clamp(q, ivec2(0), coarseSize - ivec2(1)), 0).r;
    return texture(RemapLut, vec2(clamp(g, 0.0, 1.0), anchor)).r;
}

float reconstructedCoarse(ivec2 q) {
    return texelFetch(ReconstructedBuffer, clamp(q, ivec2(0), coarseSize - ivec2(1)), 0).r;
}

// Exact 2x expansion phase and weights of the [1 4 6 4 1] binomial pyramid.
// Both coarse inputs are expanded in one tap enumeration: the Gaussian is
// remapped per texel BEFORE filtering (the filter-after-remap structure the
// local Laplacian relies on), the reconstructed level is expanded linearly.
// The anchor stays fixed for all taps of a pixel; taking it from the tap
// instead would collapse the pyramid back into a pointwise tone curve.
void expandPair(ivec2 p, float anchor, out float rebuiltBase, out float remappedCoarseSum) {
    ivec2 c = ivec2(p.x >> 1, p.y >> 1);
    bool oddX = (p.x & 1) != 0;
    bool oddY = (p.y & 1) != 0;
    rebuiltBase = 0.0;
    remappedCoarseSum = 0.0;

    if (!oddX && !oddY) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                float w = (x == 0 ? 6.0 : 1.0) * (y == 0 ? 6.0 : 1.0) / 64.0;
                ivec2 q = c + ivec2(x, y);
                rebuiltBase += w * reconstructedCoarse(q);
                remappedCoarseSum += w * remappedCoarse(q, anchor);
            }
        }
    } else if (oddX && !oddY) {
        for (int y = -1; y <= 1; y++) {
            float wy = (y == 0 ? 6.0 : 1.0) / 8.0;
            ivec2 q0 = c + ivec2(0, y);
            ivec2 q1 = c + ivec2(1, y);
            rebuiltBase += wy * 0.5 * (reconstructedCoarse(q0) + reconstructedCoarse(q1));
            remappedCoarseSum += wy * 0.5 * (remappedCoarse(q0, anchor) + remappedCoarse(q1, anchor));
        }
    } else if (!oddX && oddY) {
        for (int x = -1; x <= 1; x++) {
            float wx = (x == 0 ? 6.0 : 1.0) / 8.0;
            ivec2 q0 = c + ivec2(x, 0);
            ivec2 q1 = c + ivec2(x, 1);
            rebuiltBase += wx * 0.5 * (reconstructedCoarse(q0) + reconstructedCoarse(q1));
            remappedCoarseSum += wx * 0.5 * (remappedCoarse(q0, anchor) + remappedCoarse(q1, anchor));
        }
    } else {
        ivec2 q00 = c;
        ivec2 q10 = c + ivec2(1, 0);
        ivec2 q01 = c + ivec2(0, 1);
        ivec2 q11 = c + ivec2(1, 1);
        rebuiltBase = 0.25 * (reconstructedCoarse(q00) + reconstructedCoarse(q10)
                + reconstructedCoarse(q01) + reconstructedCoarse(q11));
        remappedCoarseSum = 0.25 * (remappedCoarse(q00, anchor) + remappedCoarse(q10, anchor)
                + remappedCoarse(q01, anchor) + remappedCoarse(q11, anchor));
    }
}

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
#if FINE_RGB == 1
    vec4 source = texelFetch(FineBuffer, p, 0);
    float luma = dot(source.rgb, LUMA);
#else
    float luma = texelFetch(FineBuffer, p, 0).r;
#endif
    float g = clamp(luma, 0.0, 1.0);
    float anchor = g;

    float rebuiltBase;
    float remappedCoarseSum;
    expandPair(p, anchor, rebuiltBase, remappedCoarseSum);

    float fineRemapped = texture(RemapLut, vec2(g, anchor)).r;
    float rebuilt = rebuiltBase + fineRemapped - remappedCoarseSum;

#if FINAL_OUTPUT == 1
    // Luma ratio carries the local contrast into chroma; the epsilon keeps
    // the gain bounded on near-black pixels instead of amplifying noise.
    const float eps = 1.0 / 4096.0;
    float gain = (rebuilt + eps) / (luma + eps);
    Output = vec4(max(source.rgb * gain, vec3(0.0)), source.a);
#else
    Output = rebuilt;
#endif
}
