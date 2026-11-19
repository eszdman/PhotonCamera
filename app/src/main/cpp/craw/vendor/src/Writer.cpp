// SPDX-License-Identifier: Apache-2.0
#include <craw/Writer.h>

#include "Crc32c.h"

#include <cstring>
#include <unistd.h>
#include <stdexcept>

namespace craw {
namespace {

template <typename T>
void put(std::vector<uint8_t>& o, T v) {
    for (size_t i = 0; i < sizeof(T); ++i) o.push_back(static_cast<uint8_t>(uint64_t(v) >> (8 * i)));
}

void putF32(std::vector<uint8_t>& o, float f) {
    uint32_t u;
    std::memcpy(&u, &f, 4);
    put(o, u);
}

void putBytes(std::vector<uint8_t>& o, const void* p, size_t n) {
    auto b = static_cast<const uint8_t*>(p);
    o.insert(o.end(), b, b + n);
}

}  // namespace

Writer::Writer(const std::string& path, std::string_view metaJson, unsigned threads)
    : threads_(threads) {
    f_ = std::fopen(path.c_str(), "wb");
    if (!f_) throw std::runtime_error("craw::Writer: cannot open " + path);
    try { initialize(metaJson); } catch (...) { std::fclose(f_); f_ = nullptr; throw; }
}

Writer::Writer(int fd, std::string_view metaJson, unsigned threads) : threads_(threads) {
    int owned = dup(fd);
    if (owned < 0) throw std::runtime_error("craw::Writer: dup failed");
    f_ = fdopen(owned, "wb");
    if (!f_) { ::close(owned); throw std::runtime_error("craw::Writer: fdopen failed"); }
    try { initialize(metaJson); } catch (...) { std::fclose(f_); f_ = nullptr; throw; }
}

void Writer::initialize(std::string_view metaJson) {
    std::vector<uint8_t> head{'C', 'R', 'A', 'W'};
    put<uint16_t>(head, 0);
    put<uint16_t>(head, 2);
    put<uint32_t>(head, 0);
    put<uint32_t>(head, 0);
    if (std::fwrite(head.data(), 1, head.size(), f_) != head.size())
        throw std::runtime_error("craw::Writer: write failed");
    pos_ = head.size();
    scratch_.assign(metaJson.begin(), metaJson.end());
    putChunk("META", 0, scratch_);
    index_.clear();  // META is not indexed
}

Writer::~Writer() {
    if (f_) {
        try { close(); } catch (...) {}
    }
}

void Writer::putChunk(const char* fourcc, int64_t ts, const std::vector<uint8_t>& payload) {
    const Part part{payload.data(), payload.size()};
    putChunkParts(fourcc, ts, &part, 1);
}

void Writer::putChunkParts(const char* fourcc, int64_t ts, const Part* parts, size_t count) {
    if (!f_) throw std::runtime_error("craw::Writer: closed");
    IndexEntry e{};
    std::memcpy(e.fourcc, fourcc, 4);
    e.offset = pos_;
    e.ts = ts;
    uint64_t size = 0;
    uint32_t crc = 0;
    for (size_t i = 0; i < count; ++i) {
        size += parts[i].size;
        crc = detail::crc32c(parts[i].data, parts[i].size, crc);
    }
    std::vector<uint8_t> head;
    putBytes(head, fourcc, 4);
    put<uint32_t>(head, 0);
    put<uint64_t>(head, size);
    std::vector<uint8_t> tail;
    put<uint32_t>(tail, crc);
    bool ok = std::fwrite(head.data(), 1, head.size(), f_) == head.size();
    for (size_t i = 0; ok && i < count; ++i)
        ok = std::fwrite(parts[i].data, 1, parts[i].size, f_) == parts[i].size;
    ok = ok && std::fwrite(tail.data(), 1, tail.size(), f_) == tail.size();
    if (!ok) throw std::runtime_error("craw::Writer: write failed");
    pos_ += head.size() + size + tail.size();
    index_.push_back(e);
}

void Writer::addFrame(const uint16_t* raw, size_t stride, const FrameDesc& desc,
                      int64_t ts, std::string_view frameMetaJson) {
    encodeFrame(raw, stride, desc, codec_, threads_);
    addEncodedFrame(codec_.data(), codec_.size(), ts, frameMetaJson);
}

void Writer::addEncodedFrame(const uint8_t* payload, size_t size, int64_t ts,
                             std::string_view frameMetaJson) {
    if (!payload || size == 0) throw std::invalid_argument("craw::Writer: empty payload");
    if (ts <= lastFrameTs_) throw std::logic_error("craw::Writer: frame timestamp regression");
    scratch_.clear();
    put<uint64_t>(scratch_, frames_);
    put<int64_t>(scratch_, ts);
    put<uint32_t>(scratch_, static_cast<uint32_t>(frameMetaJson.size()));
    const Part parts[] = {{scratch_.data(), scratch_.size()},
                          {frameMetaJson.data(), frameMetaJson.size()},
                          {payload, size}};
    putChunkParts("FRAM", ts, parts, 3);
    lastFrameTs_ = ts;
    ++frames_;
}

void Writer::addAudio(int64_t ts, AudioFormat fmt, uint8_t channels, uint32_t rate,
                      const void* samples, uint32_t frameCount) {
    const size_t bps = fmt == AudioFormat::S16 ? 2 : fmt == AudioFormat::S24 ? 3 : 4;
    scratch_.clear();
    put<int64_t>(scratch_, ts);
    put<uint8_t>(scratch_, static_cast<uint8_t>(fmt));
    put<uint8_t>(scratch_, channels);
    put<uint16_t>(scratch_, 0);
    put<uint32_t>(scratch_, rate);
    put<uint32_t>(scratch_, frameCount);
    putBytes(scratch_, samples, bps * channels * frameCount);
    putChunk("AUDI", ts, scratch_);
}

void Writer::addImu(ImuSensor sensor, const ImuSample* s, uint32_t count) {
    scratch_.clear();
    put<uint8_t>(scratch_, static_cast<uint8_t>(sensor));
    put<uint8_t>(scratch_, 0);
    put<uint16_t>(scratch_, 0);
    put<uint32_t>(scratch_, count);
    for (uint32_t i = 0; i < count; ++i) {
        put<int64_t>(scratch_, s[i].timestampNs);
        putF32(scratch_, s[i].x);
        putF32(scratch_, s[i].y);
        putF32(scratch_, s[i].z);
    }
    putChunk("IMU_", count ? s[0].timestampNs : 0, scratch_);
}

void Writer::addShading(int64_t ts, uint32_t id, uint16_t w, uint16_t h, const float* gains) {
    scratch_.clear();
    put<int64_t>(scratch_, ts);
    put<uint32_t>(scratch_, id);
    put<uint16_t>(scratch_, w);
    put<uint16_t>(scratch_, h);
    for (size_t i = 0; i < size_t{4} * w * h; ++i) putF32(scratch_, gains[i]);
    putChunk("LSHD", ts, scratch_);
}

void Writer::close() {
    if (!f_) return;
    const uint64_t indexOffset = pos_;
    scratch_.clear();
    put<uint32_t>(scratch_, static_cast<uint32_t>(index_.size()));
    for (auto& e : index_) {
        putBytes(scratch_, e.fourcc, 4);
        put<uint32_t>(scratch_, 0);
        put<uint64_t>(scratch_, e.offset);
        put<int64_t>(scratch_, e.ts);
    }
    putChunk("INDX", 0, scratch_);
    std::vector<uint8_t> trailer;
    put<uint64_t>(trailer, indexOffset);
    putBytes(trailer, "CRAWEND\0", 8);
    const bool ok = std::fwrite(trailer.data(), 1, trailer.size(), f_) == trailer.size();
    const bool closed = std::fclose(f_) == 0;
    f_ = nullptr;
    if (!ok || !closed) throw std::runtime_error("craw::Writer: close failed");
}

}  // namespace craw
