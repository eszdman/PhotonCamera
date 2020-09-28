#version 300 es

#define TILE_OFFSET 4
#define TILE_SCALE 8
#define TILE_SIZE 16
#define TILE_PX_COUNT 256.f

#define MIN_NOISE 640.f
#define MAX_NOISE 3200.f

precision mediump float;

uniform sampler2D refFrame;
uniform sampler2D altFrame;
uniform usampler2D alignment;

// Out
out vec4 result;

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);

    uvec4 xyAlign = texelFetch(alignment, xy, 0);
    ivec4 xAlign = ivec4(xyAlign % 256u) - 128;
    ivec4 yAlign = ivec4(xyAlign / 256u) - 128;

    ivec2 xyFrame = xy * TILE_SCALE;
    ivec2 xyRef;
    float refDataVal;
    vec4 altDataVal, noisef;

    vec4 currNoise = vec4(0.f);
    vec4 currYNoise;

    for (int y = 0; y < TILE_SIZE; y++) {
        currYNoise = vec4(0.f);
        for (int x = 0; x < TILE_SIZE; x++) {
            // Use a bayer pattern to speed up this comparison.
            if ((x + y) % 2 == 1) {
                //continue;
            }

            xyRef = xyFrame + ivec2(x, y) - TILE_OFFSET;
            refDataVal = texelFetch(refFrame, xyRef, 0).x;
            altDataVal.x = texelFetch(altFrame, xyRef + ivec2(xAlign.x, yAlign.x), 0).x;
            altDataVal.y = texelFetch(altFrame, xyRef + ivec2(xAlign.y, yAlign.y), 0).y;
            altDataVal.z = texelFetch(altFrame, xyRef + ivec2(xAlign.z, yAlign.z), 0).z;
            altDataVal.w = texelFetch(altFrame, xyRef + ivec2(xAlign.w, yAlign.w), 0).w;

            // All frame data is loaded, compare reference frame with other frames.
            // Penalize noise with linear error model.
            noisef = abs(altDataVal - refDataVal);
            currYNoise += noisef;
        }
        currNoise += currYNoise;
    }

    float factor = 8.f;                         // factor by which inverse function is elongated
    float min_dist = 10.f;                          // pixel L1 distance below which weight is maximal
    float max_dist = 300.f;                         // pixel L1 distance above which weight is zero

    vec4 dist = currNoise / 256.f;
    vec4 norm_dist = max(vec4(1.f), dist / factor - min_dist / factor);

    vec4 weight = mix(1.f / norm_dist, vec4(0.f), greaterThan(norm_dist, vec4(max_dist - min_dist)));

    result = weight;
}
