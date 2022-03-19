

#define FLT_MAX 3.402823466e+38

#define TILE_OFFSET 4
#define TILE_SCALE 8
#define TILE_SIZE 16
#define TILE_PX_COUNT 256

#define ALIGN_MAX_SHIFT 4

precision mediump float;

uniform sampler2D refFrame;
uniform sampler2D altFrame;

uniform usampler2D prevLayerAlign;
uniform int prevLayerScale;

out uvec4 result;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec4 xAlign = ivec4(0);
    ivec4 yAlign = ivec4(0);
    if (prevLayerScale > 0) {
        uvec4 xyAlign = texelFetch(prevLayerAlign, xy / prevLayerScale, 0);
        xAlign = (ivec4(xyAlign % 256u) - 128) * prevLayerScale;
        yAlign = (ivec4(xyAlign / 256u) - 128) * prevLayerScale;
    }

    ivec2 xyFrame = xy * TILE_SCALE;
    float refData[TILE_PX_COUNT];
    for (int i = 0; i < TILE_PX_COUNT; i++) {
        ivec2 xyRef = xyFrame + ivec2(i % TILE_SIZE, i / TILE_SIZE) - TILE_OFFSET;
        refData[i] = texelFetch(refFrame, xyRef, 0).x;
    }

    ivec4 bestXShift, bestYShift;
    vec4 bestNoise = vec4(FLT_MAX);

    int shiftedY, shiftedX;
    bool isYInCache;
    float refDataVal;
    vec4 altDataVal;
    ivec2 xyRef;
    vec4 noisef;

    vec4 currYNoise, currNoise;
    for (int dY = -ALIGN_MAX_SHIFT; dY <= ALIGN_MAX_SHIFT; dY++) {
        for (int dX = -ALIGN_MAX_SHIFT; dX <= ALIGN_MAX_SHIFT; dX++) {
            // Iterate over refData, processing all altData frames simultaneously.
            currNoise = vec4(0.f);
            for (int y = 0; y < TILE_SIZE; y++) {
                shiftedY = y + dY;
                currYNoise = vec4(0.f);
                for (int x = 0; x < TILE_SIZE; x++) {
                    // RefData is always in cache.
                    refDataVal = refData[y * TILE_SIZE + x];
                    shiftedX = x + dX;

                    // Do a slow texelFetch.
                    xyRef = xyFrame + ivec2(shiftedX, shiftedY) - TILE_OFFSET;
                    altDataVal.x = texelFetch(altFrame, xyRef + ivec2(xAlign.x, yAlign.x), 0).x;
                    altDataVal.y = texelFetch(altFrame, xyRef + ivec2(xAlign.y, yAlign.y), 0).y;
                    altDataVal.z = texelFetch(altFrame, xyRef + ivec2(xAlign.z, yAlign.z), 0).z;
                    altDataVal.w = texelFetch(altFrame, xyRef + ivec2(xAlign.w, yAlign.w), 0).w;

                    // All frame data is loaded, compare reference frame with other frames.
                    // Linear noise model.
                    noisef = abs(altDataVal - refDataVal);
                    currYNoise += noisef;
                }
                currNoise += currYNoise;
            }

            // Manually update the four frames' best shift.
            if (currNoise.x < bestNoise.x) {
                bestNoise.x = currNoise.x;
                bestXShift.x = dX;
                bestYShift.x = dY;
            }
            if (currNoise.y < bestNoise.y) {
                bestNoise.y = currNoise.y;
                bestXShift.y = dX;
                bestYShift.y = dY;
            }
            if (currNoise.z < bestNoise.z) {
                bestNoise.z = currNoise.z;
                bestXShift.z = dX;
                bestYShift.z = dY;
            }
            if (currNoise.w < bestNoise.w) {
                bestNoise.w = currNoise.w;
                bestXShift.w = dX;
                bestYShift.w = dY;
            }
        }
    }

    // Vectorizing this mathematical operation seems to create bugs.
    result = 256u * uvec4(
        uint(yAlign.x + bestYShift.x + 128),
        uint(yAlign.y + bestYShift.y + 128),
        uint(yAlign.z + bestYShift.z + 128),
        uint(yAlign.w + bestYShift.w + 128)
    ) + uvec4(
        uint(xAlign.x + bestXShift.x + 128),
        uint(xAlign.y + bestXShift.y + 128),
        uint(xAlign.z + bestXShift.z + 128),
        uint(xAlign.w + bestXShift.w + 128)
    );
}
