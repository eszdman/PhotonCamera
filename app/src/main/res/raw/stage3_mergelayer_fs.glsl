

#define TILE_OFFSET 8
#define TILE_SCALE 16
#define TILE_SIZE 32

#define M_PI 3.1415926535897932384626433832795f
#define M_2PI 6.28318530718f

precision mediump float;

uniform usampler2D altFrame1;
uniform usampler2D altFrame2;
uniform usampler2D altFrame3;
uniform usampler2D altFrame4;
uniform usampler2D refFrame;

uniform usampler2D alignment;
uniform sampler2D alignmentWeight;
uniform int alignCount;

uniform ivec2 frameSize;

const vec4 sumVec4 = vec4(1.f);

// Out
out int result;

vec4 getAlignedVals(ivec2 xy, ivec4 xAlign, ivec4 yAlign) {
    return vec4(
        texelFetch(altFrame1, xy + ivec2(xAlign.x, yAlign.x), 0).x,
        texelFetch(altFrame2, xy + ivec2(xAlign.y, yAlign.y), 0).x,
        texelFetch(altFrame3, xy + ivec2(xAlign.z, yAlign.z), 0).x,
        texelFetch(altFrame4, xy + ivec2(xAlign.w, yAlign.w), 0).x
    );
}

vec4 getAlignedVals(ivec2 xy, ivec2 xyTile) {
    uvec4 xyAlign = texelFetch(alignment, xyTile, 0);
    ivec4 xAlign = (ivec4(xyAlign % 256u) - 128) * 2;
    ivec4 yAlign = (ivec4(xyAlign / 256u) - 128) * 2;
    return getAlignedVals(xy, xAlign, yAlign);
}

void main() {
    // Shift coords from optimized to real
    ivec2 xy = ivec2(gl_FragCoord.xy);

    // Divide by TILE_SCALE, so we select the alignments for the current tile.
    ivec2 xyTileDiv = xy / TILE_SCALE;
    ivec2 xyTileMod = (xy % TILE_SCALE) + TILE_OFFSET;
    vec2 xyTileInterp = vec2(float(xyTileMod.x), float(xyTileMod.y)); // [8, 23] -> [8.5, 23.5]
    vec2 xyTileInterpFactor = (vec2(xyTileInterp) + 0.5f) / float(TILE_SIZE); // <0.25, 0.75>

    // 0 -> 0; 0.25 -> 0.5; 0.5 -> 1; 0.75 -> 0.5; 1 -> 0.
    // Multiply it directly with Mid, and inverted with Corner.
    vec2 xyTileInterpFactorCos = 0.5f - 0.5f * vec2(
        cos(M_2PI * xyTileInterpFactor.x),
        cos(M_2PI * xyTileInterpFactor.y)
    );
    vec2 xyTileInterpFactorCosInv = 1.f - xyTileInterpFactorCos;

    // Which other tiles to sample.
    int dx = xyTileInterpFactor.x < 0.5f ? -1 : 1;
    int dy = xyTileInterpFactor.y < 0.5f ? -1 : 1;

    // Middle. 00
    vec4 xyAlignMidWeight = texelFetch(alignmentWeight, xyTileDiv, 0);
    vec4 xyAlignMidVal = getAlignedVals(xy, xyTileDiv);

    // Left or Right. 10
    vec4 xyAlignHorzWeight = texelFetch(alignmentWeight, xyTileDiv + ivec2(dx, 0), 0);
    vec4 xyAlignHorzVal = getAlignedVals(xy, xyTileDiv + ivec2(dx, 0));

    // Top or Bottom. 01
    vec4 xyAlignVertWeight = texelFetch(alignmentWeight, xyTileDiv + ivec2(0, dy), 0);
    vec4 xyAlignVertVal = getAlignedVals(xy, xyTileDiv + ivec2(0, dy));

    // Corner. 11
    vec4 xyAlignCornerWeight = texelFetch(alignmentWeight, xyTileDiv + ivec2(dx, dy), 0);
    vec4 xyAlignCornerVal = getAlignedVals(xy, xyTileDiv + ivec2(dx, dy));

    // Reference pixel.
    float px = float(texelFetch(refFrame, xy, 0).x);
    float pxWeight = 1.f;

    // Cosine window, so middle only gets the mid pixel, and the edge gets split 50/50.
    xyAlignMidWeight *= xyTileInterpFactorCos.x * xyTileInterpFactorCos.y;
    xyAlignHorzWeight *= xyTileInterpFactorCosInv.x * xyTileInterpFactorCos.y;
    xyAlignVertWeight *= xyTileInterpFactorCos.x * xyTileInterpFactorCosInv.y;
    xyAlignCornerWeight *= xyTileInterpFactorCosInv.x * xyTileInterpFactorCosInv.y;

    // Order for spatial merge and temporal merge is inverted compared to HDR-Plus,
    // but the result should be the same as it's just a linear combination.
    // Add all altFrames at once.
    px += dot(xyAlignMidWeight, xyAlignMidVal);
    px += dot(xyAlignHorzWeight, xyAlignHorzVal);
    px += dot(xyAlignVertWeight, xyAlignVertVal);
    px += dot(xyAlignCornerWeight, xyAlignCornerVal);

    pxWeight += dot(xyAlignMidWeight, sumVec4);
    pxWeight += dot(xyAlignHorzWeight, sumVec4);
    pxWeight += dot(xyAlignVertWeight, sumVec4);
    pxWeight += dot(xyAlignCornerWeight, sumVec4);

    result = int(round(px / pxWeight));
}
