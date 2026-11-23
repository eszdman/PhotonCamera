//
// Created by eszdman on 19.11.2026.
//

#include "Bayer.hpp"
#include "halfConvert.h"
#include "normalizeRaw.hpp"
#include "packedRaw.hpp"
#include <cstring>
#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace photon::esd4d {
float halfValue(std::uint16_t h) {
    int exp = (h >> 10) & 31;
    if (exp == 31)
        return (h & 1023) ? NAN : (h & 0x8000 ? -INFINITY : INFINITY);
    if (!exp) {
        float v = float(h & 1023) * 0x1p-24f;
        return h & 0x8000 ? -v : v;
    }
    const std::uint32_t raw = (std::uint32_t(h & 0x8000) << 16) |
                              (std::uint32_t(exp + 112) << 23) | (std::uint32_t(h & 1023) << 13);
    float v;
    std::memcpy(&v, &raw, sizeof(v));
    return v;
}
namespace {
// Equivalent to converting to float, clamping to [0,1], then converting back.
std::uint16_t clampHalf(std::uint16_t bits) {
    if (bits <= 0x3c00 || bits == 0x8000)
        return bits;
    if ((bits & 0x7fff) > 0x7c00)
        return f32ToF16(NAN);
    return bits & 0x8000 ? 0 : 0x3c00;
}

std::uint32_t packPair(std::uint16_t left, std::uint16_t right) {
    return clampHalf(left) | (std::uint32_t(clampHalf(right)) << 16);
}

void packUnitExposure(const Sensor& s, const Geometry& g, const std::uint16_t* pixels,
                      std::uint32_t* dst) {
    for (int y = 0; y < g.rawHalfHeight + g.shiftY; ++y) {
        const int sy = 2 * y - g.shiftY;
        const auto* top = pixels + std::size_t(std::max(sy, 0)) * s.width;
        const auto* bottom = pixels + std::size_t(std::min(sy + 1, s.height - 1)) * s.width;
        if (g.shiftX) {
            *dst++ = packPair(top[0], top[0]);
            *dst++ = packPair(bottom[0], bottom[0]);
        }
        for (int x = g.shiftX; x < s.width - g.shiftX; x += 2) {
            *dst++ = packPair(top[x], top[x + 1]);
            *dst++ = packPair(bottom[x], bottom[x + 1]);
        }
        if (g.shiftX) {
            *dst++ = packPair(top[s.width - 1], top[s.width - 1]);
            *dst++ = packPair(bottom[s.width - 1], bottom[s.width - 1]);
        }
    }
}

#if defined(__aarch64__)
float32x4_t expandNormalizedHalf(uint16x4_t h) {
    const auto bits = vmovl_u16(h);
    const auto normals = vaddq_u32(vshlq_n_u32(bits, 13), vdupq_n_u32(112u << 23));
    // Integer expansion preserves subnormal input even with FP16 flush-to-zero enabled.
    return vbslq_f32(vcltq_u32(bits, vdupq_n_u32(1024)), vcvtq_n_f32_u32(bits, 24),
                     vreinterpretq_f32_u32(normals));
}

bool fourLuma(const std::uint32_t* quads, float* out) {
    const auto rows = vld2q_u32(quads);
    const auto largest = vmaxq_u16(vreinterpretq_u16_u32(rows.val[0]), vreinterpretq_u16_u32(rows.val[1]));
    if (vmaxvq_u16(largest) > 0x3c00) return false;
    auto sum = vaddq_f32(expandNormalizedHalf(vmovn_u32(rows.val[0])),
                         expandNormalizedHalf(vshrn_n_u32(rows.val[0], 16)));
    sum = vaddq_f32(sum, expandNormalizedHalf(vmovn_u32(rows.val[1])));
    sum = vaddq_f32(sum, expandNormalizedHalf(vshrn_n_u32(rows.val[1], 16)));
    auto bits = vreinterpretq_u32_f32(vsqrtq_f32(vmulq_n_f32(sum, 0.25f)));
    // sqrt(minimum positive half / 4) = 2^-13. All nonzero results are normal
    // halves, so rounding away 13 mantissa bits gives the exact FP16 round trip.
    auto odd = vandq_u32(vshrq_n_u32(bits, 13), vdupq_n_u32(1));
    bits = vaddq_u32(vaddq_u32(bits, odd), vdupq_n_u32(0xfff));
    vst1q_f32(out, vreinterpretq_f32_u32(vbicq_u32(bits, vdupq_n_u32(0x1fff))));
    return true;
}
#endif

std::uint16_t lane(const PackedQuads& image, std::size_t pixel, int c) {
    return std::uint16_t(image[2 * pixel + c / 2] >> (16 * (c % 2)));
}
void validatePacked(const Geometry& g, const PackedQuads& image) {
    if (g.rawHalfWidth <= 0 || g.rawHalfHeight <= 0 || g.shiftX < 0 || g.shiftX > 1 || g.shiftY < 0 ||
        g.shiftY > 1 ||
        (std::uint64_t(g.rawHalfWidth) + g.shiftX) * (std::uint64_t(g.rawHalfHeight) + g.shiftY) * 2 !=
            image.size())
        throw std::invalid_argument("Packed CFA extent mismatch");
}
} // namespace

Geometry geometryFor(const Sensor& s) {
    if (s.cfa < 0 || s.cfa > 3 || std::int64_t(s.width) * s.height > INT32_MAX || s.width <= 0 ||
        s.height <= 0 || (s.width % 2) || (s.height % 2))
        throw std::invalid_argument("Expected positive even Bayer dimensions");
    int x = s.cfa == 1, y = s.cfa == 2;
    int w = s.width / 2 + x, h = s.height / 2 + y;
    return {s.width / 2, s.height / 2, x, y, 2 * ((w + 3) / 4), (h + 1) / 2};
}

std::vector<std::uint16_t> unpackSensorBits(const Sensor& s, const std::uint8_t* data, std::size_t bytes,
                                            unsigned bits) {
    geometryFor(s);
    const auto count = std::size_t(s.width) * s.height;
    if (bits < 1 || bits > 16 || !data || bytes < (std::uint64_t(count) * bits + 7) / 8)
        throw std::invalid_argument("Invalid packed RAW plane");
    std::vector<std::uint16_t> output(count);
    photon::unpackSensorSamples(data, output.data(), int(count), bits);
    return output;
}

void normalizeBayer(const Sensor& s, std::vector<std::uint16_t>& pixels, SampleFormat format) {
    geometryFor(s);
    if (std::uint64_t(s.width) * s.height != pixels.size())
        throw std::invalid_argument("Incorrect RAW plane size");
    if (format == SampleFormat::NormalizedF16) {
        for (auto bits : pixels)
            if (bits > 0x3c00 && bits != 0x8000)
                throw std::invalid_argument("Expected normalized half-float samples in [0,1]");
        return;
    }
    photon::normalizeRawPlane(pixels.data(), pixels.data(), s.width, s.height, s.white, s.black.data());
}

namespace {
Geometry frameGeometry(const Sensor& s, const std::vector<std::uint16_t>& pixels, float exposure) {
    auto g = geometryFor(s);
    if (std::uint64_t(s.width) * s.height != pixels.size() || !std::isfinite(exposure) || exposure <= 0)
        throw std::invalid_argument("Invalid sensor frame");
    return g;
}

void writeQuads(const Sensor& s, const Geometry& g, const std::vector<std::uint16_t>& pixels,
                SampleFormat format, float exposure, std::uint32_t* packed) {
    const int w = g.rawHalfWidth + g.shiftX, h = g.rawHalfHeight + g.shiftY;
    if (format == SampleFormat::NormalizedF16 && exposure == 1) {
        packUnitExposure(s, g, pixels.data(), packed);
        return;
    }
    std::array<float, 4> inv{};
    for (int c = 0; c < 4; ++c)
        inv[c] = 1 / std::max(s.white - s.black[c], 1.f);
    for (int y = 0; y < h; ++y)
        for (int x = 0; x < w; ++x) {
            for (int c = 0; c < 4; ++c) {
                int sx = std::clamp(2 * x - g.shiftX + (c & 1), 0, s.width - 1);
                int sy = std::clamp(2 * y - g.shiftY + c / 2, 0, s.height - 1);
                int site = 2 * (sy % 2) + (sx % 2);
                auto bits = pixels[std::size_t(sy) * s.width + sx];
                if (format == SampleFormat::SensorU16)
                    bits = f32ToF16(std::clamp((bits - s.black[site]) * inv[site], 0.f, 1.f));
                auto value = exposure == 1 && bits <= 0x3c00
                                 ? bits
                                 : f32ToF16(std::clamp(halfValue(bits), 0.f, 1.f) * exposure);
                auto& word = packed[(std::size_t(y) * w + x) * 2 + c / 2];
                if ((c & 1) == 0)
                    word = value;
                else
                    word |= std::uint32_t(value) << 16;
            }
        }
}

} // namespace

PackedQuads packBayer(const Sensor& s, const std::vector<std::uint16_t>& pixels, SampleFormat format,
                      float exposure) {
    auto g = frameGeometry(s, pixels, exposure);
    PackedQuads packed(std::size_t(g.rawHalfWidth + g.shiftX) * (g.rawHalfHeight + g.shiftY) * 2);
    writeQuads(s, g, pixels, format, exposure, packed.data());
    return packed;
}

void packBayerInto(const Sensor& s, const std::vector<std::uint16_t>& pixels, SampleFormat format,
                   float exposure, std::uint32_t* packed, std::size_t words) {
    auto g = frameGeometry(s, pixels, exposure);
    if (!packed || words != std::size_t(g.rawHalfWidth + g.shiftX) * (g.rawHalfHeight + g.shiftY) * 2)
        throw std::invalid_argument("Invalid Bayer packing destination");
    writeQuads(s, g, pixels, format, exposure, packed);
}

std::vector<std::uint16_t> unpackBayer(const Geometry& g, const PackedQuads& image) {
    validatePacked(g, image);
    int w = g.rawHalfWidth * 2, h = g.rawHalfHeight * 2;
    std::vector<std::uint16_t> out(std::size_t(w) * h);
    for (int y = 0; y < h; ++y)
        for (int x = 0; x < w; ++x) {
            int sx = x + g.shiftX, sy = y + g.shiftY;
            auto q = std::size_t(sy / 2) * (g.rawHalfWidth + g.shiftX) + sx / 2;
            auto bits = lane(image, q, 2 * (sy % 2) + (sx % 2));
            out[std::size_t(y) * w + x] = clampHalf(bits);
        }
    return out;
}

std::vector<std::uint16_t> prewarpBayer(const Sensor& sensor, const std::vector<std::uint16_t>& in,
                                        const std::array<float, 9>& m) {
    auto g = geometryFor(sensor);
    if (std::uint64_t(sensor.width) * sensor.height != in.size())
        throw std::invalid_argument("Invalid Bayer prewarp input");
    std::vector<std::uint16_t> out(in.size());
    for (int y = 0; y < sensor.height; ++y)
        for (int x = 0; x < sensor.width; ++x) {
            float z = m[2] * x + m[5] * y + m[8];
            float u = ((m[0] * x + m[3] * y + m[6]) / z - (x & 1)) * .5f;
            float v = ((m[1] * x + m[4] * y + m[7]) / z - (y & 1)) * .5f;
            if (!std::isfinite(u) || !std::isfinite(v))
                throw std::invalid_argument("Invalid gyro projection");
            u = std::clamp(u, 0.f, float(g.rawHalfWidth - 1));
            v = std::clamp(v, 0.f, float(g.rawHalfHeight - 1));
            int ix = int(u), iy = int(v);
            auto row = [&](int sy) {
                auto offset = std::size_t(2 * sy + (y & 1)) * sensor.width + (x & 1);
                float a = halfValue(in[offset + 2 * ix]),
                      b = halfValue(in[offset + 2 * std::min(ix + 1, g.rawHalfWidth - 1)]);
                return a + (u - ix) * (b - a);
            };
            float top = row(iy), bottom = row(std::min(iy + 1, g.rawHalfHeight - 1));
            out[std::size_t(y) * sensor.width + x] = f32ToF16(top + (v - iy) * (bottom - top));
        }
    return out;
}

std::vector<float> kernelLuma(const Geometry& g, const PackedQuads& image) {
    validatePacked(g, image);
    int w = g.rawHalfWidth + g.shiftX, h = g.rawHalfHeight + g.shiftY, pitch = kernelLumaWidth(g);
    std::vector<float> out(std::size_t(pitch) * h);
    for (int y = 0; y < h; ++y) {
        const auto* row = image.data() + std::size_t(y) * w * 2;
        auto* dst = out.data() + std::size_t(y) * pitch;
        int x = 0;
        while (x < w) {
#if defined(__aarch64__)
            if (x + 4 <= w && fourLuma(row + 2 * x, dst + x)) {
                x += 4;
                continue;
            }
#endif
            float sum = 0;
            const auto* quad = row + 2 * x;
            for (int c = 0; c < 4; ++c)
                sum += halfValue(std::uint16_t(quad[c / 2] >> (16 * (c % 2))));
            dst[x++] = halfValue(f32ToF16(std::sqrt(sum * 0.25f)));
        }
        std::fill(dst + w, dst + pitch, dst[w - 1]);
    }
    return out;
}
} // namespace photon::esd4d
