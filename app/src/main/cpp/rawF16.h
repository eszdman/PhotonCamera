//
// Created by eszdman on 15.09.2026.
//

#ifndef PHOTONCAMERA_RAWF16_H
#define PHOTONCAMERA_RAWF16_H

#include <cstdint>

/**
 * Converts packed uint16 raw frames into white/black-level-normalized fp16
 * (half float) buffers for direct upload into FLOAT_16 GL textures:
 *
 *   out[site] = clamp((v - blackLevel[site]) / (whiteLevel - blackLevel[site]), 0, 1)
 *
 * blackLevel is indexed by the sensor site order (x&1) + (y&1)*2, matching
 * Parameters.blackLevel. The row loop is NEON-vectorized (8 pixels/iteration)
 * with a portable scalar fallback for ABIs without half-float NEON support.
 *
 * The existing allocator.cpp allocation/copy path is untouched: this class
 * creates a NEW fp16 buffer on demand; the caller owns (and frees) it.
 */
class RawF16 {
public:
    /**
     * Normalizes a width*height uint16 raw frame into a freshly malloc'd
     * width*height half-float buffer (2 bytes per sample, same total size).
     * Returns nullptr on allocation failure or bad arguments.
     */
    static uint16_t *normalize(const uint16_t *src, int width, int height,
                               float whiteLevel, const float blackLevel[4]);

    /**
     * Inverse of normalize(): re-encodes a width*height half-float buffer
     * back into raw uint16 counts, u = round(clamp(f,0,1)*(wl-bl[site])+bl[site]),
     * into a freshly malloc'd width*height uint16 buffer. Used by the DNG
     * save path, which requires uint16 samples. Returns nullptr on failure.
     */
    static uint16_t *encodeU16(const uint16_t *src, int width, int height,
                               float whiteLevel, const float blackLevel[4]);
};

#endif //PHOTONCAMERA_RAWF16_H
