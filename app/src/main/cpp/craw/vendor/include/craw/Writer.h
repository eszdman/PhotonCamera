// SPDX-License-Identifier: Apache-2.0
//
// Streaming .CRAW container writer (SPEC.md section 1). Chunks are appended as
// they arrive, so a crash loses at most the chunk being written; close()
// adds the INDX chunk and trailer.
#pragma once

#include <craw/Encoder.h>

#include <cstdint>
#include <cstdio>
#include <string>
#include <string_view>
#include <vector>

namespace craw {

enum class AudioFormat : uint8_t { S16 = 1, F32 = 2, S24 = 3 };
enum class ImuSensor : uint8_t { Gyroscope = 1, Accelerometer = 2 };

struct ImuSample {
    int64_t timestampNs;
    float x, y, z;
};

class Writer {
public:
    // `metaJson` is the META chunk (SPEC.md section 2). Throws std::runtime_error.
    Writer(const std::string& path, std::string_view metaJson, unsigned threads = 1);
    // PhotonCamera: duplicates a SAF fd; caller retains ownership.
    Writer(int fd, std::string_view metaJson, unsigned threads = 1);
    ~Writer();
    Writer(const Writer&) = delete;
    Writer& operator=(const Writer&) = delete;

    void addFrame(const uint16_t* raw, size_t strideSamples, const FrameDesc& desc,
                  int64_t timestampNs, std::string_view frameMetaJson = "{}");
    // Appends a payload produced by encodeFrame() (possibly on another thread).
    // Lets callers encode on N workers and commit in order on one thread.
    // Throws std::logic_error when `timestampNs` does not increase.
    void addEncodedFrame(const uint8_t* payload, size_t size, int64_t timestampNs,
                         std::string_view frameMetaJson = "{}");
    void addAudio(int64_t timestampNs, AudioFormat format, uint8_t channels,
                  uint32_t sampleRate, const void* samples, uint32_t frameCount);
    void addImu(ImuSensor sensor, const ImuSample* samples, uint32_t count);
    // `gains`: 4 * mapW * mapH floats, Camera2 lens shading map layout.
    void addShading(int64_t timestampNs, uint32_t id, uint16_t mapW, uint16_t mapH,
                    const float* gains);
    void close();

    uint64_t frameCount() const { return frames_; }

private:
    void initialize(std::string_view metaJson);
    struct IndexEntry { char fourcc[4]; uint64_t offset; int64_t ts; };
    struct Part { const void* data; size_t size; };
    void putChunk(const char* fourcc, int64_t ts, const std::vector<uint8_t>& payload);
    // Writes one chunk whose payload is the concatenation of `parts` (no copy).
    void putChunkParts(const char* fourcc, int64_t ts, const Part* parts, size_t count);

    std::FILE* f_ = nullptr;
    uint64_t pos_ = 0;
    uint64_t frames_ = 0;
    int64_t lastFrameTs_ = INT64_MIN;
    unsigned threads_;
    std::vector<IndexEntry> index_;
    std::vector<uint8_t> scratch_, codec_;
};

}  // namespace craw
