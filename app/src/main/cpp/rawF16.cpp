//
// Created by eszdman on 15.09.2026.
//

#include "rawF16.h"
#include <jni.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include "android/log.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "RawF16", __VA_ARGS__)

// Tracks the malloc'd bytes handed out through this file; declared in
// allocator.cpp so Allocator.getMemoryCount() stays a single total.
extern long memoryCount;

// NEON f16<->f32 conversions: always present on arm64; on armv7 they need the
// FP16 extension. Everything else falls back to the portable scalar path.
#if defined(__aarch64__)
#define RAWF16_NEON 1
#elif defined(__ARM_NEON) && defined(__ARM_NEON_FP) && (__ARM_NEON_FP & 2)
#define RAWF16_NEON 1
#endif

#if RAWF16_NEON
#include <arm_neon.h>
#endif

// Portable float -> half conversion with round-to-nearest-even. Values fed in
// are already clamped to [0,1], so the subnormal/Inf branches are only safety
// nets, but they keep the helper correct for arbitrary inputs.
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

static inline uint16_t normalizeSample(uint16_t v, float bl, float invScale) {
    float f = ((float)v - bl) * invScale;
    if (f < 0.0f) f = 0.0f;
    if (f > 1.0f) f = 1.0f;
    return f32ToF16(f);
}

uint16_t *RawF16::normalize(const uint16_t *src, int width, int height,
                            float whiteLevel, const float blackLevel[4]) {
    if (src == nullptr || width <= 0 || height <= 0) return nullptr;
    size_t count = (size_t)width * (size_t)height;
    auto *dst = static_cast<uint16_t *>(malloc(count * sizeof(uint16_t)));
    if (dst == nullptr) {
        LOGD("normalize: failed to allocate %u half samples", (unsigned)count);
        return nullptr;
    }

    // Per-site inverse scale; guard against broken metadata (wl <= bl).
    float invScale[4];
    for (int c = 0; c < 4; c++) {
        float range = whiteLevel - blackLevel[c];
        invScale[c] = 1.0f / (range > 1.0f ? range : 1.0f);
    }

    for (int row = 0; row < height; row++) {
        const int siteRow = (row & 1) * 2;
        const uint16_t *srcRow = src + (size_t)row * width;
        uint16_t *dstRow = dst + (size_t)row * width;
#if RAWF16_NEON
        // Sites alternate (bl0, bl1) on even rows and (bl2, bl3) on odd rows;
        // a 4-lane {a,b,a,b} vector covers 4 consecutive pixels.
        const float blPat[4] = {blackLevel[siteRow], blackLevel[siteRow + 1],
                                blackLevel[siteRow], blackLevel[siteRow + 1]};
        const float invPat[4] = {invScale[siteRow], invScale[siteRow + 1],
                                 invScale[siteRow], invScale[siteRow + 1]};
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
    return dst;
}

// Portable half -> float decode (mirror of f32ToF16) for the scalar path.
static inline float f16ToF32(uint16_t h) {
    uint32_t sign = (h >> 15) & 1u;
    uint32_t exp = (h >> 10) & 0x1Fu;
    uint32_t man = h & 0x3FFu;
    float val;
    if (exp == 0) {
        val = (float)man * 5.9604644775390625e-08f; // man * 2^-24
    } else if (exp == 31) {
        val = man ? NAN : (float)INFINITY;
    } else {
        val = (float)((1u << 10) | man);
        for (int e = exp - 25; e > 0; e--) val *= 2.0f;
        for (int e = exp - 25; e < 0; e++) val *= 0.5f;
    }
    return sign ? -val : val;
}

static inline uint16_t encodeSample(uint16_t h, float bl, float wl, float scale) {
    float f = f16ToF32(h);
    if (f < 0.0f || f != f) f = 0.0f; // clamp negatives and NaN
    if (f > 1.0f) f = 1.0f;
    float v = f * scale + bl;
    if (v > wl) v = wl;
    if (v < 0.0f) v = 0.0f;
    return (uint16_t)(v + 0.5f); // round-to-nearest; no LSB dither needed at wl scale
}

uint16_t *RawF16::encodeU16(const uint16_t *src, int width, int height,
                            float whiteLevel, const float blackLevel[4]) {
    if (src == nullptr || width <= 0 || height <= 0) return nullptr;
    size_t count = (size_t)width * (size_t)height;
    auto *dst = static_cast<uint16_t *>(malloc(count * sizeof(uint16_t)));
    if (dst == nullptr) {
        LOGD("encodeU16: failed to allocate %u samples", (unsigned)count);
        return nullptr;
    }
    float scale[4];
    for (int c = 0; c < 4; c++) scale[c] = whiteLevel - blackLevel[c];

    for (int row = 0; row < height; row++) {
        const int siteRow = (row & 1) * 2;
        const uint16_t *srcRow = src + (size_t)row * width;
        uint16_t *dstRow = dst + (size_t)row * width;
#if RAWF16_NEON
        const float blPat[4] = {blackLevel[siteRow], blackLevel[siteRow + 1],
                                blackLevel[siteRow], blackLevel[siteRow + 1]};
        const float scPat[4] = {scale[siteRow], scale[siteRow + 1],
                                scale[siteRow], scale[siteRow + 1]};
        const float32x4_t blV = vld1q_f32(blPat);
        const float32x4_t scV = vld1q_f32(scPat);
        const float32x4_t wlV = vdupq_n_f32(whiteLevel);
        const float32x4_t zeroV = vdupq_n_f32(0.0f);
        const float32x4_t halfV = vdupq_n_f32(0.5f);
        int x = 0;
        for (; x + 8 <= width; x += 8) {
            float16x8_t h = vld1q_f16(reinterpret_cast<const float16_t *>(srcRow + x));
            float32x4_t lo = vcvt_f32_f16(vget_low_f16(h));
            float32x4_t hi = vcvt_f32_f16(vget_high_f16(h));
            // vmlaq: v = f*scale + bl; clamp into [0, wl]; round via +0.5 trunc
            lo = vminq_f32(vmaxq_f32(vmlaq_f32(blV, scV, lo), zeroV), wlV);
            hi = vminq_f32(vmaxq_f32(vmlaq_f32(blV, scV, hi), zeroV), wlV);
            uint16x4_t lo16 = vmovn_u32(vcvtq_u32_f32(vaddq_f32(lo, halfV)));
            uint16x4_t hi16 = vmovn_u32(vcvtq_u32_f32(vaddq_f32(hi, halfV)));
            vst1_u16(dstRow + x, lo16);
            vst1_u16(dstRow + x + 4, hi16);
        }
        for (; x < width; x++) {
            int site = siteRow + (x & 1);
            dstRow[x] = encodeSample(srcRow[x], blackLevel[site], whiteLevel, scale[site]);
        }
#else
        for (int x = 0; x < width; x++) {
            int site = siteRow + (x & 1);
            dstRow[x] = encodeSample(srcRow[x], blackLevel[site], whiteLevel, scale[site]);
        }
#endif
    }
    return dst;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_createF16(JNIEnv *env, jclass clazz,
                                                             jobject originBuffer, jint width,
                                                             jint height, jint whiteLevel,
                                                             jfloatArray blackLevel) {
    const uint16_t *src = static_cast<const uint16_t *>(env->GetDirectBufferAddress(originBuffer));
    if (src == nullptr) {
        LOGD("createF16: origin buffer is not direct");
        return nullptr;
    }
    float bl[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    if (blackLevel != nullptr) {
        jsize len = env->GetArrayLength(blackLevel);
        if (len > 4) len = 4;
        if (len > 0) env->GetFloatArrayRegion(blackLevel, 0, len, bl);
    }
    // Bound the conversion by the buffer actually held: RAW10/RAW16 frames are
    // exactly width*height uint16 samples, but stay defensive against callers
    // passing a stride-padded size.
    jlong capacityBytes = env->GetDirectBufferCapacity(originBuffer);
    int rows = height;
    if (capacityBytes >= 0) {
        int maxRows = (int)(capacityBytes / 2) / (width > 0 ? width : 1);
        if (rows > maxRows) rows = maxRows;
    }
    uint16_t *out = RawF16::normalize(src, width, rows, (float)whiteLevel, bl);
    if (out == nullptr) return nullptr;
    int outputSize = width * rows * (int)sizeof(uint16_t);
    jobject buffer = env->NewDirectByteBuffer(out, outputSize);
    if (buffer == nullptr) {
        LOGD("createF16: failed to wrap %d bytes", outputSize);
        free(out);
        return nullptr;
    }
    memoryCount += outputSize;
    LOGD("createF16: %dx%d wl=%d bl=%.1f/%.1f/%.1f/%.1f, memory %ld MB",
         width, rows, whiteLevel, bl[0], bl[1], bl[2], bl[3], (memoryCount / 1024) / 1024);
    return buffer;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_createU16FromF16(JNIEnv *env, jclass clazz,
                                                                   jobject originBuffer, jint width,
                                                                   jint height, jint whiteLevel,
                                                                   jfloatArray blackLevel) {
    const uint16_t *src = static_cast<const uint16_t *>(env->GetDirectBufferAddress(originBuffer));
    if (src == nullptr) {
        LOGD("createU16FromF16: origin buffer is not direct");
        return nullptr;
    }
    float bl[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    if (blackLevel != nullptr) {
        jsize len = env->GetArrayLength(blackLevel);
        if (len > 4) len = 4;
        if (len > 0) env->GetFloatArrayRegion(blackLevel, 0, len, bl);
    }
    jlong capacityBytes = env->GetDirectBufferCapacity(originBuffer);
    int rows = height;
    if (capacityBytes >= 0) {
        int maxRows = (int)(capacityBytes / 2) / (width > 0 ? width : 1);
        if (rows > maxRows) rows = maxRows;
    }
    uint16_t *out = RawF16::encodeU16(src, width, rows, (float)whiteLevel, bl);
    if (out == nullptr) return nullptr;
    int outputSize = width * rows * (int)sizeof(uint16_t);
    jobject buffer = env->NewDirectByteBuffer(out, outputSize);
    if (buffer == nullptr) {
        LOGD("createU16FromF16: failed to wrap %d bytes", outputSize);
        free(out);
        return nullptr;
    }
    memoryCount += outputSize;
    LOGD("createU16FromF16: %dx%d wl=%d, memory %ld MB",
         width, rows, whiteLevel, (memoryCount / 1024) / 1024);
    return buffer;
}
