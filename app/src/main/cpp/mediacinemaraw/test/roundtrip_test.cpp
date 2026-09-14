// SPDX-License-Identifier: GPL-3.0-only
// Standalone host roundtrip test for the vendored MediaCinemaRAW encoder.
//
// 1. Implements an independent decoder for the version-3 container and the
//    compression-type-7 frame format (derived from the format specification,
//    not from the encoder code paths) and verifies that encoded files decode
//    back to the exact source pixels for RAW16/RAW10, stride padding,
//    even-row cropping, 4x binning and every delta bit-width packing mode.
// 2. Replicates RawVideoProcessor.computeMcrawGeometry() and runs it over
//    realistic sensor layouts (including unpadded last rows) to prove the
//    parameters it hands to the encoder always satisfy the encoder's
//    geometry contract.
//
// Build & run on host:
//   g++ -std=c++14 -I ../include roundtrip_test.cpp ../src/Encoder.cpp \
//       ../src/ContainerWriter.cpp -o /tmp/mcraw_test && /tmp/mcraw_test
#include <MediaCinemaRAW/Encoder.h>
#include <MediaCinemaRAW/ContainerWriter.h>

#include <fcntl.h>
#include <unistd.h>

#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <random>
#include <string>
#include <utility>
#include <vector>

using Bytes = std::vector<uint8_t>;

static uint32_t rd32(const uint8_t* p) {
    return uint32_t(p[0]) | uint32_t(p[1])<<8 | uint32_t(p[2])<<16 | uint32_t(p[3])<<24;
}
static uint16_t rd16(const uint8_t* p) {
    return uint16_t(p[0]) | uint16_t(p[1])<<8;
}

// Inverse of the encoder's 64-value lane packing for every emitted bit width.
static void unpack64(const uint8_t* src, int bits, uint16_t* out /*64*/) {
    const uint16_t mask = bits ? uint16_t((1u << bits) - 1) : 0;
    if (bits == 0) { std::memset(out, 0, 128); return; }
    if (bits == 16) { for (int i = 0; i < 64; ++i) out[i] = rd16(src + i*2); return; }
    if (bits == 8)  { for (int i = 0; i < 64; ++i) out[i] = src[i]; return; }
    if (bits == 3) {
        for (int lane = 0; lane < 8; ++lane) {
            const uint8_t b0 = src[lane], b1 = src[8+lane], b2 = src[16+lane];
            uint16_t v[8];
            v[0] = b0 & 7; v[1] = (b0>>3) & 7; v[2] = (b0>>6) | (((b2>>6)&1)<<2);
            v[3] = b1 & 7; v[4] = (b1>>3) & 7; v[5] = (b1>>6) | (((b2>>7)&1)<<2);
            v[6] = b2 & 7; v[7] = (b2>>3) & 7;
            for (int g = 0; g < 8; ++g) out[g*8+lane] = v[g];
        }
        return;
    }
    if (bits == 5) {
        for (int lane = 0; lane < 8; ++lane) {
            const uint8_t b0 = src[lane], b1 = src[8+lane], b2 = src[16+lane];
            const uint8_t b3 = src[24+lane], b4 = src[32+lane];
            uint16_t v[8];
            v[0] = b0 & 31; v[1] = b1 & 31; v[2] = b2 & 31; v[3] = b3 & 31; v[4] = b4 & 31;
            v[5] = (b0>>5) | (((b3>>5)&3)<<3);
            v[6] = (b1>>5) | (((b4>>5)&3)<<3);
            v[7] = (b2>>5) | (((b3>>7)&1)<<3) | (((b4>>7)&1)<<4);
            for (int g = 0; g < 8; ++g) out[g*8+lane] = v[g];
        }
        return;
    }
    if (bits == 6) {
        for (int lane = 0; lane < 8; ++lane) {
            uint16_t v[8];
            for (int g = 0; g < 6; ++g) v[g] = src[g*8+lane] & 63;
            v[6] = ((src[0*8+lane]>>6)&3) | (((src[1*8+lane]>>6)&3)<<2) | (((src[2*8+lane]>>6)&3)<<4);
            v[7] = ((src[3*8+lane]>>6)&3) | (((src[4*8+lane]>>6)&3)<<2) | (((src[5*8+lane]>>6)&3)<<4);
            for (int g = 0; g < 8; ++g) out[g*8+lane] = v[g];
        }
        return;
    }
    if (bits == 10) {
        for (int half = 0; half < 2; ++half) {
            const uint8_t* low = src + half*40;
            const uint8_t* hi  = src + half*40 + 32;
            for (int group = 0; group < 4; ++group)
                for (int lane = 0; lane < 8; ++lane)
                    out[half*32 + group*8 + lane] =
                        uint16_t(low[group*8+lane]) | (uint16_t((hi[lane] >> (group*2)) & 3) << 8);
        }
        return;
    }
    // bits == 1, 2 or 4: sequential groups packed across lanes.
    const int groups = 8 / bits;
    int pos = 0;
    for (int base = 0; base < 64; base += groups*8)
        for (int lane = 0; lane < 8; ++lane, ++pos)
            for (int g = 0; g < groups; ++g)
                out[base + g*8 + lane] = (src[pos] >> (g*bits)) & mask;
}

static int packedSize(int bits) {
    switch (bits) {
        case 0: return 0;   case 1: return 8;    case 2: return 16;
        case 3: return 24;  case 4: return 32;   case 5: return 40;
        case 6: return 48;  case 8: return 64;   case 10: return 80;
        case 16: return 128;
    }
    assert(false);
    return 0;
}

// Decodes a per-channel metadata stream (bit widths or references).
static std::vector<uint16_t> decodeMeta(const uint8_t*& p) {
    const uint32_t count = rd32(p); p += 4;
    assert(count % 64 == 0);
    std::vector<uint16_t> values(count);
    for (uint32_t i = 0; i < count; i += 64) {
        const int bitsField = p[0] >> 4;
        const int bits = bitsField == 15 ? 16 : bitsField;
        const uint16_t ref = (uint16_t(p[0] & 0xF) << 8) | p[1];
        p += 2;
        uint16_t block[64];
        unpack64(p, bits, block);
        p += packedSize(bits);
        for (int j = 0; j < 64; ++j) values[i+j] = block[j] + ref;
    }
    return values;
}

struct DecodedFrame {
    int width = 0, height = 0; // padded width, visible height
    Bytes meta;
    std::vector<uint16_t> pixels;
};

static DecodedFrame decodeFrame(const uint8_t* p, size_t size, const Bytes& meta) {
    assert(size >= 16);
    DecodedFrame f;
    f.meta = meta;
    const int ew = int(rd32(p));
    const int h  = int(rd32(p+4));
    const uint32_t bitsOff = rd32(p+8);
    const uint32_t refsOff = rd32(p+12);
    assert(bitsOff <= refsOff && refsOff <= size);

    const uint8_t* bitsPtr = p + bitsOff;
    const uint8_t* refsPtr = p + refsOff;
    const std::vector<uint16_t> bits = decodeMeta(bitsPtr);
    const std::vector<uint16_t> refs = decodeMeta(refsPtr);
    assert(bitsPtr == p + refsOff && refsPtr == p + size);

    const uint8_t* cursor = p + 16;
    std::vector<uint16_t> frame(size_t(ew) * h, 0xFFFF);
    size_t channel = 0;
    for (int y = 0; y < h; y += 4) {
        for (int x = 0; x < ew; x += 64) {
            for (int c = 0; c < 4; ++c, ++channel) {
                const int b = bits[channel];
                const uint16_t ref = refs[channel];
                uint16_t values[64];
                unpack64(cursor, b, values);
                cursor += packedSize(b);
                for (int i = 0; i < 64; ++i) {
                    const int px = x + (i % 32)*2 + c % 2;
                    const int py = y + (i / 32)*2 + c / 2;
                    if (px < ew) frame[size_t(py)*ew + px] = values[i] + ref;
                }
            }
        }
    }
    assert(cursor == p + bitsOff);
    // The frame header carries the 64-column-padded width (ew); the visible
    // width arrives via frame metadata. Padding columns decode to zero.
    f.width = ew;
    f.height = h;
    f.pixels = std::move(frame);
    return f;
}

struct Container {
    Bytes headerMeta;
    std::vector<DecodedFrame> frames;
    std::vector<std::pair<int64_t,int64_t>> frameIndex;
};

static Container parseContainer(const Bytes& file) {
    Container c;
    assert(file.size() >= 8);
    assert(std::memcmp(file.data(), "MOTION ", 7) == 0 && file[7] == 3);
    size_t pos = 8;
    std::vector<int64_t> frameItemPositions;
    auto item = [&](Bytes& payload) -> uint32_t {
        assert(pos + 8 <= file.size());
        const uint32_t type = rd32(file.data()+pos);
        const uint32_t len = rd32(file.data()+pos+4);
        pos += 8;
        assert(pos + len <= file.size());
        payload.assign(file.begin()+pos, file.begin()+pos+len);
        pos += len;
        return type;
    };
    Bytes payload;
    if (item(payload) == 3) c.headerMeta = payload; else assert(false);
    std::vector<std::pair<Bytes,Bytes>> rawFrames;
    std::vector<int64_t> audioItemPositions, gyroItemPositions;
    int64_t indexPayloadStart = -1;
    auto rd64f = [&](size_t p) {
        return int64_t(rd32(file.data()+p)) | (int64_t(rd32(file.data()+p+4)) << 32);
    };
    auto rd64p = [&](const Bytes& b, size_t off) {
        return int64_t(rd32(b.data()+off)) | (int64_t(rd32(b.data()+off+4)) << 32);
    };
    while (pos < file.size()) {
        const int64_t at = int64_t(pos);
        const uint32_t type = item(payload);
        switch (type) {
            case 2: rawFrames.emplace_back(payload, Bytes()); frameItemPositions.push_back(at); break;
            case 3: if (!rawFrames.empty()) rawFrames.back().second = payload; break;
            case 5: audioItemPositions.push_back(at); break;               // PCM audio chunk
            case 6: assert(payload.size() == 8); break;                     // audio chunk timestamp
            case 9: gyroItemPositions.push_back(at); break;                // gyro sample block
            case 4: {                                                       // audio index
                assert(payload.size() == 16 + audioItemPositions.size()*16);
                assert(rd64p(payload, 0) == (int64_t)audioItemPositions.size());
                // startTimestampMs actually carries full nanoseconds, as the
                // reference app writes it (verified against a genuine file).
                assert(rd64p(payload, 8) == 1001000000LL);
                for (size_t i = 0; i < audioItemPositions.size(); ++i) {
                    assert(rd64p(payload, 16+i*16) == audioItemPositions[i]);
                    assert(rd64p(payload, 24+i*16) == 1001000000LL);
                }
                break;
            }
            case 8: {                                                       // gyro index
                assert(payload.size() == 8 + gyroItemPositions.size()*16);
                assert(rd32(payload.data()+4) == gyroItemPositions.size());
                for (size_t i = 0; i < gyroItemPositions.size(); ++i) {
                    assert(rd64p(payload, 8+i*16) == gyroItemPositions[i]);
                    assert(rd64p(payload, 16+i*16) == 1002000000LL);
                }
                break;
            }
            case 1: {
                indexPayloadStart = at + 8; // writer records payload start
                assert(payload.size() % 16 == 0);
                for (size_t i = 0; i < payload.size(); i += 16) {
                    int64_t off = int64_t(rd32(payload.data()+i))
                                | (int64_t(rd32(payload.data()+i+4)) << 32);
                    int64_t ts  = int64_t(rd32(payload.data()+i+8))
                                | (int64_t(rd32(payload.data()+i+12)) << 32);
                    c.frameIndex.emplace_back(off, ts);
                }
                break;
            }
            case 0: {
                assert(payload.size() == 16);
                assert(rd32(payload.data()) == 0x8A905612);
                assert(rd32(payload.data()+4) == rawFrames.size());
                const int64_t indexOffset = int64_t(rd32(payload.data()+8))
                                          | (int64_t(rd32(payload.data()+12)) << 32);
                assert(indexOffset == indexPayloadStart);
                break;
            }
            default: assert(false);
        }
    }
    for (size_t i = 0; i < rawFrames.size(); ++i)
        c.frames.push_back(decodeFrame(rawFrames[i].first.data(), rawFrames[i].first.size(), rawFrames[i].second));
    assert(c.frameIndex.size() == rawFrames.size());
    for (size_t i = 0; i < rawFrames.size(); ++i)
        assert(c.frameIndex[i].first == frameItemPositions[i]);
    return c;
}

// --- Test case runner -------------------------------------------------------

static int g_seen[17] = {0};

// Generates a random frame, encodes it, writes a two-frame container through
// the fd-based writer, parses it back and verifies every pixel.
static void runCase(std::mt19937& random, int width, int height, int stride,
                    bool raw10, int top, int cropped, bool bin, unsigned range, bool constant) {
    std::vector<uint16_t> pixels(size_t(width)*height);
    for (auto& px : pixels) {
        px = raw10 ? random() % std::min(1024u, range) : random() % range;
        if (constant) px = raw10 ? 800 : 50000; // constant, nonzero reference
    }
    // The last row may omit stride padding, as Android Image planes do.
    const size_t lastRowBytes = raw10 ? size_t(width)/4*5 : size_t(width)*2;
    Bytes raw(size_t(height-1)*stride + lastRowBytes, 0xAD);
    for (int y = 0; y < height; ++y) for (int x = 0; x < width; ++x) {
        const uint16_t v = pixels[size_t(y)*width+x];
        if (raw10) {
            if (x % 4 == 0) raw[size_t(y)*stride + x/4*5 + 4] = 0; // clear 2-bit sample byte
            raw[size_t(y)*stride + x/4*5 + x%4] = uint8_t(v >> 2);
            raw[size_t(y)*stride + x/4*5 + 4] |= uint8_t((v & 3) << (2*(x%4)));
        } else {
            raw[size_t(y)*stride + x*2] = uint8_t(v);
            raw[size_t(y)*stride + x*2+1] = uint8_t(v >> 8);
        }
    }
    Bytes encoded;
    mediacinemaraw::encode(raw.data(), raw.size(), width, height, stride,
                           raw10, top, cropped, bin, encoded);
    const int w = bin ? width/2 : width, h = bin ? cropped/2 : cropped;

    const std::string path = "/tmp/mcraw_roundtrip_test.mcraw";
    {
        const int fd = ::open(path.c_str(), O_WRONLY|O_CREAT|O_TRUNC, 0644);
        assert(fd >= 0);
        mediacinemaraw::ContainerWriter writer(fd,
            "{\"manufacturer\":\"T\",\"model\":\"U\","
            "\"UniqueCameraModel\":\"T U\",\"uniqueCameraModel\":\"T U\"}");
        ::close(fd);
        for (int frame = 0; frame < 2; ++frame)
            writer.writeFrame(encoded, 1000000000LL + frame, "{\"width\":64,\"height\":8,\"compressionType\":7}");
        const int16_t pcm[] = {1, -2, 3, -4};
        writer.writeAudio(pcm, 4, 1001000000LL);
        const mediacinemaraw::GyroSample gyroSamples[] = {
            {1002000000LL, 0.1f, -0.2f, 0.3f}, {1002000050LL, 0.2f, -0.1f, 0.3f}};
        writer.writeGyro(gyroSamples, 2);
        writer.close();
        assert(writer.frameCount() == 2);
    }
    std::ifstream in(path, std::ios::binary);
    const Bytes file((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    const Container c = parseContainer(file);

    assert(c.frames.size() == 2);
    assert(c.frameIndex[0].second == 1000000000LL && c.frameIndex[1].second == 1000000001LL);
    assert(std::string(c.headerMeta.begin(), c.headerMeta.end()).find("UniqueCameraModel") != std::string::npos);

    // Which delta bit-width packing modes did this data exercise?
    {
        const uint8_t* p = encoded.data() + rd32(encoded.data()+8);
        for (uint16_t b : decodeMeta(p)) g_seen[b] = 1;
    }

    for (const DecodedFrame& f : c.frames) {
        const int ew = (w + 63) / 64 * 64;
        assert(f.width == ew && f.height == h);
        assert(f.pixels.size() == size_t(ew)*h);
        for (int y = 0; y < h; ++y) for (int x = 0; x < ew; ++x) {
            unsigned expected;
            if (x >= w) expected = 0; // padding columns decode to zero
            else if (bin) {
                const int sx = x/2*4 + x%2, sy = top + y/2*4 + y%2;
                expected = (unsigned(pixels[size_t(sy)*width+sx]) + pixels[size_t(sy)*width+sx+2]
                          + pixels[size_t(sy+2)*width+sx] + pixels[size_t(sy+2)*width+sx+2] + 2)/4;
            } else {
                expected = pixels[size_t(top+y)*width+x];
            }
            const unsigned actual = f.pixels[size_t(y)*f.width + x];
            if (actual != expected) {
                std::fprintf(stderr, "mismatch raw10=%d bin=%d w=%d at %d,%d: %u != %u\n",
                             raw10, bin, width, x, y, actual, expected);
                std::exit(1);
            }
        }
    }
}

// --- Java geometry replica --------------------------------------------------

// Exact replica of RawVideoProcessor.computeMcrawGeometry(): given the plane
// parameters an Android Image would report, produce the encode() arguments.
struct JavaGeo {
    int width, imageHeight, cropTop, cropHeight;
    bool bin;
};

static JavaGeo javaGeometry(int imageHeight, int stride, long planeCapacityBytes,
                            bool raw10, bool crop169, bool cropFromTop, bool binSetting) {
    int width = raw10 ? stride * 8 / 10 : stride / 2;
    const long lastRowBytes = planeCapacityBytes - long(imageHeight - 1) * stride;
    if (lastRowBytes > 0) {
        const int maxWidth = raw10 ? int(lastRowBytes * 4 / 5) : int(lastRowBytes / 2);
        if (width > maxWidth) width = maxWidth;
    }
    width -= raw10 ? width % 4 : width % 2;
    int cropHeight, cropTop = 0;
    if (raw10) {
        cropHeight = imageHeight;
    } else if (crop169) {
        cropHeight = width * 9 / 16;
        if (!cropFromTop) {
            cropTop = (imageHeight - cropHeight) / 2;
            cropTop -= cropTop % 2;
        }
    } else {
        cropHeight = imageHeight;
    }
    bool bin = binSetting;
    if (bin && width % 4 != 0) width -= width % 4;
    cropHeight -= bin ? cropHeight % 8 : cropHeight % 4;
    if (cropHeight <= 0 || cropTop > imageHeight - cropHeight) {
        cropTop = 0;
        cropHeight = imageHeight - imageHeight % 4;
        bin = false;
    }
    return {width, imageHeight, cropTop, cropHeight, bin};
}

int main() {
    std::mt19937 random(7139);
    int cases = 0;

    // 1) Randomized format-level roundtrips across the packing space.
    const unsigned ranges[] = {1,2,4,8,16,32,64,256,1024,4096,16384,65536};
    for (bool raw10 : {false, true}) for (bool bin : {false, true})
    for (int width : {4, 64, 68, 260}) for (int mode = 0; mode < 12; ++mode) {
        runCase(random, width, 24, (raw10 ? width/4*5 : width*2) + 24,
                raw10, 2, 16, bin, ranges[mode], mode == 0);
        ++cases;
    }

    // 2) Java geometry replica over realistic sensor layouts. The "capacity"
    // variants exercise full-stride and unpadded-last-row allocations.
    struct Sensor { int w, h; bool raw10; };
    const Sensor sensors[] = {
        {4000, 3000, false}, {4032, 3024, false}, {4096, 3072, false},
        {4608, 3456, false}, {4080, 3060, false}, {4000, 3000, true},
        {4096, 3072, true},  {5344, 4008, false}, {8160, 6144, false},
        {1440, 1080, false}, {1920, 1080, false},
    };
    int geoCases = 0;
    for (const Sensor& s : sensors) {
        const int stride = s.raw10 ? s.w/4*5 + (64 - (s.w/4*5) % 64) % 64
                                   : s.w*2 + (64 - (s.w*2) % 64) % 64;
        for (int variant = 0; variant < 3; ++variant) {
            // 0: full-stride allocation, 1: unpadded last row, 2: excessive pad
            const long capacity = variant == 0 ? long(stride) * s.h
                                : variant == 1 ? long(stride) * (s.h - 1) + (s.raw10 ? s.w/4*5 : s.w*2)
                                : long(stride) * (s.h + 2);
            for (bool crop169 : {false, true}) for (bool fromTop : {false, true})
            for (bool bin : {false, true}) {
                const JavaGeo g = javaGeometry(s.h, stride, capacity, s.raw10, crop169, fromTop, bin);
                assert(g.cropTop >= 0 && g.cropTop % 2 == 0);
                assert(g.cropHeight > 0 && g.cropHeight % 4 == 0);
                assert(g.cropTop + g.cropHeight <= s.h);
                assert(g.width > 0 && g.width % 2 == 0);
                assert(!s.raw10 || g.width % 4 == 0);
                assert(!g.bin || (g.width % 4 == 0 && g.cropHeight % 8 == 0));
                // The geometry must also survive the real encoder on random data.
                runCase(random, g.width, g.imageHeight, stride, s.raw10,
                        g.cropTop, g.cropHeight, g.bin, s.raw10 ? 1024u : 65536u, false);
                ++geoCases;
            }
        }
    }

    int packingModesSeen = 0;
    for (int b : {0,1,2,3,4,5,6,8,10}) packingModesSeen += g_seen[b];
    std::printf("%d format roundtrips + %d Java-geometry cases passed; "
                "packing modes exercised: %d/9 (16-bit literal: %s)\n",
                cases, geoCases, packingModesSeen, g_seen[16] ? "yes" : "no");
    return 0;
}
