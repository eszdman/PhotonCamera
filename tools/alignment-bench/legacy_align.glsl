#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
uniform highp sampler2D prevAlignment;
uniform highp sampler2D baseTexture;
uniform highp sampler2D alterTexture;
uniform highp sampler2D baseCurve;
uniform highp sampler2D alterCurve;
layout(rgba16f, binding = 0) uniform highp writeonly image2D outTexture;

uniform float noiseS;
uniform float noiseO;
uniform int first;
uniform ivec2 rawHalf;
uniform float exposure;
uniform float integralNorm;

#define TILE_AL 16
#define TILE (TILE_AL/2)
#define M_PI 3.1415926535897932384626433832795
#define OFFSETS 5
#import median

shared mat4 inputDifferences[TILE*TILE]; // use this to store the 3x3 search grid images differences

vec4 getPixel(ivec2 coords, highp sampler2D tex) {
    return texelFetch(tex, coords, 0);
}

vec4 getPixelLaplacian(ivec2 coords, highp sampler2D tex) {
    ivec2 size = textureSize(tex, 0);
    coords = clamp(coords, ivec2(1), size - ivec2(2));
    vec4 center = texelFetch(tex, coords, 0);
    vec4 left = texelFetch(tex, coords + ivec2(-1, 0), 0);
    vec4 right = texelFetch(tex, coords + ivec2(1, 0), 0);
    vec4 up = texelFetch(tex, coords + ivec2(0, -1), 0);
    vec4 down = texelFetch(tex, coords + ivec2(0, 1), 0);
    return (left + right + up + down) - center * 3.0;
}

highp vec4 getAlignment(ivec2 coords) {
    coords = clamp(coords, ivec2(0), ivec2(textureSize(baseTexture, 0)/TILE_AL - 1));
    return texelFetch(prevAlignment, coords, 0);
}

highp vec4 alignmentToVec4(highp vec2 alignment) {
    highp vec4 converted = vec4(floor(alignment.x), floor(alignment.y), fract(alignment.x), fract(alignment.y));
    converted.xy /= vec2(rawHalf);
    return converted;
}

highp vec2 vec4ToAlignment(highp vec4 alignment) {
    vec2 converted = alignment.xy*vec2(rawHalf);
    converted.xy += alignment.zw;
    return converted;
}

float brightness(vec4 color) {
    /*if (dot(color, vec4(0.25)) < 0.5) {
        return max(max(color.r, color.g), max(color.b, color.a));
    } else {
        return min(min(color.r, color.g), min(color.b, color.a));
    }*/
    return dot(color, vec4(0.25));
    //return median4(color);
}

mat4 getSharedDifferences(ivec2 xy, ivec2 prevOffset) {
    mat4 differences;
    float value = 0.0;
    float maxNoise = sqrt(noiseS + noiseO)*1.0/integralNorm;
    vec4 baseValue = clamp(getPixel(xy, baseTexture), 0.000, 1.0);
    float baseBrightness = brightness(baseValue);
    //baseValue *= sqrt((baseValue*baseValue)/(baseValue*baseValue + maxNoise*maxNoise));
    //baseValue = clamp(baseValue / exposure, vec4(0.0), vec4(1.0));
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            //differences[i][j] = dot(abs(diff), vec4(0.25));
            vec4 alterValue = clamp(getPixel(xy + ivec2(i-1, j-1) + prevOffset, alterTexture), 0.0, exposure);
            //alterValue *= sqrt((alterValue*alterValue)/(alterValue*alterValue + maxNoise*maxNoise));
            //float luma = brightness(baseValue) - brightness(alterValue);
            //float luma = texture(baseCurve, vec2(brightness(baseValue), 0.5)).r - texture(alterCurve, vec2(brightness(alterValue),0.5)).r;
            //float luma = baseBrightness - brightness(alterValue);
            //float luma = texture(baseCurve, vec2(brightness(baseValue), 0.5)).r - texture(alterCurve, vec2(brightness(alterValue),0.5)).r;
            differences[i][j] = dot(abs(baseValue - alterValue), vec4(0.25));//abs(luma);
        }
    }
    //if(brightness(baseValue) > brightness(clamp(baseValue, 0.0, exposure)) || brightness(baseValue) < 0.001){
    if (baseBrightness > brightness(clamp(baseValue, 0.0, exposure)) || baseBrightness < 0.001) {
        differences *= 0.0;
    }
    return differences;
}

mat4 getOffsetDifferences(ivec2 xy) {
    mat4 differences;
    float maxNoise = sqrt(noiseS + noiseO)*1.0/integralNorm;
    vec4 baseValue = clamp(getPixel(xy, baseTexture), 0.000, 1.0);
    float baseBrightness = brightness(baseValue);
    //baseValue *= sqrt((baseValue*baseValue)/(baseValue*baseValue + maxNoise*maxNoise));
    //baseValue = clamp(baseValue / exposure, vec4(0.0), vec4(1.0));
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            vec2 prevOffset = vec4ToAlignment(getAlignment(xy/(2*TILE) + ivec2(i-1, j-1)))*2.0;
            if(i == 3 && j == 3) {
                prevOffset = vec2(0.0);
            }
            vec4 alterValue = clamp(getPixel(xy + ivec2(prevOffset), alterTexture), 0.0, exposure);
            //alterValue *= sqrt((alterValue*alterValue)/(alterValue*alterValue + maxNoise*maxNoise));
            //alterValue = (alterValue-alterMin) / (alterMax-alterMin);
            //differences[i][j] = dot(abs(diff), vec4(0.25));
            //float luma = median4(baseValue)-median4(alterValue);
            //float luma = texture(baseCurve, vec2(brightness(baseValue), 0.5)).r - texture(alterCurve, vec2(brightness(alterValue),0.5)).r;
            float luma = baseBrightness - brightness(alterValue);
            differences[i][j] = dot(abs(baseValue - alterValue), vec4(0.25));//abs(luma);
        }
    }
    if (baseBrightness > brightness(clamp(baseValue, 0.0, exposure)) || baseBrightness < 0.001) {
        differences *= 0.0;
    }
    return differences;
}

highp vec2 getPrevOffset(ivec2 tile_xy) {
    ivec2 localOffsets[OFFSETS];
    localOffsets[0] = ivec2(0, 0);
    localOffsets[1] = ivec2(1, 0);
    localOffsets[2] = ivec2(-1, 0);
    localOffsets[3] = ivec2(0, 1);
    localOffsets[4] = ivec2(0, -1);
/*#if OFFSETS > 4
    localOffsets[4] = ivec2(-1, 0);
    localOffsets[5] = ivec2(0, -1);
    localOffsets[6] = ivec2(-1, -1);
    localOffsets[7] = ivec2(-1, 1);
    localOffsets[8] = ivec2(1, -1);
#endif*/
    vec2 prevOffset = vec2(0.0);
    // Local thread ID within work group
    ivec2 localID = ivec2(gl_LocalInvocationID.xy) - ivec2(TILE/2, TILE/2); // 0 - TILE-1
    int localIndex = int(gl_LocalInvocationIndex); // 0 - TILE*TILE-1
    // Get previous alignment if not first level
    // split to 4 calls to increase scan window size
    // Decrease inputDifferences size to TILE*TILE
    mat4 temp = mat4(0.0);
    for (int i = 0; i < OFFSETS; i++) {
        temp += getOffsetDifferences((tile_xy+localOffsets[i]) * TILE + localID);
    }
    inputDifferences[localIndex] = temp;
    barrier();
    mat4 sum = mat4(0.0);
    // Parallel reduction for summing
    for (int stride = TILE * TILE / 2; stride > 0; stride >>= 1) {
        if (localIndex < stride) {
            inputDifferences[localIndex] += inputDifferences[localIndex + stride];
        }
        barrier();
    }

    sum = inputDifferences[0];
    // Use mat4 sum to find the best offset from (-1,-1) to (1,1)
    vec2 bestOffset = vec2(0.0, 0.0);
    float minDiff = sum[0][0];

    for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
            if (sum[i][j] < minDiff) {
                minDiff = sum[i][j];
                if(i == 3 && j == 3) {
                    bestOffset = vec2(0.0);
                } else {
                    bestOffset = vec2(i - 1, j - 1);
                }
            }
        }
    }
    prevOffset = vec4ToAlignment(getAlignment(tile_xy / 2 + ivec2(bestOffset))) * 2.0;
    return prevOffset;
}

// Compute alignment between base and alter textures
highp vec3 computeAlignment(ivec2 tile_xy, vec2 prevOffset) {
    // Fill inputDifferences array with 4 calls to getSharedDifferences
    ivec2 localOffsets[OFFSETS];
    localOffsets[0] = ivec2(0, 0);
    localOffsets[1] = ivec2(1, 0);
    localOffsets[2] = ivec2(-1, 0);
    localOffsets[3] = ivec2(0, 1);
    localOffsets[4] = ivec2(0, -1);
/*#if OFFSETS > 4
    localOffsets[4] = ivec2(-1, 0);
    localOffsets[5] = ivec2(0, -1);
    localOffsets[6] = ivec2(-1, -1);
    localOffsets[7] = ivec2(-1, 1);
    localOffsets[8] = ivec2(1, -1);
#endif*/
    // Local thread ID within work group
    ivec2 localID = ivec2(gl_LocalInvocationID.xy) - ivec2(TILE/2, TILE/2); // 0 - TILE-1
    int localIndex = int(gl_LocalInvocationIndex); // 0 - TILE*TILE-1
    // split to 4 calls to increase scan window size and sum calls
    mat4 temp = mat4(0.0);
    for (int i = 0; i < OFFSETS; i++) {
        int targetIndex = localIndex + i * TILE*TILE;
        temp += getSharedDifferences((tile_xy+localOffsets[i]) * TILE + localID, ivec2(prevOffset));
    }
    inputDifferences[localIndex] = temp;
    // Ensure all threads have written to shared memory
    barrier();
    // Sum the differences to get final mat4 sum
    mat4 sum = mat4(0.0);
    // Parallel reduction for summing
    for (int stride = TILE * TILE / 2; stride > 0; stride >>= 1) {
        if (localIndex < stride) {
            inputDifferences[localIndex] += inputDifferences[localIndex + stride];
        }
        barrier();
    }
    // First thread has the final sum
    sum = inputDifferences[0];
    // Use mat4 sum to find the best offset from (-1,-1) to (1,1)
    highp vec2 bestOffset = prevOffset;
    float minDiff = sum[0][0];

    for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
            if (sum[i][j] < minDiff) {
                minDiff = sum[i][j];
                bestOffset = prevOffset + vec2(i-1, j-1);
            }
        }
    }
    return vec3(bestOffset.x, bestOffset.y, minDiff);
}

void main() {
    //ivec2 tile_xy = ivec2(gl_GlobalInvocationID.xy)/TILE;
    ivec2 tile_xy = ivec2(gl_WorkGroupID.xy);
    int localIndex = int(gl_LocalInvocationIndex);
    // Get previous offset
    vec2 prevOffset = vec2(0.0);
    if(first == 0) {
        prevOffset = getPrevOffset(tile_xy);
    }

    // Compute alignment vector
    vec3 bestOffset = computeAlignment(tile_xy, prevOffset);
    bestOffset = computeAlignment(tile_xy, bestOffset.xy);
    //bestOffset = computeAlignment(tile_xy, bestOffset.xy);
    if (localIndex == 0) {
        // Store the best offset in the output texture
        imageStore(outTexture, tile_xy, alignmentToVec4(bestOffset.xy));
        //vec4 alter = clamp(getPixel(tile_xy*TILE, alterTexture), vec4(0.0), vec4(exposure));
        //vec4 base = clamp(getPixel(tile_xy*TILE, baseTexture), vec4(0.0), vec4(exposure));
        //float luma = texture(baseCurve, vec2(brightness(base), 0.5)).r - texture(alterCurve, vec2(brightness(alter),0.5)).r;
        //float luma = brightness(base) - brightness(alter);
        //float luma = brightness(alter);
        //float luma = texture(alterCurve, vec2(brightness(alter),0.5)).r;
        //imageStore(outTexture, tile_xy, abs(vec4(luma)));
    }
}
