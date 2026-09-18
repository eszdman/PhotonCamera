//
// Shared float -> half conversion helpers.
//
// The allocator (rawF16.cpp) and the KernelNet JNI (ncnnMl.cpp) both write
// half-float buffers that are uploaded straight into FLOAT_16 GL textures.
// Doing the conversion here keeps it NEON-vectorized and in one place: the
// alternative (uploading 32-bit floats with GL_FLOAT) makes the GL driver
// convert every texel, which is a large per-shot cost on the KernetNet map.
//

#ifndef PHOTONCAMERA_HALFCONVERT_H
#define PHOTONCAMERA_HALFCONVERT_H

#include <cstdint>
#include <cstring>

// NEON f16<->f32 conversions: always present on arm64; on armv7 they need the
// FP16 extension. Everything else falls back to the portable scalar path.
#if defined(__aarch64__)
#define PHOTON_F16_NEON 1
#elif defined(__ARM_NEON) && defined(__ARM_NEON_FP) && (__ARM_NEON_FP & 2)
#define PHOTON_F16_NEON 1
#else
#define PHOTON_F16_NEON 0
#endif

#if PHOTON_F16_NEON
#include <arm_neon.h>
#endif

// Portable float -> half with round-to-nearest-even. Values fed in are already
// clamped to [0,1] (or small model outputs), so the subnormal/Inf branches are
// only safety nets, but they keep the helper correct for arbitrary inputs.
static inline uint16_t f32ToF16(float value) {
    uint32_t x;
    memcpy(&x, &value, sizeof(x));
    uint32_t sign = (x >> 16) & 0x00008000u;
    int32_t exp = (int32_t)((x >> 23) & 0x000000FFu) - 127 + 15;
    uint32_t man = x & 0x007FFFFFu;
    if (((x >> 23) & 0xFFu) == 0xFFu) {
        // Inf / NaN
        return (uint16_t)(sign | 0x7C00u | (man ? 0x0200u : 0u));
    }
    if (exp >= 0x1F) {
        // overflow -> Inf
        return (uint16_t)(sign | 0x7C00u);
    }
    if (exp <= 0) {
        // subnormal or underflow to zero
        if (exp < -10) return (uint16_t)sign;
        man |= 0x00800000u;
        uint32_t shift = (uint32_t)(14 - exp);
        uint32_t half = man >> shift;
        uint32_t rem = man & ((1u << shift) - 1u);
        uint32_t halfway = 1u << (shift - 1u);
        if (rem > halfway || (rem == halfway && (half & 1u))) half++;
        return (uint16_t)(sign | half);
    }
    uint32_t half = ((uint32_t)exp << 10) | (man >> 13);
    uint32_t rem = man & 0x1FFFu;
    if (rem > 0x1000u || (rem == 0x1000u && (half & 1u))) half++;
    return (uint16_t)(sign | half);
}

#if PHOTON_F16_NEON
// One instruction pair for 4 floats -> 4 halves.
static inline uint16x4_t f32x4ToF16x4(float32x4_t v) {
    return vreinterpret_u16_f16(vcvt_f16_f32(v));
}
#endif

#endif // PHOTONCAMERA_HALFCONVERT_H
