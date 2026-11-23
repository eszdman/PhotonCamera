//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include <stddef.h>
#include <stdint.h>
#if defined(__aarch64__) && defined(__ARM_NEON) && defined(__AARCH64EL__)
#include <arm_neon.h>
#endif

namespace photon {
// Scalar bitstream decode (reference implementation; also the tail handler
// and the fallback for bit depths without a fast path).
inline void unpackScalar(const uint8_t *packed, int64_t startByte, uint16_t *out,
                         int start, int pixels, int bits, uint32_t mask) {
    uint32_t acc = 0;
    int accBits = 0;
    int64_t inByte = startByte;
    for (int i = start; i < pixels; i++) {
        while (accBits < bits) {
            acc |= (uint32_t) packed[inByte++] << accBits;
            accBits += 8;
        }
        out[i] = (uint16_t) (acc & mask);
        acc >>= bits;
        accBits -= bits;
    }
}

// Four 10-bit samples occupy five consecutive little-endian bytes.
inline void unpackFast10(const uint8_t *packed, uint16_t *out, int pixels) {
    int i = 0;
#if defined(__aarch64__) && defined(__ARM_NEON) && defined(__AARCH64EL__)
    // Two overlapping loads cover exactly 20 bytes. Each lookup gathers
    // eight byte pairs into lanes, with one 10-bit sample inside each lane.
    const uint8x16_t firstPairs = {0,1, 1,2, 2,3, 3,4, 5,6, 6,7, 7,8, 8,9};
    const uint8x16_t lastPairs = {6,7, 7,8, 8,9, 9,10, 11,12, 12,13, 13,14, 14,15};
    const int16x8_t shifts = {0,-2,-4,-6, 0,-2,-4,-6};
    const uint16x8_t mask = vdupq_n_u16(1023);
    for (; i < (pixels & ~15); i += 16) {
        const uint8_t *group = packed + size_t(i / 4) * 5;
        uint16x8_t first = vreinterpretq_u16_u8(vqtbl1q_u8(vld1q_u8(group), firstPairs));
        uint16x8_t last = vreinterpretq_u16_u8(vqtbl1q_u8(vld1q_u8(group + 4), lastPairs));
        vst1q_u16(out + i, vandq_u16(vshlq_u16(first, shifts), mask));
        vst1q_u16(out + i + 8, vandq_u16(vshlq_u16(last, shifts), mask));
    }
#endif
    int n4 = pixels & ~3;
    for (; i < n4; i += 4) {
        const auto j = size_t(i / 4) * 5;
        uint32_t w = uint32_t(packed[j]) | (uint32_t(packed[j + 1]) << 8) |
                     (uint32_t(packed[j + 2]) << 16) | (uint32_t(packed[j + 3]) << 24);
        uint32_t b4 = packed[j + 4];
        out[i]     = (uint16_t) (w & 0x3FFu);
        out[i + 1] = (uint16_t) ((w >> 10) & 0x3FFu);
        out[i + 2] = (uint16_t) ((w >> 20) & 0x3FFu);
        out[i + 3] = (uint16_t) (((w >> 30) & 0x3u) | (b4 << 2));
    }
    if (n4 < pixels) {
        // 4-sample groups consume whole 5-byte units, so the tail always
        // restarts on a byte boundary.
        unpackScalar(packed, (int64_t) n4 * 10 / 8, out, n4, pixels, 10, 0x3FFu);
    }
}

inline void unpackSensorSamples(const uint8_t* packed, uint16_t* out, int pixels, unsigned bits) {
    if (bits == 10)
        unpackFast10(packed, out, pixels);
    else
        unpackScalar(packed, 0, out, 0, pixels, bits, (1u << bits) - 1u);
}
} // namespace photon
