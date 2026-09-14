// SPDX-License-Identifier: GPL-3.0-only
// Clean-room MediaCinemaRAW container writer (version-3 container).
// Android adaptation: output is a raw file descriptor (created through SAF
// by the Java side) and all writes are issued as large blocks. Bionic's
// stdio buffers in small chunks, which multiplies FUSE round-trips on
// Android storage and stalls the pipeline; this writer stages only small
// items and writes frame payloads with single write() calls.
#pragma once
#include <cstdint>
#include <string>
#include <vector>
namespace mediacinemaraw {
struct GyroSample { int64_t timestampNs; float x,y,z; };
class ContainerWriter {
public:
    ContainerWriter(const std::string& path, const std::string& metadata);
    // Dups fd; the caller keeps ownership of the original descriptor.
    ContainerWriter(int fd, const std::string& metadata);
    ~ContainerWriter();
    ContainerWriter(const ContainerWriter&) = delete;
    ContainerWriter& operator=(const ContainerWriter&) = delete;
    void writeFrame(const uint8_t* data, size_t size, int64_t timestamp, const std::string& meta);
    void writeFrame(const std::vector<uint8_t>& d, int64_t ts, const std::string& m) {
        writeFrame(d.data(), d.size(), ts, m);
    }
    void writeAudio(const int16_t*, size_t, int64_t);
    void writeGyro(const GyroSample*, size_t);
    void close();
    size_t frameCount() const noexcept { return frames_.size(); }
private:
    struct Offset { int64_t offset, timestamp; };
    int fd_ = -1;
    std::vector<uint8_t> staging_;   // batches small items into big writes
    int64_t written_ = 0;            // bytes handed to write()
    std::vector<Offset> frames_, audio_, gyro_;
    bool closed_ = false;
    void start(const std::string& metadata);
    void stage(const void*, size_t);
    void flushStaging();
    void writeAll(const void*, size_t);   // direct large block write
    void item(uint32_t, uint32_t);
    int64_t position();                   // logical end of file so far
};
}
