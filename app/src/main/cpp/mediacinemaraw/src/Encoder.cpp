#include <MediaCinemaRAW/Encoder.h>
// SPDX-License-Identifier: GPL-3.0-only
#include <algorithm>
#include <stdexcept>
#include <cstring>
#if defined(__ARM_NEON)
#include <arm_neon.h>
#endif

namespace mediacinemaraw {
namespace {
void u32(std::vector<uint8_t>& out, uint32_t n) {
    for (int i = 0; i < 4; ++i) out.push_back(n >> (8*i));
}
void patch(std::vector<uint8_t>& out, size_t pos, uint32_t n) {
    for (int i = 0; i < 4; ++i) out[pos+i] = n >> (8*i);
}
// The decoder uses eight interleaved lanes, not a conventional contiguous bitstream.
void pack(std::vector<uint8_t>& out, const uint16_t* p, int bits) {
    if (!bits) return;
    const size_t offset = out.size();
    out.resize(offset + (bits == 16 ? 128 : bits*8));
    uint8_t* dst = out.data()+offset;
    if (bits == 16) {
        // Android targets are little-endian, matching the codec's literal representation.
        std::memcpy(dst,p,128);
    } else if (bits == 8) {
#if defined(__ARM_NEON)
        for (int i = 0; i < 64; i += 8)
            vst1_u8(dst+i,vmovn_u16(vld1q_u16(p+i)));
#else
        for (int i = 0; i < 64; ++i) dst[i] = p[i];
#endif
    } else if (bits == 3 || bits == 5 || bits == 6) {
        uint8_t* packed = dst;
        for (int lane = 0; lane < 8; ++lane) {
            uint16_t v[8];
            for (int g = 0; g < 8; ++g) v[g] = p[g*8+lane];
            if (bits == 3) {
                packed[lane] = v[0] | (v[1]<<3) | ((v[2]&3)<<6);
                packed[8+lane] = v[3] | (v[4]<<3) | ((v[5]&3)<<6);
                packed[16+lane] = v[6] | (v[7]<<3) | ((v[2]>>2)<<6) | ((v[5]>>2)<<7);
            } else if (bits == 5) {
                packed[lane] = v[0] | ((v[5]&7)<<5);
                packed[8+lane] = v[1] | ((v[6]&7)<<5);
                packed[16+lane] = v[2] | ((v[7]&7)<<5);
                packed[24+lane] = v[3] | ((v[5]>>3)<<5) | (((v[7]>>3)&1)<<7);
                packed[32+lane] = v[4] | ((v[6]>>3)<<5) | ((v[7]>>4)<<7);
            } else {
                for (int g = 0; g < 6; ++g)
                    packed[g*8+lane] = v[g] | (((v[6+g/3]>>(2*(g%3)))&3)<<6);
            }
        }

    } else if (bits == 10) {
        for (int half = 0; half < 2; ++half) {
#if defined(__ARM_NEON)
            for (int i = 0; i < 32; i += 8)
                vst1_u8(dst+half*40+i,vmovn_u16(vld1q_u16(p+half*32+i)));
#else
            for (int i = 0; i < 32; ++i) dst[half*40+i] = p[half*32+i];
#endif
            for (int lane = 0; lane < 8; ++lane) {
                uint8_t hi = 0;
                for (int group = 0; group < 4; ++group)
                    hi |= (p[half*32+group*8+lane] >> 8) << (group*2);
                dst[half*40+32+lane] = hi;
            }
        }
    } else {
        const int groups = 8 / bits;
        for (int base = 0; base < 64; base += groups*8)
            for (int lane = 0; lane < 8; ++lane) {
                uint8_t v = 0;
                for (int g = 0; g < groups; ++g) v |= p[base+g*8+lane] << (g*bits);
                *dst++ = v;
            }
    }
}
int bitWidth(uint16_t delta) {
    if (!delta) return 0;
    for (int b : {1, 2, 3, 4, 5, 6, 8, 10}) if (delta < (1u << b)) return b;
    return 16;
}
void metadata(std::vector<uint8_t>& out, std::vector<uint16_t>& values) {
    values.resize((values.size()+63)/64*64, 0);
    u32(out, static_cast<uint32_t>(values.size()));
    for (size_t i = 0; i < values.size(); i += 64) {
        uint16_t ref = std::min<uint16_t>(*std::min_element(values.begin()+i, values.begin()+i+64), 4095);
        uint16_t p[64];
        uint16_t max = 0;
        for (int j = 0; j < 64; ++j) { p[j] = values[i+j]-ref; max = std::max(max, p[j]); }
        int bits = bitWidth(max);
        // Header's four-bit field represents the 16-bit literal mode as 15.
        out.push_back(((bits == 16 ? 15 : bits) << 4) | (ref >> 8));
        out.push_back(ref);
        pack(out, p, bits);
    }
}
}
void encode(const uint8_t* raw, size_t size, int width, int height, int stride,
            bool raw10, int cropTop, int cropHeight, bool bin, std::vector<uint8_t>& out) {
    if (!raw || width <= 0 || height <= 0 || width > 65536 || height > 65536
        || (width & 1) || (raw10 && width % 4) || cropTop < 0 || (cropTop & 1)
        || cropHeight <= 0 || cropTop > height-cropHeight)
        throw std::invalid_argument("Invalid RAW geometry");
    const size_t rowBytes = raw10 ? size_t(width)/4*5 : size_t(width)*2;
    if (stride < 0 || size_t(stride) < rowBytes || size < size_t(height-1)*stride+rowBytes)
        throw std::invalid_argument("Truncated RAW plane or invalid stride");
    const int w = bin ? width/2 : width;
    const int h = bin ? cropHeight/2 : cropHeight;
    if ((w & 1) || h % 4 || (bin && width % 4))
        throw std::invalid_argument("MediaCinemaRAW requires even width and height divisible by four");
    const int ew = (w+63)/64*64;
    auto sample = [&](int x, int y) -> uint16_t {
        const uint8_t* row = raw + size_t(y+cropTop)*stride;
        if (raw10) return (uint16_t(row[x/4*5+x%4]) << 2) | ((row[x/4*5+4] >> (2*(x%4))) & 3);
        return uint16_t(row[x*2]) | (uint16_t(row[x*2+1]) << 8);
    };
    auto pixel = [&](int x, int y) -> uint16_t {
        // Padding is outside the visible width and is discarded by the decoder.
        if (x >= w) return 0;
        if (!bin) return sample(x,y);
        int sx = (x/2)*4+x%2, sy = (y/2)*4+y%2;
        // Average four same-colour samples; retain the original black/white levels.
        return (uint32_t(sample(sx,sy))+sample(sx+2,sy)+sample(sx,sy+2)+sample(sx+2,sy+2)+2)/4;
    };
    out.clear();
    out.reserve(size_t(ew)*h*2 + size_t(ew)*h/8 + 1024);
    u32(out, ew); u32(out, h); u32(out, 0); u32(out, 0);
    std::vector<uint16_t> bits, refs;
    bits.reserve(size_t(ew)*h/64); refs.reserve(size_t(ew)*h/64);
    for (int y = 0; y < h; y += 4) for (int x = 0; x < ew; x += 64) {
        uint16_t channels[4][64];
        if (!raw10 && !bin && x+64 <= w) {
            // Deinterleave contiguous Bayer rows once, without per-pixel format/crop branches.
            for (int row = 0; row < 4; ++row) {
                const uint8_t* src = raw+size_t(y+row+cropTop)*stride+x*2;
                uint16_t* even = channels[(row%2)*2]+(row/2)*32;
                uint16_t* odd = channels[(row%2)*2+1]+(row/2)*32;
#if defined(__ARM_NEON)
                for (int i = 0; i < 32; i += 8) {
                    uint16x8x2_t v = vld2q_u16(reinterpret_cast<const uint16_t*>(src+i*4));
                    vst1q_u16(even+i,v.val[0]); vst1q_u16(odd+i,v.val[1]);
                }
#else
                for (int i = 0; i < 32; ++i) {
                    even[i] = uint16_t(src[i*4]) | (uint16_t(src[i*4+1])<<8);
                    odd[i] = uint16_t(src[i*4+2]) | (uint16_t(src[i*4+3])<<8);
                }
#endif
            }
        } else {
            for (int c = 0; c < 4; ++c) for (int i = 0; i < 64; ++i)
                channels[c][i] = pixel(x+(i%32)*2+c%2, y+(i/32)*2+c/2);
        }
        for (int c = 0; c < 4; ++c) {
            uint16_t* p = channels[c];
            uint16_t lo = 65535, hi = 0;
#if defined(__ARM_NEON)
            uint16x8_t vlo = vdupq_n_u16(65535), vhi = vdupq_n_u16(0);
            for (int i = 0; i < 64; i += 8) {
                uint16x8_t v = vld1q_u16(p+i);
                vlo = vminq_u16(vlo,v); vhi = vmaxq_u16(vhi,v);
            }
            uint16_t mins[8], maxs[8];
            vst1q_u16(mins,vlo); vst1q_u16(maxs,vhi);
            for (int i = 0; i < 8; ++i) {
                lo = std::min(lo,mins[i]); hi = std::max(hi,maxs[i]);
            }
#else
            for (int i = 0; i < 64; ++i) {
                lo = std::min(lo,p[i]); hi = std::max(hi,p[i]);
            }
#endif
            int b = bitWidth(hi-lo);
            bits.push_back(b); refs.push_back(lo);
#if defined(__ARM_NEON)
            uint16x8_t reference = vdupq_n_u16(lo);
            for (int i = 0; i < 64; i += 8)
                vst1q_u16(p+i,vsubq_u16(vld1q_u16(p+i),reference));
#else
            for (int i = 0; i < 64; ++i) p[i] -= lo;
#endif
            pack(out,p,b);
        }
    }
    patch(out,8,static_cast<uint32_t>(out.size())); metadata(out,bits);
    patch(out,12,static_cast<uint32_t>(out.size())); metadata(out,refs);
}
}
