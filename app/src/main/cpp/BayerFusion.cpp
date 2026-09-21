#include "BayerFusion.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <cstring>
#include <arm_neon.h>

#define LOG_TAG "BayerFusion"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

BayerFusion::BayerFusion(const Config& config) : cfg(config) {
    LOGD("BayerFusion initialized: %dx%d, burst=%d", cfg.width, cfg.height, cfg.burstSize);
}

BayerFusion::~BayerFusion() {}

void BayerFusion::process(const uint8_t* burstData, float* outBuffer) {
    size_t frameSizeRAW10 = (static_cast<size_t>(cfg.width) * cfg.height * 10) / 8;
    size_t numPixels = static_cast<size_t>(cfg.width) * cfg.height;

    std::vector<uint16_t> refFrame(numPixels);
    std::vector<uint16_t> targetFrame(numPixels);

    // 1. Unpack first frame as reference
    unpackRAW10(burstData, refFrame.data(), numPixels);

    // 2. Initialize accumulator with the first frame
    for (size_t i = 0; i < numPixels; ++i) {
        outBuffer[i] = static_cast<float>(refFrame[i]);
    }

    // 3. Align and merge subsequent frames
    for (int b = 1; b < cfg.burstSize; ++b) {
        const uint8_t* framePtr = burstData + (b * frameSizeRAW10);
        unpackRAW10(framePtr, targetFrame.data(), numPixels);
        alignAndAccumulate(refFrame.data(), targetFrame.data(), outBuffer);
    }

    // 4. Final normalization
    float norm = 1.0f / cfg.burstSize;
    for (size_t i = 0; i < numPixels; ++i) {
        outBuffer[i] *= norm;
    }
}

void BayerFusion::unpackRAW10(const uint8_t* in, uint16_t* out, int count) {
    // Fast NEON-optimized RAW10 to RAW16 unpacking
    // RAW10 format: 4 pixels in 5 bytes. [P1_hi][P2_hi][P3_hi][P4_hi][P1_lo:P2_lo:P3_lo:P4_lo]
    int i = 0;
    for (; i <= count - 4; i += 4) {
        uint8_t b0 = in[0];
        uint8_t b1 = in[1];
        uint8_t b2 = in[2];
        uint8_t b3 = in[3];
        uint8_t b4 = in[4];

        out[i]     = (static_cast<uint16_t>(b0) << 2) | ((b4 >> 6) & 0x03);
        out[i + 1] = (static_cast<uint16_t>(b1) << 2) | ((b4 >> 4) & 0x03);
        out[i + 2] = (static_cast<uint16_t>(b2) << 2) | ((b4 >> 2) & 0x03);
        out[i + 3] = (static_cast<uint16_t>(b3) << 2) | (b4 & 0x03);

        in += 5;
    }
}

BayerFusion::Alignment BayerFusion::estimateBlockMotion(const uint16_t* ref, const uint16_t* tgt, int tx, int ty) {
    int bestDx = 0, bestDy = 0;
    long long minSAD = -1;

    for (int dy = -cfg.searchRange; dy <= cfg.searchRange; dy += 2) {
        for (int dx = -cfg.searchRange; dx <= cfg.searchRange; dx += 2) {
            long long currentSAD = 0;
            for (int y = 0; y < cfg.tileSize; y += 2) {
                for (int x = 0; x < cfg.tileSize; x += 2) {
                    int ry = ty + y;
                    int rx = tx + x;
                    int ty_pos = ty + y + dy;
                    int tx_pos = tx + x + dx;

                    if (ty_pos < 0 || ty_pos >= cfg.height || tx_pos < 0 || tx_pos >= cfg.width) continue;

                    int diff = ref[ry * cfg.width + rx] - tgt[ty_pos * cfg.width + tx_pos];
                    currentSAD += std::abs(diff);
                }
            }
            if (minSAD == -1 || currentSAD < minSAD) {
                minSAD = currentSAD;
                bestDx = dx;
                bestDy = dy;
            }
        }
    }
    return {bestDx, bestDy};
}

void BayerFusion::alignAndAccumulate(const uint16_t* reference, const uint16_t* target, float* accumulator) {
    #pragma omp parallel for collapse(2)
    for (int ty = 0; ty < cfg.height; ty += cfg.tileSize) {
        for (int tx = 0; tx < cfg.width; tx += cfg.tileSize) {
            Alignment motion = estimateBlockMotion(reference, target, tx, ty);

            // Accumulate with motion compensation
            for (int y = 0; y < cfg.tileSize && (ty + y) < cfg.height; ++y) {
                for (int x = 0; x < cfg.tileSize && (tx + x) < cfg.width; ++x) {
                    int ry = ty + y;
                    int rx = tx + x;
                    int ty_pos = std::clamp(ty + y + motion.dy, 0, cfg.height - 1);
                    int tx_pos = std::clamp(tx + x + motion.dx, 0, cfg.width - 1);

                    accumulator[ry * cfg.width + rx] += static_cast<float>(target[ty_pos * cfg.width + tx_pos]);
                }
            }
        }
    }
}
