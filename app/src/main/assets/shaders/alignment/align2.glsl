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
uniform float significancy;

#define TILE_AL 16
#define TILE (TILE_AL/2)
#define M_PI 3.1415926535897932384626433832795
#define OFFSETS 9
// Sub-texel refinement of the tile minimum by a bivariate quadratic fit of
// the 3x3 cost neighborhood: the fractional
// part is stored in the atlas (alignmentToVec4) and applied by mergeAlign as
// a bilinear warp, i.e. alignment to green sub-pixel granularity (1 quad
// texel = 2 raw px, so 0.5 sub-texel = 1 raw px).
#define SUBPIXEL 1
// Cost: plain L1 normalized by the local noise sigma (shot+read, scaled to
// the current pyramid level by integralNorm). NO truncation: a hot pixel is
// invisible after the normalize prefilter and the gaussian pyramid (it is
// ~1/25 of one channel of one tap), so clamping differences at k*sigma does
// not reject outliers - it only flattens the strong edges and fine texture
// the matcher runs on, which measurably destroys alignment on detailed real
// scenes (tools/alignment-bench). The noise normalization itself still
// matters: it downweights dark noisy pixels and puts the significance gate in
// statistical units.
#import median

// 1 = baseTexture/alterTexture hold sqrt-encoded (gamma ~2.0) values, see
// normalizebl.glsl SQRT_ENC; getPixel squares them back to linear so all cost
// math below is unchanged. prevAlignment is NOT affected (rgba16f vectors).
#define SQRT_DEC 0
shared mat4 inputDifferences[TILE*TILE]; // per-thread candidate costs, reduced to a tile total

vec4 getPixel(ivec2 coords, highp sampler2D tex) {
    vec4 c = texelFetch(tex, coords, 0);
#if SQRT_DEC
    return c * c;
#else
    return c;
#endif
}

highp vec4 getAlignment(ivec2 coords) {
    // Clamp to the prev-alignment tile grid, i.e. the dispatch grid of the
    // level above: floor(levelAboveWidth/8) == floor(2*thisTexWidth/8).
    // The old textureSize(baseTexture)/TILE_AL-1 bound collapsed to 0 (or
    // went undefined, min>max) at coarse levels narrower than ~32 texels,
    // scrambling the coarse-offset propagation exactly where large warps
    // need it most.
    coords = clamp(coords, ivec2(0), ivec2(textureSize(prevAlignment, 0)*2/TILE));
    return texelFetch(prevAlignment, coords, 0);
}

highp vec4 alignmentToVec4(highp vec2 alignment) {
    highp vec4 converted = vec4(floor(alignment.x), floor(alignment.y), fract(alignment.x), fract(alignment.y));
    converted.xy /= vec2(rawHalf);
    return converted;
}

highp vec2 vec4ToAlignment(highp vec4 alignment) {
    // Round the integer part: it is stored as floor(v)/rawHalf in an rgba16f
    // texture, and half-float precision reconstructs e.g. 2/480*480 as 1.9998.
    // Truncating that silently biases offsets by -1px. The fract part
    // (subpixel residual) is preserved for callers, which must floor().
    return floor(alignment.xy*vec2(rawHalf) + vec2(0.5)) + alignment.zw;
}

float brightness(vec4 color) {
    return dot(color, vec4(0.25));
}

// Per-pixel noise sigma at this pyramid level for the given base brightness.
float levelNoise(float baseBrightness) {
    // sigma per frame; the base-alter difference has sqrt(2) larger sigma,
    // which is folded into the significancy threshold instead of here.
    return max(sqrt(max(baseBrightness, 0.0) * noiseS + noiseO) / integralNorm, 1e-5);
}
float alignCost(vec4 baseValue, vec4 alterValue, float sigma) {
    // Plain noise-normalized L1, unclamped - see the cost comment above.
    return dot(abs(baseValue - alterValue) / vec4(sigma), vec4(0.25));
}

void reduceTileSum() {
    // Tree-reduce inputDifferences (one mat4 of candidate costs per thread)
    // into inputDifferences[0]; every call site reads the total only after
    // this returns.
    int localIndex = int(gl_LocalInvocationIndex);
    for (int stride = TILE * TILE / 2; stride > 0; stride >>= 1) {
        if (localIndex < stride) {
            inputDifferences[localIndex] += inputDifferences[localIndex + stride];
        }
        barrier();
    }
}

// Sum the tile cost of the 9 integer candidates windowCenter+{-1,0,1}^2 over
// the OFFSETS subtiles (a 24x24 texel window centered on the tile). The
// symmetric 3x3 window replaces the old asymmetric 4x4 (-1..2)^2: the +2
// diagonal slots were near-never selected and cost 7/16 more texture fetches
// per pixel; large corrections are the pyramid's job.
mat4 evaluateWindow(ivec2 tile_xy, ivec2 windowCenter) {
    ivec2 localOffsets[OFFSETS];
    localOffsets[0] = ivec2(0, 0);
    localOffsets[1] = ivec2(1, 0);
    localOffsets[2] = ivec2(-1, 0);
    localOffsets[3] = ivec2(0, 1);
    localOffsets[4] = ivec2(0, -1);
#if OFFSETS > 5
    localOffsets[5] = ivec2(-1, -1);
    localOffsets[6] = ivec2(-1, 1);
    localOffsets[7] = ivec2(1, -1);
    localOffsets[8] = ivec2(1, 1);
#endif
    ivec2 localID = ivec2(gl_LocalInvocationID.xy) - ivec2(TILE/2, TILE/2);
    int localIndex = int(gl_LocalInvocationIndex);
    mat4 acc = mat4(0.0);
    for (int s = 0; s < OFFSETS; s++) {
        ivec2 xy = (tile_xy + localOffsets[s]) * TILE + localID;
        vec4 baseValue = clamp(getPixel(xy, baseTexture), 0.000, 1.0);
        float baseBrightness = brightness(baseValue);
        float sigma = levelNoise(baseBrightness);
        // Base pixel unusable (clipped above the alter frame's exposure or below
        // the black floor): contribute a neutral 0 cost to every candidate so the
        // tile keeps the previous alignment instead of locking onto garbage.
        float baseWeight = (baseBrightness > brightness(clamp(baseValue, 0.0, exposure)) || baseBrightness < 0.001) ? 0.0 : 1.0;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec4 alterValue = clamp(getPixel(xy + windowCenter + ivec2(i, j), alterTexture), 0.0, exposure);
                acc[i+1][j+1] += alignCost(baseValue, alterValue, sigma) * baseWeight;
            }
        }
    }
    inputDifferences[localIndex] = acc;
    barrier();
    reduceTileSum();
    mat4 sum = inputDifferences[0];
    // All threads must finish reading the total before the next phase
    // overwrites the shared buffer.
    barrier();
    return sum;
}

// Pick the gated best candidate of an evaluated window. Returns the best
// offset (integer position, or 'center' unchanged - including its carried
// subpixel residual - when the significance gate rejects the move).
vec2 gatedArgmin(mat4 sum, vec2 center) {
    ivec2 windowCenter = ivec2(floor(center));
    float minDiff = sum[1][1];
    ivec2 rel = ivec2(0);
    // strict '<' with the center as the initial winner: ties keep the
    // previous position instead of the old (-1,-1)-corner bias
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            if (sum[i+1][j+1] < minDiff) {
                minDiff = sum[i+1][j+1];
                rel = ivec2(i, j);
            }
        }
    }
    // Significance gate: compare the cost improvement of the best candidate
    // over the previous alignment against the statistical noise of the summed
    // cost (CLT: std of the sum ~ sqrt(expected cost * N)). With the
    // noise-normalized L1 cost a correctly aligned tile averages ~1.13 (|N(0,
    // sqrt(2))| per pixel). If the improvement is below 'significancy'
    // standard deviations, the minimum is noise and we keep the previous
    // alignment. This stops textureless tiles from random-walking into
    // blocky misalignment while leaving genuine detail matches untouched
    // (k = 2.0, measured on real ProRAW bursts in tools/alignment-bench).
    // 'sum' comes from shared memory and is identical on every thread, so
    // the gate keeps the returned offset uniform.
    {
        float n = float(OFFSETS * TILE * TILE);
        float expected = 1.13; // mean per-pixel cost when aligned
        float thresh = significancy * sqrt(expected / n);
        float improvement = (sum[1][1] - minDiff) / n;
        if (improvement < thresh) {
            return center;
        }
    }
    return vec2(windowCenter + rel);
}

// Sub-texel minimum of the cost surface by a bivariate quadratic fit on the
// 3x3 neighborhood centered on the window center (which must be the accepted
// integer minimum - callers ensure that). Exact port of the IPOL 2021.336
// reference implementation (subPixelMinimum): least-squares A/b filters,
// positive-semi-definite fixup of A, and the <1 sub-texel displacement clamp.
// d[k] with k=(j+1)*3+(i+1), (i,j) the (x,y) local displacement; the
// reference's osvJ/osvI map to the x/y axes in that order.
bool subpixelMinimum(mat4 sum, out vec2 delta) {
    float d[9];
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            d[(j+1)*3 + (i+1)] = sum[i+1][j+1];
        }
    }
    float A11 = 0.25*d[0] - 0.50*d[1] + 0.25*d[2]
              + 0.50*d[3] - 1.00*d[4] + 0.50*d[5]
              + 0.25*d[6] - 0.50*d[7] + 0.25*d[8];
    float A22 = 0.25*d[0] + 0.50*d[1] + 0.25*d[2]
              - 0.50*d[3] - 1.00*d[4] - 0.50*d[5]
              + 0.25*d[6] + 0.50*d[7] + 0.25*d[8];
    float A12 = 0.25*d[0] - 0.25*d[2] - 0.25*d[6] + 0.25*d[8];
    float b1  = -0.125*d[0] + 0.125*d[2]
              - 0.250*d[3] + 0.250*d[5]
              - 0.125*d[6] + 0.125*d[8];
    float b2  = -0.125*d[0] - 0.250*d[1] - 0.125*d[2]
              + 0.125*d[6] + 0.250*d[7] + 0.125*d[8];
    // Enforce that A is positive semi-definite so the critical point is a
    // minimum, then solve A*delta = -b.
    A11 = max(A11, 0.0);
    A22 = max(A22, 0.0);
    if (A11 * A22 - A12 * A12 < 0.0) A12 = 0.0;
    float det = A11 * A22 - A12 * A12;
    if (det == 0.0) return false;
    float osvI = -(A11 * b2 - A12 * b1) / det; // y
    float osvJ = -(A22 * b1 - A12 * b2) / det; // x
    vec2 off = vec2(osvJ, osvI);
    // Only keep displacements smaller than one sub-texel step
    if (dot(off, off) >= 1.0) return false;
    delta = off;
    return true;
}

mat4 getOffsetDifferences(ivec2 xy) {
    mat4 differences;
    vec4 baseValue = clamp(getPixel(xy, baseTexture), 0.000, 1.0);
    float baseBrightness = brightness(baseValue);
    float sigma = levelNoise(baseBrightness);
    float baseWeight = (baseBrightness > brightness(clamp(baseValue, 0.0, exposure)) || baseBrightness < 0.001) ? 0.0 : 1.0;
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            vec2 prevOffset = vec4ToAlignment(getAlignment(xy/(2*TILE) + ivec2(i-1, j-1)))*2.0;
            if(i == 3 && j == 3) {
                prevOffset = vec2(0.0);
            }
            // floor, not ivec2() truncation: prevOffset may carry a subpixel
            // fract and trunc rounds the wrong way for negative offsets
            vec4 alterValue = clamp(getPixel(xy + ivec2(floor(prevOffset)), alterTexture), 0.0, exposure);
            differences[i][j] = alignCost(baseValue, alterValue, sigma) * baseWeight;
        }
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
#if OFFSETS > 5
    localOffsets[5] = ivec2(-1, -1);
    localOffsets[6] = ivec2(-1, 1);
    localOffsets[7] = ivec2(1, -1);
    localOffsets[8] = ivec2(1, 1);
#endif
    // Local thread ID within work group
    ivec2 localID = ivec2(gl_LocalInvocationID.xy) - ivec2(TILE/2, TILE/2); // 0 - TILE-1
    int localIndex = int(gl_LocalInvocationIndex); // 0 - TILE*TILE-1
    // Get previous alignment if not first level
    // split to 4 calls to increase scan window size
    // Decrease inputDifferences size to TILE*TILE
    // NOTE: deliberately kept array-free (the old getOffsetDifferences
    // structure) - a dedup variant that preloaded the 16 neighbor-candidate
    // offsets into local arrays measured 2.4x slower: the dynamically
    // indexed arrays spill to scratch memory, while these uniform-address
    // texelFetches are L1 broadcasts.
    mat4 temp = mat4(0.0);
    for (int i = 0; i < OFFSETS; i++) {
        temp += getOffsetDifferences((tile_xy+localOffsets[i]) * TILE + localID);
    }
    inputDifferences[localIndex] = temp;
    barrier();
    reduceTileSum();
    mat4 sum = inputDifferences[0];
    barrier();

    // Use the mat4 sum to find the best offset from the 4x4 neighborhood of
    // coarse alignments (slot (3,3) = keep zero displacement)
    vec2 bestOffset = vec2(0.0);
    float minDiff = sum[0][0];
    for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
            if (sum[i][j] < minDiff) {
                minDiff = sum[i][j];
                if (i == 3 && j == 3) {
                    bestOffset = vec2(0.0);
                } else {
                    bestOffset = vec2(i - 1, j - 1);
                }
            }
        }
    }
    return vec4ToAlignment(getAlignment(tile_xy / 2 + ivec2(bestOffset))) * 2.0;
}

void main() {
    ivec2 tile_xy = ivec2(gl_WorkGroupID.xy);
    int localIndex = int(gl_LocalInvocationIndex);
    // Get previous offset
    vec2 prevOffset = vec2(0.0);
    if (first == 0) {
        prevOffset = getPrevOffset(tile_xy);
    }

    // Coarse-to-fine refinement, one symmetric 3x3 window at a time. The old
    // unconditional second iteration re-evaluated an identical candidate set
    // whenever the first step found no move (the common case after good
    // coarse propagation), so it is skipped when the integer center did not
    // move; a moved tile gets re-centered once more, preserving the old
    // +-2 texel/level reach.
    mat4 sum = evaluateWindow(tile_xy, ivec2(floor(prevOffset)));
    vec2 best = gatedArgmin(sum, prevOffset);
    bool fitReady = ivec2(floor(best)) == ivec2(floor(prevOffset));
    if (!fitReady) {
        vec2 center2 = best;
        mat4 sum2 = evaluateWindow(tile_xy, ivec2(floor(center2)));
        best = gatedArgmin(sum2, center2);
        sum = sum2;
        // The 3x3 fit neighborhood around the final integer minimum is fully
        // inside the last evaluated window only if the second step did not
        // march to that window's edge; tiles that keep sliding (>=2 texels
        // at this level) stay on their integer offset.
        fitReady = ivec2(floor(best)) == ivec2(floor(center2));
    }
#if SUBPIXEL
    if (localIndex == 0) {
        vec2 delta;
        if (fitReady && subpixelMinimum(sum, delta)) {
            best = vec2(ivec2(floor(best))) + delta;
        }
        imageStore(outTexture, tile_xy, alignmentToVec4(best));
    }
#else
    if (localIndex == 0) {
        imageStore(outTexture, tile_xy, alignmentToVec4(ivec2(floor(best))));
    }
#endif
}
