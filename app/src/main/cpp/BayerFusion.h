#ifndef BAYER_FUSION_H
#define BAYER_FUSION_H

#include <vector>
#include <cstdint>
#include <memory>

/**
 * High-performance RAW Bayer Fusion Engine.
 * Aligns and merges 15 RAW10 frames into a high-dynamic-range F32 or U16 buffer.
 */
class BayerFusion {
public:
    struct Config {
        int width;
        int height;
        int burstSize = 15;
        int tileSize = 32;
        int searchRange = 8;
    };

    BayerFusion(const Config& config);
    ~BayerFusion();

    /**
     * Processes the burst of RAW10 frames.
     * @param burstData Pointer to the concatenated RAW10 frames.
     * @param outBuffer Output buffer (allocated by caller, size width*height*sizeof(float)).
     */
    void process(const uint8_t* burstData, float* outBuffer);

private:
    Config cfg;

    // Internal alignment using Block Matching (simplified for mobile perf)
    struct Alignment {
        int dx;
        int dy;
    };

    void alignAndAccumulate(const uint16_t* reference, const uint16_t* target, float* accumulator);
    Alignment estimateBlockMotion(const uint16_t* ref, const uint16_t* tgt, int tx, int ty);

    // Fast RAW10 to RAW16 unpacking
    void unpackRAW10(const uint8_t* in, uint16_t* out, int count);
};

#endif
