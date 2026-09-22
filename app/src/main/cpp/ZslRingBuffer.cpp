#include "ZslRingBuffer.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "KoshcamZSL"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace koshcam {

ZslRingBuffer::ZslRingBuffer() : head(0), count(0), currentFrameIndex(0) {
    buffer.resize(BUFFER_CAPACITY);
}

ZslRingBuffer::~ZslRingBuffer() {
    clear();
}

void ZslRingBuffer::pushFrame(const uint8_t* rawData, size_t size, int width, int height, int64_t timestampNs) {
    std::lock_guard<std::mutex> lock(bufferMutex);

    RawFrame& frame = buffer[head];
    if (frame.data.size() != size) {
        frame.data.resize(size);
    }
    std::copy(rawData, rawData + size, frame.data.begin());
    frame.width = width;
    frame.height = height;
    frame.timestampNs = timestampNs;
    frame.frameIndex = currentFrameIndex++;

    head = (head + 1) % BUFFER_CAPACITY;
    if (count < BUFFER_CAPACITY) {
        count++;
    }

    LOGD("ZSL pushed frame #%d, ts=%lld, count=%zu", frame.frameIndex, (long long)timestampNs, count);
}

bool ZslRingBuffer::getZslFrame(int64_t shutterTimestampNs, int64_t offsetNs, RawFrame& outFrame) {
    std::lock_guard<std::mutex> lock(bufferMutex);

    if (count == 0) {
        LOGD("ZSL buffer is empty!");
        return false;
    }

    // Target timestamp: shutter timestamp minus offset (-200ms)
    int64_t targetTimestamp = shutterTimestampNs - offsetNs;
    LOGI("ZSL requested target timestamp: %lld (shutter=%lld, offset=%lld)",
         (long long)targetTimestamp, (long long)shutterTimestampNs, (long long)offsetNs);

    int bestIndex = -1;
    int64_t minDiff = -1;

    for (size_t i = 0; i < count; ++i) {
        size_t idx = (head + BUFFER_CAPACITY - 1 - i) % BUFFER_CAPACITY;
        const RawFrame& frame = buffer[idx];

        int64_t diff = std::abs(frame.timestampNs - targetTimestamp);
        if (minDiff == -1 || diff < minDiff) {
            minDiff = diff;
            bestIndex = static_cast<int>(idx);
        }
    }

    if (bestIndex != -1) {
        const RawFrame& bestFrame = buffer[bestIndex];
        outFrame = bestFrame;
        LOGI("ZSL matched frame #%d, ts=%lld, diff=%lld ms",
             bestFrame.frameIndex, (long long)bestFrame.timestampNs, (long long)(minDiff / 1000000));
        return true;
    }

    return false;
}

void ZslRingBuffer::clear() {
    std::lock_guard<std::mutex> lock(bufferMutex);
    for (auto& frame : buffer) {
        frame.data.clear();
        frame.data.shrink_to_fit();
    }
    head = 0;
    count = 0;
}

} // namespace koshcam
