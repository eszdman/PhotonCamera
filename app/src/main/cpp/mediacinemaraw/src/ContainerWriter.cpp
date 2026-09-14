// SPDX-License-Identifier: GPL-3.0-only
// MediaCinemaRAW version-3 container writer. Raw fd IO: frame payloads are
// written with
// one write() call each (the kernel/FUSE layer splits internally and
// pipelines much better than userspace small-block loops), while small items
// accumulate in a staging buffer that is flushed in large blocks.
#include <MediaCinemaRAW/ContainerWriter.h>
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <limits>
#include <stdexcept>
namespace mediacinemaraw { namespace {
constexpr size_t kFlushThreshold = 256 * 1024;

uint32_t ck(size_t n) { if (n > UINT32_MAX) throw std::length_error("item too large"); return uint32_t(n); }
}

ContainerWriter::ContainerWriter(const std::string& path, const std::string& metadata)
    : fd_(::open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644)) {
    if (fd_ < 0) throw std::runtime_error(std::string("cannot open output: ") + strerror(errno));
    start(metadata);
}
ContainerWriter::ContainerWriter(int fd, const std::string& metadata)
    : fd_(::dup(fd)) {
    if (fd_ < 0) throw std::runtime_error(std::string("cannot dup output fd: ") + strerror(errno));
    start(metadata);
}
void ContainerWriter::start(const std::string& metadata) {
    staging_.reserve(kFlushThreshold + 4096);
    const char h[8] = {'M','O','T','I','O','N',' ',3};
    stage(h,8); item(3,ck(metadata.size())); stage(metadata.data(),metadata.size());
    flushStaging();
}
ContainerWriter::~ContainerWriter() { try { close(); } catch (...) {} }

void ContainerWriter::stage(const void* p, size_t n) {
    const auto* bytes = static_cast<const uint8_t*>(p);
    staging_.insert(staging_.end(), bytes, bytes + n);
    if (staging_.size() >= kFlushThreshold) flushStaging();
}
void ContainerWriter::flushStaging() {
    if (!staging_.empty()) {
        writeAll(staging_.data(), staging_.size());
        staging_.clear();
    }
}
void ContainerWriter::writeAll(const void* p, size_t n) {
    const auto* cursor = static_cast<const uint8_t*>(p);
    while (n > 0) {
        const ssize_t written = ::write(fd_, cursor, n);
        if (written < 0) {
            if (errno == EINTR) continue;
            throw std::runtime_error(std::string("write failed: ") + strerror(errno));
        }
        if (written == 0) throw std::runtime_error("write returned 0");
        cursor += written;
        written_ += written;
        n -= size_t(written);
    }
}
int64_t ContainerWriter::position() {
    return int64_t(written_) + int64_t(staging_.size());
}
void ContainerWriter::item(uint32_t t, uint32_t n) {
    const uint8_t header[8] = {
        uint8_t(t), uint8_t(t >> 8), uint8_t(t >> 16), uint8_t(t >> 24),
        uint8_t(n), uint8_t(n >> 8), uint8_t(n >> 16), uint8_t(n >> 24),
    };
    stage(header, 8);
}

void ContainerWriter::writeFrame(const uint8_t* data, size_t size, int64_t ts, const std::string& m) {
    if (closed_) throw std::logic_error("closed");
    if (!frames_.empty() && ts <= frames_.back().timestamp) throw std::invalid_argument("timestamp");
    const int64_t offset = position();
    item(2,ck(size));
    flushStaging();                 // payload must land exactly at the recorded offset
    writeAll(data,size);
    item(3,ck(m.size()));
    stage(m.data(),m.size());
    frames_.push_back({offset,ts});
}
void ContainerWriter::writeAudio(const int16_t* s, size_t n, int64_t ts) {
    const int64_t offset = position();
    item(5,ck(n*2));
    std::vector<uint8_t> pcm(n*2);
    for (size_t i = 0; i < n; i++) {
        const uint16_t v = uint16_t(s[i]);
        pcm[i*2] = uint8_t(v);
        pcm[i*2+1] = uint8_t(v >> 8);
    }
    flushStaging();
    writeAll(pcm.data(), pcm.size());
    item(6,8);
    const uint8_t tsBytes[8] = {
        uint8_t(ts), uint8_t(ts >> 8), uint8_t(ts >> 16), uint8_t(ts >> 24),
        uint8_t(ts >> 32), uint8_t(ts >> 40), uint8_t(ts >> 48), uint8_t(ts >> 56),
    };
    stage(tsBytes, 8);
    audio_.push_back({offset,ts});
}
void ContainerWriter::writeGyro(const GyroSample* s, size_t n) {
    if (!n) return;
    const int64_t offset = position();
    item(9,ck(8+n*24));
    std::vector<uint8_t> data(8+n*24);
    data[0] = 1; // version
    const uint32_t count = ck(n);
    data[4] = uint8_t(count); data[5] = uint8_t(count >> 8);
    data[6] = uint8_t(count >> 16); data[7] = uint8_t(count >> 24);
    auto put64 = [&](size_t at, uint64_t v) {
        for (size_t i = 0; i < 8; ++i) data[at+i] = uint8_t(v >> (8*i));
    };
    auto putFloat = [&](size_t at, float v) {
        uint32_t bits; std::memcpy(&bits,&v,4);
        for (size_t i = 0; i < 4; ++i) data[at+i] = uint8_t(bits >> (8*i));
    };
    for (size_t i = 0; i < n; i++) {
        const size_t at = 8 + i*24;
        put64(at, uint64_t(s[i].timestampNs));
        putFloat(at+8, s[i].x); putFloat(at+12, s[i].y); putFloat(at+16, s[i].z);
        std::memset(&data[at+20], 0, 4);
    }
    flushStaging();
    writeAll(data.data(), data.size());
    gyro_.push_back({offset,s[0].timestampNs});
}
void ContainerWriter::close() {
    if (closed_) return;
    closed_ = true;
    if (fd_ < 0) return;
    try {
        if (!audio_.empty()) {
            item(4,ck(16+audio_.size()*16));
            staging_.reserve(staging_.size() + 16+audio_.size()*16);
            auto put64 = [&](int64_t v) {
                for (size_t i = 0; i < 8; ++i) staging_.push_back(uint8_t(uint64_t(v) >> (8*i)));
            };
            put64(int64_t(audio_.size()));
            // Despite the field name, the reference app stores the first
            // chunk's FULL NANOSECOND timestamp here; readers use it as the
            // audio timeline origin. Verified against a genuine recording.
            put64(audio_[0].timestamp);
            for (auto x : audio_) { put64(x.offset); put64(x.timestamp); }
        }
        if (!gyro_.empty()) {
            item(8,ck(8+gyro_.size()*16));
            staging_.reserve(staging_.size() + 8+gyro_.size()*16);
            staging_.push_back(1); staging_.push_back(0); staging_.push_back(0); staging_.push_back(0);
            const uint32_t count = ck(gyro_.size());
            for (size_t i = 0; i < 4; ++i) staging_.push_back(uint8_t(count >> (8*i)));
            auto put64 = [&](int64_t v) {
                for (size_t i = 0; i < 8; ++i) staging_.push_back(uint8_t(uint64_t(v) >> (8*i)));
            };
            for (auto x : gyro_) { put64(x.offset); put64(x.timestamp); }
        }
        item(1,ck(frames_.size()*16));
        const int64_t index = position();
        staging_.reserve(staging_.size() + frames_.size()*16);
        auto put64 = [&](int64_t v) {
            for (size_t i = 0; i < 8; ++i) staging_.push_back(uint8_t(uint64_t(v) >> (8*i)));
        };
        for (auto x : frames_) { put64(x.offset); put64(x.timestamp); }
        item(0,16);
        const uint32_t magic = 0x8A905612, count = ck(frames_.size());
        for (size_t i = 0; i < 4; ++i) staging_.push_back(uint8_t(magic >> (8*i)));
        for (size_t i = 0; i < 4; ++i) staging_.push_back(uint8_t(count >> (8*i)));
        put64(index);
        flushStaging();
        if (::fsync(fd_) != 0 && errno != EINVAL)
            throw std::runtime_error(std::string("fsync failed: ") + strerror(errno));
    } catch (...) {
        ::close(fd_);
        fd_ = -1;
        throw;
    }
    if (::close(fd_) != 0) {
        fd_ = -1;
        throw std::runtime_error(std::string("close failed: ") + strerror(errno));
    }
    fd_ = -1;
}
}
