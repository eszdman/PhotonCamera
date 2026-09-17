precision highp float;
precision highp sampler2D;

#define ANCHORS 24

uniform sampler2D PrevRecon;    // reconstruction of level l+1
uniform sampler2D PackedFine;   // per-anchor remapped pyramid at level l
uniform sampler2D PackedCoarse; // per-anchor remapped pyramid at level l+1
uniform sampler2D FineBuffer;   // luma Gaussian at level l (anchor source)
uniform ivec2 fineSize;         // level l size (== one PackedFine column)
uniform ivec2 coarseSize;       // level l+1 size (== one PackedCoarse column)

out float Output;

float reconAt(ivec2 q) {
    return texelFetch(PrevRecon, clamp(q, ivec2(0), coarseSize - ivec2(1)), 0).r;
}

float packedFineAt(int k, ivec2 q) {
    q = clamp(q, ivec2(0), fineSize - ivec2(1));
    return texelFetch(PackedFine, ivec2(k * fineSize.x + q.x, q.y), 0).r;
}

float packedCoarseAt(int k, ivec2 q) {
    q = clamp(q, ivec2(0), coarseSize - ivec2(1));
    return texelFetch(PackedCoarse, ivec2(k * coarseSize.x + q.x, q.y), 0).r;
}

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    float v = clamp(texelFetch(FineBuffer, p, 0).r, 0.0, 1.0);
    // anchor brackets, gamma_k = (k + 0.5) / ANCHORS rows
    float t = v * float(ANCHORS) - 0.5;
    int lo = clamp(int(floor(t)), 0, ANCHORS - 2);
    float a = clamp(t - float(lo), 0.0, 1.0);
    int hi = lo + 1;

    // lap(p) = mix(R_lo, R_hi, a)(p) - expand(mix(R_lo, R_hi, a))(p):
    // the fine term is taken at p itself, the coarse term accumulates
    // over the expansion taps around c.  Same tap enumeration as the
    // reconstruction expansion below.
    float fineRemapped = mix(packedFineAt(lo, p), packedFineAt(hi, p), a);

    ivec2 c = ivec2(p.x >> 1, p.y >> 1);
    bool oddX = (p.x & 1) != 0;
    bool oddY = (p.y & 1) != 0;
    float rebuiltBase = 0.0;
    float remappedCoarse = 0.0;

    if (!oddX && !oddY) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                float w = (x == 0 ? 6.0 : 1.0) * (y == 0 ? 6.0 : 1.0) / 64.0;
                ivec2 q = c + ivec2(x, y);
                rebuiltBase += w * reconAt(q);
                remappedCoarse += w * mix(packedCoarseAt(lo, q), packedCoarseAt(hi, q), a);
            }
        }
    } else if (oddX && !oddY) {
        for (int y = -1; y <= 1; y++) {
            float wy = (y == 0 ? 6.0 : 1.0) / 8.0;
            ivec2 q0 = c + ivec2(0, y);
            ivec2 q1 = c + ivec2(1, y);
            rebuiltBase += wy * 0.5 * (reconAt(q0) + reconAt(q1));
            remappedCoarse += wy * 0.5 * (mix(packedCoarseAt(lo, q0), packedCoarseAt(hi, q0), a)
                    + mix(packedCoarseAt(lo, q1), packedCoarseAt(hi, q1), a));
        }
    } else if (!oddX && oddY) {
        for (int x = -1; x <= 1; x++) {
            float wx = (x == 0 ? 6.0 : 1.0) / 8.0;
            ivec2 q0 = c + ivec2(x, 0);
            ivec2 q1 = c + ivec2(x, 1);
            rebuiltBase += wx * 0.5 * (reconAt(q0) + reconAt(q1));
            remappedCoarse += wx * 0.5 * (mix(packedCoarseAt(lo, q0), packedCoarseAt(hi, q0), a)
                    + mix(packedCoarseAt(lo, q1), packedCoarseAt(hi, q1), a));
        }
    } else {
        ivec2 q00 = c;
        ivec2 q10 = c + ivec2(1, 0);
        ivec2 q01 = c + ivec2(0, 1);
        ivec2 q11 = c + ivec2(1, 1);
        rebuiltBase = 0.25 * (reconAt(q00) + reconAt(q10) + reconAt(q01) + reconAt(q11));
        remappedCoarse = 0.25 * (
                mix(packedCoarseAt(lo, q00), packedCoarseAt(hi, q00), a)
              + mix(packedCoarseAt(lo, q10), packedCoarseAt(hi, q10), a)
              + mix(packedCoarseAt(lo, q01), packedCoarseAt(hi, q01), a)
              + mix(packedCoarseAt(lo, q11), packedCoarseAt(hi, q11), a));
    }

    Output = rebuiltBase + fineRemapped - remappedCoarse;
}
