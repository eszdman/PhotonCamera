#pragma once
#include <craw/Encoder.h>
#include <stdexcept>
#include <vector>

namespace photon {
// Convert packed Android RAW10 / padded RAW16 without shifting sample codes.
// Binning, when requested, averages four samples of the same Bayer colour.
inline craw::FrameDesc prepareRaw(const uint8_t* raw, size_t size, int width, int height,
        int stride, bool raw10, int top, int rows, bool bin, int cfa, int codec,
        std::vector<uint16_t>& pixels) {
    if (!raw || width <= 0 || height <= 0 || width > 65536 || height > 65536
            || (width & 1) || (raw10 && width % 4) || top < 0 || (top & 1)
            || rows <= 0 || top > height - rows || (rows & 1)
            || (bin && (width % 4 || rows % 4)) || cfa < 0 || cfa > 3
            || (codec != 1 && codec != 3))
        throw std::invalid_argument("Invalid CRAW input geometry or codec");
    size_t rowBytes = raw10 ? size_t(width) / 4 * 5 : size_t(width) * 2;
    if (stride < 0 || size_t(stride) < rowBytes
            || size < size_t(height - 1) * stride + rowBytes)
        throw std::invalid_argument("Truncated CRAW input plane");
    auto sample = [&](int x, int y) -> uint16_t {
        const uint8_t* row = raw + size_t(y + top) * stride;
        if (raw10) return (uint16_t(row[x / 4 * 5 + x % 4]) << 2)
                | ((row[x / 4 * 5 + 4] >> (2 * (x % 4))) & 3);
        return uint16_t(row[x * 2]) | (uint16_t(row[x * 2 + 1]) << 8);
    };
    craw::FrameDesc desc;
    desc.width = bin ? width / 2 : width;
    desc.height = bin ? rows / 2 : rows;
    // RAW16 is a storage format, not a 10-bit guarantee. Never truncate DCG/14/16-bit data.
    desc.bitDepth = raw10 ? 10 : 16;
    desc.cfa = static_cast<craw::Cfa>(cfa);
    desc.codec = codec;
    desc.frameCrc = false; // Writer protects every chunk with CRC32C.
    pixels.resize(size_t(desc.width) * desc.height);
    for (unsigned y = 0; y < desc.height; ++y) for (unsigned x = 0; x < desc.width; ++x) {
        uint16_t value;
        if (bin) {
            int sx = (x / 2) * 4 + x % 2, sy = (y / 2) * 4 + y % 2;
            value = (uint32_t(sample(sx, sy)) + sample(sx + 2, sy)
                    + sample(sx, sy + 2) + sample(sx + 2, sy + 2) + 2) / 4;
        } else value = sample(x, y);
        pixels[size_t(y) * desc.width + x] = value;
    }
    return desc;
}
}
