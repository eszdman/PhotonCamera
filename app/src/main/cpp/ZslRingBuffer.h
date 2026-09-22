#ifndef KOSHCAM_ZSL_RING_BUFFER_H
#define KOSHCAM_ZSL_RING_BUFFER_H

#include <vector>
#include <cstdint>
#include <mutex>
#include <cstddef>

namespace koshcam {

struct RawFrame {
    std::vector<uint8_t> data;
    int width;
    int height;
    int64_t timestampNs;
    int frameIndex;
};

class ZslRingBuffer {
public:
    static constexpr size_t BUFFER_CAPACITY = 10;

    ZslRingBuffer();
    ~ZslRingBuffer();

    void pushFrame(const uint8_t* rawData, size_t size, int width, int height, int64_t timestampNs);

    /**
     * Retrieves frame offset by -200ms (200,000,000 ns) from shutter trigger timestamp
     * to compensate for human reaction lag.
     */
    bool getZslFrame(int64_t shutterTimestampNs, int64_t offsetNs, RawFrame& outFrame);

    void clear();

private:
    std::vector<RawFrame> buffer;
    size_t head;
    size_t count;
    int currentFrameIndex;
    std::mutex bufferMutex;
};

} // namespace koshcam

#endif // KOSHCAM_ZSL_RING_BUFFER_H
