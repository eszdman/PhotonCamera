//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include "halfConvert.h"
#include <cstddef>

namespace photon {
// Input and output may alias. Each vector load precedes its corresponding stores.
static inline uint16_t normalizeSample(uint16_t v, float bl, float invScale) {
    float f = ((float)v - bl) * invScale;
    if (f < 0.0f)
        f = 0.0f;
    if (f > 1.0f)
        f = 1.0f;
    return f32ToF16(f);
}

inline void normalizeRawPlane(const uint16_t* src, uint16_t* dst, int width, int height, float whiteLevel,
                              const float blackLevel[4]) {
    // Per-site inverse scale; guard against broken metadata (wl <= bl).
    float invScale[4];
    for (int c = 0; c < 4; c++) {
        float range = whiteLevel - blackLevel[c];
        invScale[c] = 1.0f / (range > 1.0f ? range : 1.0f);
    }

    for (int row = 0; row < height; row++) {
        const int siteRow = (row & 1) * 2;
        const uint16_t* srcRow = src + (size_t)row * width;
        uint16_t* dstRow = dst + (size_t)row * width;
#if PHOTON_F16_NEON
        // Sites alternate (bl0, bl1) on even rows and (bl2, bl3) on odd rows;
        // a 4-lane {a,b,a,b} vector covers 4 consecutive pixels.
        const float blPat[4] = {blackLevel[siteRow], blackLevel[siteRow + 1], blackLevel[siteRow],
                                blackLevel[siteRow + 1]};
        const float invPat[4] = {invScale[siteRow], invScale[siteRow + 1], invScale[siteRow],
                                 invScale[siteRow + 1]};
        const float32x4_t blV = vld1q_f32(blPat);
        const float32x4_t invV = vld1q_f32(invPat);
        const float32x4_t zeroV = vdupq_n_f32(0.0f);
        const float32x4_t oneV = vdupq_n_f32(1.0f);
        int x = 0;
        for (; x + 8 <= width; x += 8) {
            uint16x8_t v = vld1q_u16(srcRow + x);
            float32x4_t lo = vcvtq_f32_u32(vmovl_u16(vget_low_u16(v)));
            float32x4_t hi = vcvtq_f32_u32(vmovl_u16(vget_high_u16(v)));
            lo = vminq_f32(vmaxq_f32(vmulq_f32(vsubq_f32(lo, blV), invV), zeroV), oneV);
            hi = vminq_f32(vmaxq_f32(vmulq_f32(vsubq_f32(hi, blV), invV), zeroV), oneV);
            vst1_u16(dstRow + x, vreinterpret_u16_f16(vcvt_f16_f32(lo)));
            vst1_u16(dstRow + x + 4, vreinterpret_u16_f16(vcvt_f16_f32(hi)));
        }
        for (; x < width; x++) {
            int site = siteRow + (x & 1);
            dstRow[x] = normalizeSample(srcRow[x], blackLevel[site], invScale[site]);
        }
#else
        for (int x = 0; x < width; x++) {
            int site = siteRow + (x & 1);
            dstRow[x] = normalizeSample(srcRow[x], blackLevel[site], invScale[site]);
        }
#endif
    }
}

} // namespace photon
