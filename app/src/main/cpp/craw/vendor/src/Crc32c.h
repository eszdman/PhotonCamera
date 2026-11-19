// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <cstring>

#if defined(__ARM_FEATURE_CRC32)
#include <arm_acle.h>
#endif

namespace craw::detail {

inline uint32_t crc32c(const void* data, size_t n, uint32_t crc = 0) {
    auto p = static_cast<const uint8_t*>(data);
    crc = ~crc;
#if defined(__ARM_FEATURE_CRC32)
    for (; n >= 8; n -= 8, p += 8) {
        uint64_t v;
        std::memcpy(&v, p, 8);
        crc = __crc32cd(crc, v);
    }
    for (; n; --n) crc = __crc32cb(crc, *p++);
#else
    static const auto table = [] {
        std::array<uint32_t, 256> t{};
        for (uint32_t i = 0; i < 256; ++i) {
            uint32_t c = i;
            for (int k = 0; k < 8; ++k) c = (c & 1) ? (c >> 1) ^ 0x82F63B78u : c >> 1;
            t[i] = c;
        }
        return t;
    }();
    for (; n; --n) crc = table[(crc ^ *p++) & 0xFF] ^ (crc >> 8);
#endif
    return ~crc;
}

}  // namespace craw::detail

namespace craw::detail {

// CRC32C of A||B from crc(A), crc(B) and len(B) (zlib's GF(2) matrix method),
// so frame bands can be checksummed in parallel.
inline uint32_t crc32cCombine(uint32_t crc1, uint32_t crc2, uint64_t len2) {
    if (len2 == 0) return crc1;
    auto times = [](const uint32_t* mat, uint32_t vec) {
        uint32_t sum = 0;
        for (; vec; vec >>= 1, ++mat)
            if (vec & 1) sum ^= *mat;
        return sum;
    };
    auto square = [&](uint32_t* sq, const uint32_t* mat) {
        for (int n = 0; n < 32; ++n) sq[n] = times(mat, mat[n]);
    };
    uint32_t even[32], odd[32];
    odd[0] = 0x82F63B78u;
    for (int n = 1; n < 32; ++n) odd[n] = 1u << (n - 1);
    square(even, odd);
    square(odd, even);
    do {
        square(even, odd);
        if (len2 & 1) crc1 = times(even, crc1);
        len2 >>= 1;
        if (!len2) break;
        square(odd, even);
        if (len2 & 1) crc1 = times(odd, crc1);
        len2 >>= 1;
    } while (len2);
    return crc1 ^ crc2;
}

}  // namespace craw::detail
