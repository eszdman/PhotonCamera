

#define FLT_MAX 3.402823466e+38

#define TILE_SCALE 8
#define TILE_SIZE 16

#define TILE_MIN_INDEX -4
#define TILE_MAX_INDEX 12

#define TILE_PX_COUNT 256

// Should be at least TILE_SCALE / 2.
#define ALIGN_MIN_SHIFT -4
#define ALIGN_MAX_SHIFT 4
#define ALIGN_TOTAL_SHIFTS 9

precision mediump float;

uniform sampler2D refFrameHorz;
uniform sampler2D refFrameVert;
uniform sampler2D altFrameHorz;
uniform sampler2D altFrameVert;

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

    int x, y;
    float refDataVal;
    float refDataHorz[TILE_SIZE]; // Horizontally integrated, so a vertical line of data.
    float refDataVert[TILE_SIZE]; // Vertically integrated, so a horizontal line of data.

    // Init from texture.
    for (int i = 0; i < TILE_SIZE; i++) {
        refDataHorz[i] = texelFetch(refFrameHorz, xyFrame + ivec2(0, i + TILE_MIN_INDEX), 0).x;
        refDataVert[i] = texelFetch(refFrameVert, xyFrame + ivec2(i + TILE_MIN_INDEX, 0), 0).x;
    }

    // Optimize the bestXShift and bestYShift by minimizing bestNoise.
    ivec4 bestXShift, bestYShift;
    vec4 bestNoise = vec4(FLT_MAX);

    // Varying variables.
    int altDataValIndex;
    vec4 altDataVal;
    vec4 altDataVert[TILE_SIZE];
    ivec2 xyShifted, xyIndex;
    vec4 noisef;
    vec4 currXNoise, currYNoise, currNoise;
    for (int dY = ALIGN_MIN_SHIFT; dY <= ALIGN_MAX_SHIFT; dY++) {
        // Preload all vertical integrations for this row.
        xyShifted = xyFrame + ivec2(TILE_MIN_INDEX + ALIGN_MIN_SHIFT, dY);
        for (x = 0; x < TILE_SIZE + ALIGN_TOTAL_SHIFTS; x++) {
            xyIndex = xyShifted + ivec2(x, 0);
            altDataVert[x].x = texelFetch(altFrameVert, xyIndex + ivec2(xAlign.x, yAlign.x), 0).x;
            altDataVert[x].y = texelFetch(altFrameVert, xyIndex + ivec2(xAlign.y, yAlign.y), 0).y;
            altDataVert[x].z = texelFetch(altFrameVert, xyIndex + ivec2(xAlign.z, yAlign.z), 0).z;
            altDataVert[x].w = texelFetch(altFrameVert, xyIndex + ivec2(xAlign.w, yAlign.w), 0).w;
        }

        for (int dX = ALIGN_MIN_SHIFT; dX <= ALIGN_MAX_SHIFT; dX++) {
            currXNoise = vec4(0.f);
            currYNoise = vec4(0.f);
            xyShifted = xyFrame + ivec2(dX, dY);

            // Check all horizontally integrated rows by doing expensive texelFetches.
            for (y = TILE_MIN_INDEX; y < TILE_MAX_INDEX; y++) {
                xyIndex = xyShifted + ivec2(0, y);
                altDataVal.x = texelFetch(altFrameHorz, xyIndex + ivec2(xAlign.x, yAlign.x), 0).x;
                altDataVal.y = texelFetch(altFrameHorz, xyIndex + ivec2(xAlign.y, yAlign.y), 0).y;
                altDataVal.z = texelFetch(altFrameHorz, xyIndex + ivec2(xAlign.z, yAlign.z), 0).z;
                altDataVal.w = texelFetch(altFrameHorz, xyIndex + ivec2(xAlign.w, yAlign.w), 0).w;

                // All frame data is loaded, compare reference frame with other frames.
                // Linear noise model.
                noisef = abs(altDataVal - refDataHorz[y - TILE_MIN_INDEX]);
                currYNoise += noisef;
            }

            // Check all vertically integrated rows from cache.
            for (x = 0; x < TILE_SIZE; x++) {
                altDataVal = altDataVert[x + (dX - ALIGN_MIN_SHIFT)];

                // All frame data is loaded, compare reference frame with other frames.
                // Linear noise model.
                noisef = abs(altDataVal - refDataVert[x]);
                currXNoise += noisef;
            }

            currNoise = currXNoise + currYNoise;

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
