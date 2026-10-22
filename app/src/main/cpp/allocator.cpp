//
// Created by eszdman on 03.06.2025.
//

#include <jni.h>
#include <malloc.h>
#include <string.h>
#include <android/bitmap.h>
#include "android/log.h"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "Allocator", __VA_ARGS__)

long memoryCount = 0;

extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_allocate(JNIEnv *env, jclass clazz,
                                                            jint capacity) {
    // Allocate a direct ByteBuffer of the specified size
    jobject buffer = env->NewDirectByteBuffer(malloc(capacity), capacity);
    if (buffer == nullptr) {
        // Handle allocation failure
        LOGD("Failed to allocate buffer of size %d", capacity);
        return nullptr;
    }
    memoryCount += capacity;
    return buffer;
}

// Copies a rectangular sub-region of a 2D image row by row. Supports an
// arbitrary XY offset (unlike allocateAndCopy which only shifts a single
// contiguous run), so an in-place digital-zoom crop can be taken directly
// from a RAW buffer that has stride padding.
//
// cropWidthBytes  - number of bytes to copy per output row (cropWidth * bpp)
// cropHeight      - number of rows to copy
// originBuffer    - source ByteBuffer (full frame)
// row_stride      - source row stride in bytes
// offset          - byte offset to the crop's top-left corner
//
// Output is tightly packed: cropWidthBytes * cropHeight (no padding).
extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_allocateAndCopyCrop(JNIEnv *env, jclass clazz,
                                                                      jint cropWidthBytes,
                                                                      jint cropHeight,
                                                                      jobject originBuffer,
                                                                      jint row_stride,
                                                                      jint offset) {
    int output_size = cropWidthBytes * cropHeight;
    void* allocation = malloc(output_size);
    jobject buffer = env->NewDirectByteBuffer(allocation, output_size);
    if (buffer == nullptr) {
        LOGD("allocateAndCopyCrop: failed to allocate output");
        free(allocation);
        return nullptr;
    }
    void* ptr = env->GetDirectBufferAddress(originBuffer);
    if (ptr == nullptr) {
        LOGD("allocateAndCopyCrop: failed to get direct buffer address of originBuffer");
        free(allocation);
        return nullptr;
    }
    jlong cap = env->GetDirectBufferCapacity(originBuffer);
    if (offset < 0 || cropWidthBytes < 0 || cropHeight < 0 || row_stride <= 0 ||
        (int64_t)offset + (int64_t)(cropHeight - 1) * row_stride + cropWidthBytes > cap) {
        LOGD("allocateAndCopyCrop: bounds violation cap=%lld offset=%d row_stride=%d crop=%dx%d oob", (long long)cap, offset, row_stride, cropWidthBytes, cropHeight);
        free(allocation);
        return nullptr;
    }
    uint8_t* src = static_cast<uint8_t*>(ptr) + offset;
    uint8_t* dst = static_cast<uint8_t*>(allocation);
    for (int row = 0; row < cropHeight; row++) {
        memcpy(dst + (uint64_t)row * cropWidthBytes,
               src + (uint64_t)row * row_stride,
               cropWidthBytes);
    }
    memoryCount += output_size;
    LOGD("allocateAndCopyCrop: %dx%d, memory %ld MB",
         cropWidthBytes, cropHeight, (memoryCount / 1024) / 1024);
    return buffer;
}

extern "C"
#pragma clang diagnostic push
#pragma ide diagnostic ignored "MemoryLeak"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_allocateAndCopy(JNIEnv *env, jclass clazz,
                                                            jint capacity, jobject originBuffer, jint offset) {
    // Allocate a direct ByteBuffer of the specified size
    void* allocation = malloc(capacity);
    jobject buffer = env->NewDirectByteBuffer(allocation, capacity);
    if (buffer == nullptr) {
        // Handle allocation failure
        LOGD("Failed to allocate buffer of size %ld", capacity);
        if (allocation != nullptr) {
            free(allocation);
        }
        return nullptr;
    }
    void* ptr = env->GetDirectBufferAddress(originBuffer);
    if (ptr == nullptr) {
        LOGD("Failed to get direct buffer address of originBuffer, disabling copying");
        return buffer;
    } else {
        // Copy the contents of the original buffer to the new buffer
        memcpy(allocation, reinterpret_cast<uint8_t*>(ptr) + offset, capacity);
        LOGD("Buffer allocated and copied successfully");
    }
    memoryCount += capacity;
    LOGD("Current memory count: %ld MB", (memoryCount/1024)/1024);
    return buffer;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_allocateAndCopyConvert(JNIEnv *env, jclass clazz,
                                                                          jint capacity, jobject originBuffer,
                                                                          jint width, jint row_stride, jint offset) {
    // Calculate output buffer size (width * height * 2 bytes per pixel)
    int height = capacity / row_stride;
    int output_size = width * height * sizeof(uint16_t);

    // Allocate output buffer
    auto* allocation = static_cast<uint16_t *>(malloc(output_size));
    jobject buffer = env->NewDirectByteBuffer(allocation, output_size);

    if (buffer == nullptr) {
        LOGD("Failed to allocate buffer of size %d", output_size);
        free(allocation);
        return nullptr;
    }

    void* ptr = env->GetDirectBufferAddress(originBuffer);
    if (ptr == nullptr) {
        LOGD("Failed to get direct buffer address of originBuffer");
        free(allocation);
        return nullptr;
    }
    jlong cap = env->GetDirectBufferCapacity(originBuffer);
    if (offset < 0 || height <= 0 || (int64_t)offset + (int64_t)height * row_stride > cap) {
        LOGD("allocateAndCopyConvert: bounds violation cap=%lld offset=%d row_stride=%d h=%d", (long long)cap, offset, row_stride, height);
        free(allocation);
        return nullptr;
    }
    uint8_t* input = static_cast<uint8_t*>(ptr) + offset;
    uint16_t* output = allocation;

    // Calculate bytes per row of actual image data (without padding)
    int bytes_per_row = (width * 10) / 8;

    // Process each row
    for (int row = 0; row < height; row++) {
        uint8_t* row_start = input + (row * row_stride);

        // Process each group of 4 pixels (5 bytes) in the row
        for (int col = 0; col < bytes_per_row; col += 5) {
            // Ensure we don't read beyond the row
            if (col + 4 >= bytes_per_row) break;

            uint8_t b0 = row_start[col];
            uint8_t b1 = row_start[col + 1];
            uint8_t b2 = row_start[col + 2];
            uint8_t b3 = row_start[col + 3];
            uint8_t b4 = row_start[col + 4];

            // Convert and store as 16-bit values
            *output++ = (b0 << 2) | (b4 & 0x03);        // Pixel 0
            *output++ = (b1 << 2) | ((b4 >> 2) & 0x03); // Pixel 1
            *output++ = (b2 << 2) | ((b4 >> 4) & 0x03); // Pixel 2
            *output++ = (b3 << 2) | (b4 >> 6);          // Pixel 3
        }
    }

    LOGD("Buffer allocated and converted successfully with padding handling");
    memoryCount += output_size;
    LOGD("Current memory count: %ld MB", (memoryCount / 1024) / 1024);
    return buffer;
}

// Helper: decode one RAW10 row into dst (width uint16_t values)
static void decodeRaw10Row(const uint8_t* row_start, uint16_t* dst, int width) {
    int bytes_per_row = (width * 10) / 8;
    for (int col = 0, px = 0; col + 4 < bytes_per_row && px + 3 < width; col += 5, px += 4) {
        uint8_t b0 = row_start[col];
        uint8_t b1 = row_start[col + 1];
        uint8_t b2 = row_start[col + 2];
        uint8_t b3 = row_start[col + 3];
        uint8_t b4 = row_start[col + 4];
        dst[px]     = (uint16_t)((b0 << 2) | (b4 & 0x03));
        dst[px + 1] = (uint16_t)((b1 << 2) | ((b4 >> 2) & 0x03));
        dst[px + 2] = (uint16_t)((b2 << 2) | ((b4 >> 4) & 0x03));
        dst[px + 3] = (uint16_t)((b3 << 2) | (b4 >> 6));
    }
}

// Helper: Bayer-aware 2x2 binning matching dngCreator::applyBayerBinning.
// Each output pixel combines 4 same-colour pixels from a 4x4 input block.
// input is width*height uint16_t (packed, no stride padding).
static void applyBayerBinning(const uint16_t* input, uint16_t* output,
                               int srcWidth, int srcHeight,
                               int outWidth, int outHeight) {
    for (int oy = 0; oy < outHeight; oy++) {
        int blockStartRow = (oy / 2) * 4;
        int dr    = oy % 2;
        int inRow  = blockStartRow + dr;
        int inRow2 = (inRow + 2 < srcHeight) ? inRow + 2 : srcHeight - 1;

        for (int ox = 0; ox < outWidth; ox++) {
            int blockStartCol = (ox / 2) * 4;
            int dc    = ox % 2;
            int inCol  = blockStartCol + dc;
            int inCol2 = (inCol + 2 < srcWidth) ? inCol + 2 : srcWidth - 1;

            uint32_t sum =
                (uint32_t)input[inRow  * srcWidth + inCol ] +
                (uint32_t)input[inRow  * srcWidth + inCol2] +
                (uint32_t)input[inRow2 * srcWidth + inCol ] +
                (uint32_t)input[inRow2 * srcWidth + inCol2];

            output[oy * outWidth + ox] = (uint16_t)(sum > 65535u ? 65535u : sum);
        }
    }
}

// Converts RAW10 to uint16, then applies Bayer-aware 2x2 sum binning.
// Output size: (width/2) * (height/2) * sizeof(uint16_t)
extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_allocateAndCopyConvertBinning(JNIEnv *env, jclass clazz,
                                                                                  jint capacity, jobject originBuffer,
                                                                                  jint width, jint row_stride, jint offset) {
    int height = capacity / row_stride;
    int out_width  = width  / 2;
    int out_height = height / 2;
    int output_size = out_width * out_height * (int)sizeof(uint16_t);

    auto* allocation = static_cast<uint16_t*>(malloc(output_size));
    jobject buffer = env->NewDirectByteBuffer(allocation, output_size);
    if (buffer == nullptr) {
        LOGD("allocateAndCopyConvertBinning: failed to allocate output");
        free(allocation);
        return nullptr;
    }

    void* ptr = env->GetDirectBufferAddress(originBuffer);
    if (ptr == nullptr) {
        LOGD("allocateAndCopyConvertBinning: failed to get buffer address");
        free(allocation);
        return nullptr;
    }
    jlong cap2 = env->GetDirectBufferCapacity(originBuffer);
    if (offset < 0 || height <= 0 || (int64_t)offset + (int64_t)height * row_stride > cap2) {
        LOGD("allocateAndCopyConvertBinning: bounds violation cap=%lld offset=%d row_stride=%d h=%d", (long long)cap2, offset, row_stride, height);
        free(allocation);
        return nullptr;
    }
    // Decode entire RAW10 image into a packed uint16 buffer (no row padding)
    int full_size = width * height * (int)sizeof(uint16_t);
    auto* decoded = static_cast<uint16_t*>(malloc(full_size));
    if (decoded == nullptr) {
        LOGD("allocateAndCopyConvertBinning: failed to allocate decode buffer");
        free(allocation);
        return nullptr;
    }
    uint8_t* input = static_cast<uint8_t*>(ptr) + offset;
    for (int row = 0; row < height; row++) {
        decodeRaw10Row(input + row * row_stride, decoded + row * width, width);
    }

    applyBayerBinning(decoded, allocation, width, height, out_width, out_height);
    free(decoded);

    memoryCount += output_size;
    LOGD("allocateAndCopyConvertBinning: %dx%d -> %dx%d, memory %ld MB",
         width, height, out_width, out_height, (memoryCount / 1024) / 1024);
    return buffer;
}

// Applies Bayer-aware 2x2 sum binning on a cropped region of a RAW16 buffer.
// Equivalent to allocateAndCopyCrop followed by binning, but done in a single
// pass (only the cropped region is binned). offset points to the crop's
// top-left; cropWidth/cropHeight are the crop dimensions (full, un-binned).
// Output is (cropWidth/2)*(cropHeight/2)*sizeof(uint16_t).
extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_allocateAndCopyCropBinning(JNIEnv *env, jclass clazz,
                                                                              jint cropWidth, jint cropHeight,
                                                                              jobject originBuffer,
                                                                              jint row_stride, jint offset) {
    int out_width  = cropWidth  / 2;
    int out_height = cropHeight / 2;
    int output_size = out_width * out_height * (int)sizeof(uint16_t);
    if (out_width <= 0 || out_height <= 0) {
        LOGD("allocateAndCopyCropBinning: invalid crop %dx%d", cropWidth, cropHeight);
        return nullptr;
    }

    auto* allocation = static_cast<uint16_t*>(malloc(output_size));
    jobject buffer = env->NewDirectByteBuffer(allocation, output_size);
    if (buffer == nullptr) {
        LOGD("allocateAndCopyCropBinning: failed to allocate output");
        free(allocation);
        return nullptr;
    }

    void* ptr = env->GetDirectBufferAddress(originBuffer);
    if (ptr == nullptr) {
        LOGD("allocateAndCopyCropBinning: failed to get buffer address");
        free(allocation);
        return nullptr;
    }

    jlong cap = env->GetDirectBufferCapacity(originBuffer);
    if (offset < 0 || (int64_t)offset + (int64_t)(cropHeight - 1) * row_stride + cropWidth * sizeof(uint16_t) > cap) {
        LOGD("allocateAndCopyCropBinning: bounds violation cap=%lld offset=%d row_stride=%d crop=%dx%d", (long long)cap, offset, row_stride, cropWidth, cropHeight);
        free(allocation);
        return nullptr;
    }
    const uint8_t* src = static_cast<const uint8_t*>(ptr) + offset;
    auto* rowA = static_cast<uint16_t*>(malloc(cropWidth * sizeof(uint16_t)));
    auto* rowB = static_cast<uint16_t*>(malloc(cropWidth * sizeof(uint16_t)));
    if (rowA == nullptr || rowB == nullptr) {
        LOGD("allocateAndCopyCropBinning: failed to allocate row buffers");
        free(rowA);
        free(rowB);
        free(allocation);
        return nullptr;
    }
    for (int oy = 0; oy < out_height; oy++) {
        int blockStartRow = (oy / 2) * 4;
        int dr    = oy % 2;
        int inRow  = blockStartRow + dr;
        int inRow2 = (inRow + 2 < cropHeight) ? inRow + 2 : cropHeight - 1;
        memcpy(rowA, src + inRow  * row_stride, cropWidth * sizeof(uint16_t));
        memcpy(rowB, src + inRow2 * row_stride, cropWidth * sizeof(uint16_t));
        uint16_t* outRow = allocation + oy * out_width;
        for (int ox = 0; ox < out_width; ox++) {
            int blockStartCol = (ox / 2) * 4;
            int dc    = ox % 2;
            int inCol  = blockStartCol + dc;
            int inCol2 = (inCol + 2 < cropWidth) ? inCol + 2 : cropWidth - 1;

            uint32_t sum = (uint32_t)rowA[inCol] + (uint32_t)rowA[inCol2]
                         + (uint32_t)rowB[inCol] + (uint32_t)rowB[inCol2];
            outRow[ox] = (uint16_t)(sum > 65535u ? 65535u : sum);
        }
    }

    free(rowA);
    free(rowB);

    memoryCount += output_size;
    LOGD("allocateAndCopyCropBinning: %dx%d -> %dx%d, memory %ld MB",
         cropWidth, cropHeight, out_width, out_height, (memoryCount / 1024) / 1024);
    return buffer;
}

// Applies Bayer-aware 2x2 sum binning on a RAW16 (uint16_t) buffer.
// row_stride is in bytes; output is (width/2)*(height/2)*sizeof(uint16_t)
extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_allocateAndCopyBinning(JNIEnv *env, jclass clazz,
                                                                           jint capacity, jobject originBuffer,
                                                                           jint width, jint height, jint row_stride) {
    int out_width  = width  / 2;
    int out_height = height / 2;
    int output_size = out_width * out_height * (int)sizeof(uint16_t);

    auto* allocation = static_cast<uint16_t*>(malloc(output_size));
    jobject buffer = env->NewDirectByteBuffer(allocation, output_size);
    if (buffer == nullptr) {
        LOGD("allocateAndCopyBinning: failed to allocate output");
        free(allocation);
        return nullptr;
    }

    void* ptr = env->GetDirectBufferAddress(originBuffer);
    if (ptr == nullptr) {
        LOGD("allocateAndCopyBinning: failed to get buffer address");
        free(allocation);
        return nullptr;
    }

    // If row_stride matches width (no padding), bin directly
    int stride_pixels = row_stride / (int)sizeof(uint16_t);
    if (stride_pixels == width) {
        applyBayerBinning(static_cast<const uint16_t*>(ptr), allocation,
                          width, height, out_width, out_height);
    } else {
        // De-stride into a packed buffer first
        int full_size = width * height * (int)sizeof(uint16_t);
        auto* packed = static_cast<uint16_t*>(malloc(full_size));
        if (packed == nullptr) {
            LOGD("allocateAndCopyBinning: failed to allocate pack buffer");
            free(allocation);
            return nullptr;
        }
        const uint8_t* src = static_cast<const uint8_t*>(ptr);
        for (int row = 0; row < height; row++) {
            memcpy(packed + row * width, src + row * row_stride, width * sizeof(uint16_t));
        }
        applyBayerBinning(packed, allocation, width, height, out_width, out_height);
        free(packed);
    }

    memoryCount += output_size;
    LOGD("allocateAndCopyBinning: %dx%d -> %dx%d, memory %ld MB",
         width, height, out_width, out_height, (memoryCount / 1024) / 1024);
    return buffer;
}

#pragma clang diagnostic pop

extern "C"
JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_free(JNIEnv *env, jclass clazz,
                                                            jobject buffer) {
    if (buffer == nullptr) {
        LOGD("Buffer is null, nothing to free");
        return;
    }

    // Get the address of the allocated memory
    void* ptr = env->GetDirectBufferAddress(buffer);
    long capacity = env->GetDirectBufferCapacity(buffer);
    if (ptr == nullptr) {
        LOGD("Failed to get direct buffer address");
        return;
    }

    // Free the allocated memory
    free(ptr);
    memoryCount -= capacity;
    LOGD("Buffer freed successfully");
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_getMemoryCount(JNIEnv *env, jclass clazz) {
    // Return the current memory count
    LOGD("Current memory count: %ld MB", (memoryCount/1024)/1024);
    return memoryCount;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_wrapBitmap(JNIEnv *env, jclass clazz,
                                                             jobject bitmap) {
    // Wrap a software ARGB_8888 bitmap's pixel memory in a direct ByteBuffer
    // so GL readback can target it without any intermediate full-frame buffer.
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGD("wrapBitmap: AndroidBitmap_getInfo failed");
        return nullptr;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGD("wrapBitmap: unsupported format %d", info.format);
        return nullptr;
    }
    if (info.stride != (uint32_t)(info.width * 4)) {
        LOGD("wrapBitmap: unsupported stride %u for width %u", info.stride, info.width);
        return nullptr;
    }
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        pixels == nullptr) {
        LOGD("wrapBitmap: lockPixels failed");
        return nullptr;
    }
    return env->NewDirectByteBuffer(pixels, (jlong) info.stride * info.height);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_unlockBitmap(JNIEnv *env, jclass clazz,
                                                               jobject bitmap) {
    return AndroidBitmap_unlockPixels(env, bitmap) == ANDROID_BITMAP_RESULT_SUCCESS;
}

// ---------------------------------------------------------------------------
// Packed burst staging: sensor samples are <= whiteLevel, so they fit in
// ceil(log2(whiteLevel+1)) bits. packBits copies a tightly-packed 16-bit
// frame into a new native buffer holding the low `bits` of every sample as an
// LSB-first bitstream; unpack16 restores little-endian shorts. The pair is an
// exact inverse for samples < 2^bits (the only ones a sensor can emit below
// whiteLevel).
// ---------------------------------------------------------------------------

static inline uint32_t maxSampleForBits(int bits) {
    return bits >= 32 ? 0xFFFFFFFFu : ((1u << bits) - 1u);
}

// Bitstream decode helpers (defined below, used by packBits' verify block).
static void unpackScalar(const uint8_t *packed, int64_t startByte, uint16_t *out,
                         int start, int pixels, int bits, uint32_t mask);
// Non-static: also used by RawF16's fused unpack+normalize (rawF16.cpp).
void unpackFast10(const uint8_t *packed, uint16_t *out, int pixels);

extern "C"
JNIEXPORT jobject JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_packBits(JNIEnv *env, jclass clazz,
                                                            jobject srcBuffer, jint pixels,
                                                            jint bits, jboolean verify) {
    if (srcBuffer == nullptr || pixels <= 0 || bits <= 0 || bits >= 16) {
        LOGD("packBits: invalid args pixels=%d bits=%d", pixels, bits);
        return nullptr;
    }
    const uint8_t *src = static_cast<const uint8_t *>(env->GetDirectBufferAddress(srcBuffer));
    jlong srcCap = env->GetDirectBufferCapacity(srcBuffer);
    if (src == nullptr || srcCap < (jlong) pixels * 2) {
        LOGD("packBits: bad source capacity=%lld", (long long) srcCap);
        return nullptr;
    }
    int64_t packedSize = ((int64_t) pixels * bits + 7) / 8;
    uint8_t *dst = static_cast<uint8_t *>(malloc(packedSize));
    if (dst == nullptr) {
        LOGD("packBits: allocation of %lld failed", (long long) packedSize);
        return nullptr;
    }
    memset(dst, 0, packedSize);
    const uint16_t *in = reinterpret_cast<const uint16_t *>(src);
    const uint32_t mask = maxSampleForBits(bits);
    int64_t bitPos = 0;
    bool overflow = false;
    int i = 0;
    if (bits == 10) {
        // Fast path: straight-line 4-sample/5-byte groups. Bit-identical to
        // the scalar loop below (same 40-bit little-endian groups, disjoint
        // writes onto the zeroed buffer); any sample above the mask fails
        // the same way.
        uint8_t *dp = dst;
        int n4 = pixels & ~3;
        for (; i < n4; i += 4) {
            uint32_t s0 = in[i], s1 = in[i + 1], s2 = in[i + 2], s3 = in[i + 3];
            if ((s0 | s1 | s2 | s3) > 0x3FFu) overflow = true;
            uint32_t lo = (s0 & 0x3FFu) | ((s1 & 0x3FFu) << 10)
                        | ((s2 & 0x3FFu) << 20) | ((s3 & 0x3u) << 30);
            dp[0] = (uint8_t) lo;
            dp[1] = (uint8_t) (lo >> 8);
            dp[2] = (uint8_t) (lo >> 16);
            dp[3] = (uint8_t) (lo >> 24);
            dp[4] = (uint8_t) ((s3 & 0x3FFu) >> 2);
            dp += 5;
        }
        bitPos = (int64_t) n4 * bits;
    }
    for (; i < pixels; i++) {
        uint32_t raw = (uint32_t) in[i];
        if (raw > mask) {
            // The caller derived `bits` from whiteLevel; a sample above the
            // mask means the assumption is wrong, so refuse rather than
            // truncate. The caller keeps the original 16-bit buffer.
            overflow = true;
        }
        uint32_t v = raw & mask;
        int64_t bytePos = bitPos >> 3;
        int shift = (int) (bitPos & 7);
        uint32_t word = v << shift;
        int nbytes = (shift + bits + 7) >> 3;
        for (int b = 0; b < nbytes; b++) {
            dst[bytePos + b] |= (uint8_t) (word >> (8 * b));
        }
        bitPos += bits;
    }
    if (overflow) {
        LOGD("packBits: sample above %d-bit range, keeping 16-bit buffer", bits);
        free(dst);
        return nullptr;
    }
    if (verify) {
        uint16_t *check = static_cast<uint16_t *>(malloc((size_t) pixels * 2));
        if (check == nullptr) {
            free(dst);
            return nullptr;
        }
        unpackScalar(dst, 0, check, 0, pixels, bits, mask);
        jboolean ok = memcmp(check, src, (size_t) pixels * 2) == 0 ? JNI_TRUE : JNI_FALSE;
        free(check);
        if (ok && bits == 10) {
            // Cross-check the 10-bit fast decode against the same source
            // (DEBUG only): proves the production unpack path is
            // bit-identical to the reference scalar loop on real frame data.
            uint16_t *checkFast = static_cast<uint16_t *>(malloc((size_t) pixels * 2));
            if (checkFast == nullptr) {
                free(dst);
                return nullptr;
            }
            unpackFast10(dst, checkFast, pixels);
            if (memcmp(checkFast, src, (size_t) pixels * 2) != 0) {
                LOGD("packBits: fast10 unpack mismatch pixels=%d", pixels);
                ok = JNI_FALSE;
            }
            free(checkFast);
        }
        if (!ok) {
            LOGD("packBits: verification FAILED pixels=%d bits=%d", pixels, bits);
            free(dst);
            return nullptr;
        }
    }
    jobject buffer = env->NewDirectByteBuffer(dst, (jlong) packedSize);
    if (buffer == nullptr) {
        free(dst);
        return nullptr;
    }
    memoryCount += packedSize;
    LOGD("packBits: %d px %d-bit -> %lld bytes, memory %ld MB",
         pixels, bits, (long long) packedSize, (memoryCount / 1024) / 1024);
    return buffer;
}

// Scalar bitstream decode (reference implementation; also the tail handler
// and the fallback for bit depths without a fast path).
static void unpackScalar(const uint8_t *packed, int64_t startByte, uint16_t *out,
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

// 10-bit fast path: straight-line 5-byte/4-sample groups, then scalar tail.
// Pure integer regrouping of unpackScalar — bit-identical output. Non-static:
// RawF16's fused unpack+normalize reuses it per chunk (chunk starts are
// multiples of 4 samples, so the 5-byte grouping stays aligned).
void unpackFast10(const uint8_t *packed, uint16_t *out, int pixels) {
    int n4 = pixels & ~3;
    for (int i = 0, j = 0; i < n4; i += 4, j += 5) {
        uint32_t w;
        memcpy(&w, packed + j, sizeof(w));
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

extern "C"
JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_unpack16(JNIEnv *env, jclass clazz,
                                                            jobject dstBuffer, jobject packedBuffer,
                                                            jint pixels, jint bits) {
    if (dstBuffer == nullptr || packedBuffer == nullptr || pixels <= 0 || bits <= 0 || bits > 16) {
        LOGD("unpack16: invalid args pixels=%d bits=%d", pixels, bits);
        return;
    }
    uint8_t *dst = static_cast<uint8_t *>(env->GetDirectBufferAddress(dstBuffer));
    jlong dstCap = env->GetDirectBufferCapacity(dstBuffer);
    const uint8_t *packed =
            static_cast<const uint8_t *>(env->GetDirectBufferAddress(packedBuffer));
    jlong packedCap = env->GetDirectBufferCapacity(packedBuffer);
    int64_t needPacked = ((int64_t) pixels * bits + 7) / 8;
    if (dst == nullptr || packed == nullptr || dstCap < (jlong) pixels * 2
        || packedCap < needPacked) {
        LOGD("unpack16: buffer too small dst=%lld packed=%lld need=%lld",
             (long long) dstCap, (long long) packedCap, (long long) needPacked);
        return;
    }
    uint16_t *out = reinterpret_cast<uint16_t *>(dst);
    const uint32_t mask = maxSampleForBits(bits);
    if (bits == 10) {
        unpackFast10(packed, out, pixels);
    } else {
        unpackScalar(packed, 0, out, 0, pixels, bits, mask);
    }
}
// Converts one tile of tightly packed RGBA8888 into 8-bit YUV420 full-range
// BT.709, writing the Y/U/V plane buffers of an encoder input Image. Strides
// are in bytes; edge tiles replicate the last valid row/column.
extern "C"
JNIEXPORT void JNICALL
Java_com_particlesdevs_photoncamera_util_Allocator_rgbaToYuv420Tile(
        JNIEnv *env, jclass clazz, jobject srcBuffer,
        jint fullWidth, jint fullHeight,
        jint tileX, jint tileY, jint tileWidth, jint tileHeight,
        jobject yBuffer, jint yRowStride, jint yPixelStride,
        jobject uBuffer, jint uvRowStride, jint uPixelStride,
        jobject vBuffer, jint vRowStride, jint vPixelStride) {
    const uint8_t *src = static_cast<const uint8_t *>(env->GetDirectBufferAddress(srcBuffer));
    uint8_t *yPlane = static_cast<uint8_t *>(env->GetDirectBufferAddress(yBuffer));
    uint8_t *uPlane = static_cast<uint8_t *>(env->GetDirectBufferAddress(uBuffer));
    uint8_t *vPlane = static_cast<uint8_t *>(env->GetDirectBufferAddress(vBuffer));
    if (src == nullptr || yPlane == nullptr || uPlane == nullptr || vPlane == nullptr) {
        LOGD("rgbaToYuv420Tile: null buffer");
        return;
    }
    jlong srcCap = env->GetDirectBufferCapacity(srcBuffer);
    if (fullWidth <= 0 || fullHeight <= 0 || tileWidth <= 0 || tileHeight <= 0
        || srcCap < (jlong) fullWidth * fullHeight * 4) {
        LOGD("rgbaToYuv420Tile: bad size %dx%d tile %dx%d cap=%lld",
             fullWidth, fullHeight, tileWidth, tileHeight, (long long) srcCap);
        return;
    }
    auto clampCoord = [](int v, int max) { return v < max ? v : max - 1; };
    auto clamp8 = [](int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); };

    for (int r = 0; r < tileHeight; r++) {
        const int sy = clampCoord(tileY + r, fullHeight);
        const uint8_t *row = src + (size_t) sy * fullWidth * 4;
        uint8_t *yRow = yPlane + (size_t) r * yRowStride;
        for (int c = 0; c < tileWidth; c++) {
            const int sx = clampCoord(tileX + c, fullWidth);
            const uint8_t *p = row + (size_t) sx * 4;
            const int R = p[0], G = p[1], B = p[2];
            const int Y = (54 * R + 183 * G + 18 * B + 128) >> 8;
            yRow[(size_t) c * yPixelStride] = (uint8_t) clamp8(Y);
        }
    }

    for (int r = 0; r < tileHeight; r += 2) {
        const int sy0 = clampCoord(tileY + r, fullHeight);
        const int sy1 = clampCoord(tileY + r + 1, fullHeight);
        const uint8_t *row0 = src + (size_t) sy0 * fullWidth * 4;
        const uint8_t *row1 = src + (size_t) sy1 * fullWidth * 4;
        const int cy = r / 2;
        for (int c = 0; c < tileWidth; c += 2) {
            const int sx0 = clampCoord(tileX + c, fullWidth);
            const int sx1 = clampCoord(tileX + c + 1, fullWidth);
            const int R = (row0[sx0 * 4] + row0[sx1 * 4]
                           + row1[sx0 * 4] + row1[sx1 * 4] + 2) >> 2;
            const int G = (row0[sx0 * 4 + 1] + row0[sx1 * 4 + 1]
                           + row1[sx0 * 4 + 1] + row1[sx1 * 4 + 1] + 2) >> 2;
            const int B = (row0[sx0 * 4 + 2] + row0[sx1 * 4 + 2]
                           + row1[sx0 * 4 + 2] + row1[sx1 * 4 + 2] + 2) >> 2;
            const int Cb = 128 + ((-29 * R - 99 * G + 128 * B + 128) >> 8);
            const int Cr = 128 + ((128 * R - 116 * G - 12 * B + 128) >> 8);
            const int cc = c / 2;
            uPlane[(size_t) cy * uvRowStride + (size_t) cc * uPixelStride] =
                    (uint8_t) clamp8(Cb);
            vPlane[(size_t) cy * vRowStride + (size_t) cc * vPixelStride] =
                    (uint8_t) clamp8(Cr);
        }
    }
}
