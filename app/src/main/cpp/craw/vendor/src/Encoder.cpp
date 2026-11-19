// SPDX-License-Identifier: Apache-2.0
#include <craw/Encoder.h>

#include "Crc32c.h"

#include <algorithm>
#include <atomic>
#include <bit>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <thread>

#if defined(__ARM_NEON) && defined(__aarch64__)
#include <arm_neon.h>
#endif
#ifdef CRAW_PROFILE
#include <chrono>
#endif

namespace craw {
namespace {

constexpr int kRiceLimit = 24;
constexpr int kEscapeBits = 24;

// (G1, G2, R, B) positions in the 2x2 cell, index = py * 2 + px.
constexpr int kLayout[4][4] = {{1, 2, 0, 3}, {0, 3, 1, 2}, {0, 3, 2, 1}, {1, 2, 3, 0}};

// MSB-first bit writer into a buffer pre-sized for the worst case, so the hot
// loop never checks capacity. Flushes 32 bits at a time.
class BitWriter {
public:
    explicit BitWriter(uint8_t* out) : p_(out), begin_(out) {}

    void put(uint32_t value, int bits) {  // bits <= 32, value < 2^bits
        acc_ = (acc_ << bits) | value;
        n_ += bits;
        // Branchless flush: always store the top 32 pending bits, advance only
        // when 32 are complete (the buffer has slack for the extra store).
        const int full = n_ >= 32;
        const uint32_t w = static_cast<uint32_t>(acc_ >> (n_ - 32 * full));
        const uint8_t be[4] = {uint8_t(w >> 24), uint8_t(w >> 16), uint8_t(w >> 8), uint8_t(w)};
        std::memcpy(p_, be, 4);
        p_ += 4 * full;
        n_ -= 32 * full;
    }

    size_t finish() {
        while (n_ >= 8) { n_ -= 8; *p_++ = static_cast<uint8_t>(acc_ >> n_); }
        if (n_) *p_++ = static_cast<uint8_t>(acc_ << (8 - n_));
        n_ = 0;
        return static_cast<size_t>(p_ - begin_);
    }

private:
    uint8_t* p_;
    uint8_t* begin_;
    uint64_t acc_ = 0;
    int n_ = 0;
};

void putU16(std::vector<uint8_t>& o, uint16_t v) { o.push_back(v & 0xFF); o.push_back(v >> 8); }
void putU32(std::vector<uint8_t>& o, uint32_t v) {
    for (int i = 0; i < 4; ++i) o.push_back(static_cast<uint8_t>(v >> (8 * i)));
}

constexpr int kBlock = 16;
constexpr int kMaxBlockK = 16;

inline int32_t med(int32_t a, int32_t b, int32_t c) {
    const int32_t mx = std::max(a, b), mn = std::min(a, b);
    return c >= mx ? mn : c <= mn ? mx : a + b - c;
}

inline uint32_t zigzag(int32_t e) { return e >= 0 ? 2u * uint32_t(e) : 2u * uint32_t(-e) - 1u; }

// SPEC 3.4: Rice parameter from the causal block sums (`up` < 0 = absent).
inline int blockK(int64_t up, int64_t left, uint32_t riceInit) {
    int64_t s;
    if (up >= 0 && left >= 0) s = up + left;
    else if (up >= 0) s = 2 * up;
    else if (left >= 0) s = 2 * left;
    else s = int64_t{riceInit} * 2 * kBlock;
    const uint64_t v = (static_cast<uint64_t>(s) * 11) >> 4;
    return std::min(static_cast<int>(std::bit_width((v >> 5) + 1)) - 1, kMaxBlockK);
}

inline void putCode(BitWriter& bw, uint32_t u, int k) {
    const uint32_t q = u >> k;
    if (q < kRiceLimit) [[likely]] {
        if (q + 1 + k <= 32) {
            bw.put((1u << k) | (u & ((1u << k) - 1)), static_cast<int>(q) + 1 + k);
        } else {
            bw.put(1, static_cast<int>(q) + 1);
            bw.put(u & ((1u << k) - 1), k);
        }
    } else {
        bw.put(1, kRiceLimit + 1);
        bw.put(u & ((1u << kEscapeBits) - 1), kEscapeBits);
    }
}

// Codes `n` samples sharing Rice parameter k. Common case: two code words
// with q < 24 fit together in one 32-bit put, halving the bit-writer chain.
inline void putBlock(BitWriter& bw, const uint32_t* u, int n, int k) {
    const uint32_t lowMask = (1u << k) - 1, mark = 1u << k;
    int x = 0;
    for (; x + 4 <= n; x += 4) {
        const uint32_t q0 = u[x] >> k, q1 = u[x + 1] >> k, q2 = u[x + 2] >> k, q3 = u[x + 3] >> k;
        const uint32_t total = q0 + q1 + q2 + q3 + 4 * (1 + k);
        if ((q0 | q1 | q2 | q3) < kRiceLimit && total <= 32) [[likely]] {
            const uint32_t l1 = q1 + 1 + k, l2 = q2 + 1 + k, l3 = q3 + 1 + k;
            uint64_t c = mark | (u[x] & lowMask);
            c = (c << l1) | mark | (u[x + 1] & lowMask);
            c = (c << l2) | mark | (u[x + 2] & lowMask);
            c = (c << l3) | mark | (u[x + 3] & lowMask);
            bw.put(static_cast<uint32_t>(c), static_cast<int>(total));
            continue;
        }
        for (int i = 0; i < 4; i += 2) {
            const uint32_t a = u[x + i] >> k, b = u[x + i + 1] >> k;
            const uint32_t la = a + 1 + k, lb = b + 1 + k;
            if (a < kRiceLimit && b < kRiceLimit && la + lb <= 32) {
                bw.put(((mark | (u[x + i] & lowMask)) << lb) | mark | (u[x + i + 1] & lowMask),
                       static_cast<int>(la + lb));
            } else {
                putCode(bw, u[x + i], k);
                putCode(bw, u[x + i + 1], k);
            }
        }
    }
    for (; x + 2 <= n; x += 2) {
        const uint32_t q0 = u[x] >> k, q1 = u[x + 1] >> k;
        const uint32_t len0 = q0 + 1 + k, len1 = q1 + 1 + k;
        if (q0 < kRiceLimit && q1 < kRiceLimit && len0 + len1 <= 32) [[likely]] {
            const uint32_t c0 = mark | (u[x] & lowMask), c1 = mark | (u[x + 1] & lowMask);
            bw.put(len1 == 32 ? c1 : (c0 << len1) | c1, static_cast<int>(len0 + len1));
        } else {
            putCode(bw, u[x], k);
            putCode(bw, u[x + 1], k);
        }
    }
    if (x < n) putCode(bw, u[x], k);
}

// Zigzag MED residuals of one row (SPEC 3.4); no coding state, so the interior vectorises.
inline void rowResiduals(const int32_t* row, const int32_t* up, int y, int w, int32_t firstPred, uint32_t* u) {
        if (y == 0) {
            u[0] = zigzag(row[0] - firstPred);
            for (int x = 1; x < w; ++x) u[x] = zigzag(row[x] - row[x - 1]);
        } else {
            u[0] = zigzag(row[0] - up[0]);
            int x = 1;
#if defined(__ARM_NEON) && defined(__aarch64__)
            for (; x + 4 <= w; x += 4) {
                const int32x4_t a = vld1q_s32(row + x - 1), b = vld1q_s32(up + x), c = vld1q_s32(up + x - 1);
                const int32x4_t mx = vmaxq_s32(a, b), mn = vminq_s32(a, b);
                const int32x4_t grad = vsubq_s32(vaddq_s32(a, b), c);
                const int32x4_t pred = vbslq_s32(vcgeq_s32(c, mx), mn, vbslq_s32(vcleq_s32(c, mn), mx, grad));
                const int32x4_t e = vsubq_s32(vld1q_s32(row + x), pred);
                vst1q_u32(u + x, vreinterpretq_u32_s32(veorq_s32(vshlq_n_s32(e, 1), vshrq_n_s32(e, 31))));
            }
#endif
            for (; x < w; ++x) u[x] = zigzag(row[x] - med(row[x - 1], up[x], up[x - 1]));
        }
}

// `u` receives the zigzag residuals of the plane, `sums` the block sums of
// the previous and current row.
void encodePlane(BitWriter& bw, const int32_t* p, int w, int h, int32_t firstPred,
                 uint32_t riceInit, std::vector<uint32_t>& u, std::vector<int64_t>& sums) {
    const int blocks = (w + kBlock - 1) / kBlock;
    u.resize(w);
    sums.assign(2 * blocks, -1);
    int64_t* prevSums = sums.data();
    int64_t* curSums = sums.data() + blocks;
    for (int y = 0; y < h; ++y) {
        const int32_t* row = p + y * w;
        const int32_t* up = row - w;
        rowResiduals(row, up, y, w, firstPred, u.data());
        for (int bx = 0; bx < blocks; ++bx) {
            const int x0 = bx * kBlock, n = std::min(kBlock, w - x0);
            int64_t s = 0;
            int x = x0;
#if defined(__ARM_NEON) && defined(__aarch64__)
            if (n == kBlock) {
                const uint32_t* q = u.data() + x0;
                uint64x2_t acc = vpaddlq_u32(vld1q_u32(q));
                acc = vpadalq_u32(acc, vld1q_u32(q + 4));
                acc = vpadalq_u32(acc, vld1q_u32(q + 8));
                acc = vpadalq_u32(acc, vld1q_u32(q + 12));
                s = static_cast<int64_t>(vaddvq_u64(acc));
                x = x0 + n;
            }
#endif
            for (; x < x0 + n; ++x) s += u[x];
            curSums[bx] = s;
        }
        for (int bx = 0; bx < blocks; ++bx) {
            const int x0 = bx * kBlock, n = std::min(kBlock, w - x0);
            const int64_t upS = y > 0 ? prevSums[bx] * kBlock / n : -1;
            const int k = blockK(upS, bx > 0 ? curSums[bx - 1] : -1, riceInit);
#ifdef CRAW_PROFILE_SKIP_EMIT
            bw.put(k & 1, 1);
#else
            putBlock(bw, u.data() + x0, n, k);
#endif
        }
        std::swap(prevSums, curSums);
    }
}

struct TileJob {
    const uint16_t* raw;
    size_t stride;
    const FrameDesc* d;
    uint32_t riceInit;
};

// Appends up to 64 bits LSB-first; flushes whole 32-bit words.
// Bytes a vectorized block store may write past the coded data.
constexpr size_t kPackSlack = 16;

struct LiveBits {
    uint8_t* out;
    uint64_t acc = 0;
    int bits = 0;
    inline void put(uint64_t v, int n) {  // n <= 64
        if (n == 0) return;
        if (bits + n > 64) {  // split: low part first
            const int lo = 32;
            put(v & 0xFFFFFFFFull, lo);
            put(v >> lo, n - lo);
            return;
        }
        acc |= bits < 64 ? v << bits : 0;
        bits += n;
        while (bits >= 32) {
            const uint32_t w = static_cast<uint32_t>(acc);
            std::memcpy(out, &w, 4);  // little-endian targets
            out += 4;
            acc = bits > 32 ? acc >> 32 : 0;
            bits -= 32;
        }
    }
    inline void align() {
        for (; bits > 0; bits -= 8) {
            *out++ = static_cast<uint8_t>(acc);
            acc >>= 8;
        }
        bits = 0;
        acc = 0;
    }
};

// Packs one block of n zigzag residuals at width w (SPEC 3.6) and byte-aligns.
inline void packBlock(LiveBits& lb, const uint16_t* q, uint32_t n, int w) {
#if defined(__ARM_NEON) && defined(__aarch64__)
    if (n == 16 && w <= 16) {
        // Pairwise lane merge: 16 x w -> 8 x 2w (u32) -> 4 x 4w (u64), then 4 puts.
        const uint16x8_t a = vld1q_u16(q), b = vld1q_u16(q + 8);
        const uint32x4_t sh = vdupq_n_u32(static_cast<uint32_t>(w));
        // even/odd samples
        const uint16x8_t ev = vuzp1q_u16(a, b), od = vuzp2q_u16(a, b);
        const uint32x4_t p0 = vorrq_u32(vmovl_u16(vget_low_u16(ev)), vshlq_u32(vmovl_u16(vget_low_u16(od)), vreinterpretq_s32_u32(sh)));
        const uint32x4_t p1 = vorrq_u32(vmovl_high_u16(ev), vshlq_u32(vmovl_high_u16(od), vreinterpretq_s32_u32(sh)));
        // p0 = pairs (0,1),(2,3),(4,5),(6,7); p1 = pairs (8,9)...(14,15)
        const uint32x4_t e2 = vuzp1q_u32(p0, p1), o2 = vuzp2q_u32(p0, p1);
        // e2 = pairs 0,2,4,6 ; o2 = pairs 1,3,5,7 (each 2w bits)
        const int64x2_t sh2 = vdupq_n_s64(2 * w);
        const uint64x2_t q0 = vorrq_u64(vmovl_u32(vget_low_u32(e2)), vshlq_u64(vmovl_u32(vget_low_u32(o2)), sh2));
        const uint64x2_t q1 = vorrq_u64(vmovl_high_u32(e2), vshlq_u64(vmovl_high_u32(o2), sh2));
        // A full block is exactly 2w bytes, and each half (8 samples, 8w bits)
        // is exactly w bytes: build the halves as 128-bit values and store
        // them directly. The second 16-byte store may run past the block;
        // the next block overwrites that, and tile buffers keep kPackSlack.
        using u128 = unsigned __int128;
        const int n4 = 4 * w;  // 4w <= 64
        const u128 h0 = u128{vgetq_lane_u64(q0, 0)} | (u128{vgetq_lane_u64(q0, 1)} << n4);
        const u128 h1 = u128{vgetq_lane_u64(q1, 0)} | (u128{vgetq_lane_u64(q1, 1)} << n4);
        std::memcpy(lb.out, &h0, 16);  // little-endian targets (NEON path)
        std::memcpy(lb.out + w, &h1, 16);
        lb.out += 2 * w;
        return;
    }
#endif
    for (uint32_t i = 0; i < n; ++i) lb.put(q[i], w);
    lb.align();
}

// Width nibbles written straight into bytes (low nibble first).
struct NibbleWriter {
    uint8_t* p;
    bool half = false;
    inline void put(uint8_t v) {
        if (!half) *p = v;
        else *p++ |= static_cast<uint8_t>(v << 4);
        half = !half;
    }
    inline void width(int bw) {
        if (bw < 15) put(static_cast<uint8_t>(bw));
        else { put(15); put(static_cast<uint8_t>(bw - 15)); }
    }
    size_t finish(uint8_t* begin) { if (half) { ++p; half = false; } return static_cast<size_t>(p - begin); }
};

// Encodes one plane row's residuals as blocks (width nibble + packed samples).
inline void liveRow(LiveBits& lb, NibbleWriter& nw, const uint16_t* u, uint32_t tw) {
    for (uint32_t x0 = 0; x0 < tw; x0 += kBlock) {
        const uint32_t n = std::min<uint32_t>(kBlock, tw - x0);
        const uint16_t* q = u + x0;
        uint32_t mx = 0;
        uint32_t i = 0;
#if defined(__ARM_NEON) && defined(__aarch64__)
        if (n == kBlock) { mx = vmaxvq_u16(vmaxq_u16(vld1q_u16(q), vld1q_u16(q + 8))); i = n; }
#endif
        for (; i < n; ++i) mx = std::max<uint32_t>(mx, q[i]);
        const int bw = static_cast<int>(std::bit_width(mx));
        nw.width(bw);
        if (bw) packBlock(lb, q, n, bw);
    }
}

// CRAW-Live (SPEC 3.6), one fused pass over the raw rows of a tile. For each
// tile row and cell (py, px): left-neighbour differences within the plane (the
// first sample from the sample above), zigzag, then per 16-sample block a
// width nibble and the samples packed LSB-first, each block byte-aligned.
// Both cells of a raw row come from one deinterleaving load.
size_t encodeTileLive(const TileJob& j, uint32_t tx, uint32_t ty, uint32_t tw, uint32_t th, uint8_t* scratch,
                      std::vector<uint32_t>& u32, uint16_t& maxSample) {
    thread_local std::vector<uint16_t> ubuf;
    ubuf.resize(2 * (size_t{tw} + 16));
    uint16_t* u0 = ubuf.data();
    uint16_t* u1 = ubuf.data() + tw + 16;
    const int32_t first = 1 << (j.d->bitDepth - 1);
    const bool narrow = j.d->bitDepth <= 15;  // zigzag fits 16 bits
    // <=13-bit input needs one width nibble/block, avoiding a tile data move.
    const int widthNibbles = j.d->bitDepth <= 13 ? 1 : 2;
    const size_t maxNib = (size_t{4} * th * ((tw + kBlock - 1) / kBlock) * widthNibbles + 1) / 2;
    uint8_t* const nibBegin = scratch + 5;
    uint8_t* const blocks = scratch + 5 + maxNib;
    LiveBits lb{blocks};
    NibbleWriter nw{nibBegin};
    uint16_t mx = maxSample;
    for (uint32_t y = 0; y < th; ++y) {
        for (int py = 0; py < 2; ++py) {
            const uint16_t* src = j.raw + (2 * (ty + y) + py) * j.stride + 2 * tx;
            const uint16_t* above = src - 2 * j.stride;
            if (narrow) {
                const int32_t p0 = y == 0 ? first : above[0], p1 = y == 0 ? first : above[1];
                u0[0] = static_cast<uint16_t>(zigzag(int32_t{src[0]} - p0));
                u1[0] = static_cast<uint16_t>(zigzag(int32_t{src[1]} - p1));
                mx = std::max({mx, src[0], src[1]});
                uint32_t x = 1;
#if defined(__ARM_NEON) && defined(__aarch64__)
                uint16x8_t vmx = vdupq_n_u16(mx);
                for (; x + 8 <= tw; x += 8) {
                    const uint16x8x2_t cur = vld2q_u16(src + 2 * x);
                    const uint16x8x2_t prv = vld2q_u16(src + 2 * x - 2);
                    vmx = vmaxq_u16(vmx, vmaxq_u16(cur.val[0], cur.val[1]));
                    const int16x8_t d0 = vreinterpretq_s16_u16(vsubq_u16(cur.val[0], prv.val[0]));
                    const int16x8_t d1 = vreinterpretq_s16_u16(vsubq_u16(cur.val[1], prv.val[1]));
                    vst1q_u16(u0 + x, vreinterpretq_u16_s16(veorq_s16(vshlq_n_s16(d0, 1), vshrq_n_s16(d0, 15))));
                    vst1q_u16(u1 + x, vreinterpretq_u16_s16(veorq_s16(vshlq_n_s16(d1, 1), vshrq_n_s16(d1, 15))));
                }
                mx = vmaxvq_u16(vmx);
#endif
                for (; x < tw; ++x) {
                    mx = std::max({mx, src[2 * x], src[2 * x + 1]});
                    u0[x] = static_cast<uint16_t>(zigzag(int32_t{src[2 * x]} - int32_t{src[2 * x - 2]}));
                    u1[x] = static_cast<uint16_t>(zigzag(int32_t{src[2 * x + 1]} - int32_t{src[2 * x - 1]}));
                }
                liveRow(lb, nw, u0, tw);
                liveRow(lb, nw, u1, tw);
            } else {
                // 16-bit samples: residuals need 17 bits; scalar path.
                u32.resize(tw);
                for (int px = 0; px < 2; ++px) {
                    int32_t prev = y == 0 ? first : above[px];
                    uint32_t bmax = 0;
                    for (uint32_t x = 0; x < tw; ++x) {
                        const int32_t v = src[2 * x + px];
                        mx = std::max<uint16_t>(mx, static_cast<uint16_t>(v));
                        u32[x] = zigzag(v - prev);
                        prev = v;
                    }
                    for (uint32_t x0 = 0; x0 < tw; x0 += kBlock) {
                        const uint32_t n = std::min<uint32_t>(kBlock, tw - x0);
                        bmax = 0;
                        for (uint32_t i = 0; i < n; ++i) bmax = std::max(bmax, u32[x0 + i]);
                        const int bw = static_cast<int>(std::bit_width(bmax));
                        nw.width(bw);
                        if (!bw) continue;
                        for (uint32_t i = 0; i < n; ++i) lb.put(u32[x0 + i], bw);
                        lb.align();
                    }
                }
            }
        }
    }
    maxSample = mx;
    const size_t nibBytes = nw.finish(nibBegin);
    scratch[0] = 0;
    scratch[1] = uint8_t(nibBytes); scratch[2] = uint8_t(nibBytes >> 8);
    scratch[3] = uint8_t(nibBytes >> 16); scratch[4] = uint8_t(nibBytes >> 24);
    const size_t dataBytes = static_cast<size_t>(lb.out - blocks);
    if (nibBytes != maxNib) std::memmove(scratch + 5 + nibBytes, blocks, dataBytes);
    return 5 + nibBytes + dataBytes;
}

// ---- codec 3: CRAW-Live v2 (SPEC 3.7) --------------------------------------

constexpr int kPforTag = 14;  // first nibble: 0..13 plain width, 14 PFOR, 15 escape (14 + next)
constexpr int kPforMaxExceptions = 15;

inline void widthNibbles(NibbleWriter& nw, int v) {  // 0..14 direct, else 15 + (v - 15)
    if (v < 15) nw.put(static_cast<uint8_t>(v));
    else { nw.put(15); nw.put(static_cast<uint8_t>(v - 15)); }
}

// Codes one plane row of zigzag residuals as plain or PFOR blocks.
template <typename T>
inline void packAny(LiveBits& lb, const T* q, uint32_t n, int w) {
    if constexpr (sizeof(T) == 2) {
        packBlock(lb, q, n, w);
    } else {
        for (uint32_t i = 0; i < n; ++i) lb.put(q[i], w);
        lb.align();
    }
}

template <typename T>
inline void pforRow(LiveBits& lb, NibbleWriter& nw, const T* u, uint32_t tw, bool pfor) {
    for (uint32_t x0 = 0; x0 < tw; x0 += kBlock) {
        const uint32_t n = std::min<uint32_t>(kBlock, tw - x0);
        const T* q = u + x0;
        // exceptions(k) = number of values >= 2^k.
        uint32_t mx = 0;
#if defined(__ARM_NEON) && defined(__aarch64__)
        const bool vec = sizeof(T) == 2 && n == kBlock;
        uint16x8_t va = vdupq_n_u16(0), vb = vdupq_n_u16(0);
        if constexpr (sizeof(T) == 2) {
            if (vec) {
                va = vld1q_u16(reinterpret_cast<const uint16_t*>(q));
                vb = vld1q_u16(reinterpret_cast<const uint16_t*>(q) + 8);
                mx = vmaxvq_u16(vmaxq_u16(va, vb));
            }
        }
        if (!vec)
#endif
            for (uint32_t i = 0; i < n; ++i) mx = std::max<uint32_t>(mx, q[i]);
        auto countAtLeast = [&](uint32_t t) -> int {
#if defined(__ARM_NEON) && defined(__aarch64__)
            if (vec) {
                const uint16x8_t th = vdupq_n_u16(static_cast<uint16_t>(t));
                const uint16x8_t c = vaddq_u16(vshrq_n_u16(vcgeq_u16(va, th), 15), vshrq_n_u16(vcgeq_u16(vb, th), 15));
                return vaddvq_u16(c);
            }
#endif
            int c = 0;
            for (uint32_t i = 0; i < n; ++i) c += q[i] >= t;
            return c;
        };
        const int bw = static_cast<int>(std::bit_width(mx));
        int bestCost = static_cast<int>(n) * bw + (bw < kPforTag ? 4 : 8), bestK = -1, bestExc = 0;
        for (int k = bw - 1; pfor && k >= std::max(0, bw - 8); --k) {
            const int above = countAtLeast(1u << k);
            if (above > kPforMaxExceptions) break;
            const int hb = bw - k;
            const int cost = static_cast<int>(n) * k + 4 + (k < 15 ? 4 : 8) + 4 + (hb < 15 ? 4 : 8) + above * (4 + hb);
            if (cost < bestCost) { bestCost = cost; bestK = k; bestExc = above; }
        }
        if (bestK < 0) {  // plain block
            if (bw < kPforTag) nw.put(static_cast<uint8_t>(bw));
            else { nw.put(15); nw.put(static_cast<uint8_t>(bw - kPforTag)); }
            if (bw) packAny(lb, q, n, bw);
            continue;
        }
        const int k = bestK, hb = bw - k;
        nw.put(kPforTag);
        widthNibbles(nw, k);
        nw.put(static_cast<uint8_t>(bestExc));
        widthNibbles(nw, hb);
        T low[kBlock];
        uint32_t high[kBlock];
        int ne = 0;
        const uint32_t mask = (1u << k) - 1;
        for (uint32_t i = 0; i < n; ++i) {
            low[i] = static_cast<T>(q[i] & mask);
            if (q[i] >> k) { nw.put(static_cast<uint8_t>(i)); high[ne++] = static_cast<uint32_t>(q[i] >> k); }
        }
        if (k) packAny(lb, low, n, k);
        for (int e = 0; e < ne; ++e) lb.put(high[e], hb);
        lb.align();
    }
}

// One fused pass per tile: 4-neighbour average prediction within each colour
// plane (SPEC 3.7), zigzag, PFOR blocks. Rows of the tile, cells (py, px) in order.
size_t encodeTileLive2(const TileJob& j, uint32_t tx, uint32_t ty, uint32_t tw, uint32_t th, uint8_t* out,
                       uint16_t& maxSample) {
    thread_local std::vector<uint16_t> ubuf;
    ubuf.resize(2 * (size_t{tw} + 16));
    uint16_t* u0 = ubuf.data();
    uint16_t* u1 = ubuf.data() + tw + 16;
    const int32_t first = 1 << (j.d->bitDepth - 1);
    const bool narrow = j.d->bitDepth <= 15;  // 16-bit zigzag residuals
    const size_t maxNib = (size_t{4} * th * ((tw + kBlock - 1) / kBlock) * 24 + 1) / 2;
    uint8_t* const nibBegin = out + 5;
    uint8_t* const blocks = out + 5 + maxNib;
    LiveBits lb{blocks};
    NibbleWriter nw{nibBegin};
    uint16_t mx = maxSample;
    auto zz = [](int32_t e) { return static_cast<uint16_t>(e >= 0 ? 2 * e : -2 * e - 1); };
    for (uint32_t y = 0; y < th; ++y) {
        for (int py = 0; py < 2; ++py) {
            const uint16_t* src = j.raw + (2 * (ty + y) + py) * j.stride + 2 * tx;
            const uint16_t* up = src - 2 * j.stride;
            auto at = [&](const uint16_t* row, uint32_t x, int px) { return int32_t{row[2 * x + px]}; };
            if (!narrow) {
                // 16-bit samples: 17-bit residuals, scalar 32-bit path.
                thread_local std::vector<uint32_t> w32;
                w32.resize(tw);
                auto zz32 = [](int32_t e) { return e >= 0 ? 2u * uint32_t(e) : 2u * uint32_t(-e) - 1u; };
                for (int px = 0; px < 2; ++px) {
                    for (uint32_t x = 0; x < tw; ++x) {
                        int32_t pred;
                        if (y == 0) pred = x == 0 ? first : at(src, x - 1, px);
                        else if (x == 0) pred = at(up, 0, px);
                        else {
                            const int32_t U = at(up, x, px);
                            pred = (at(src, x - 1, px) + U + at(up, x - 1, px) + (x + 1 < tw ? at(up, x + 1, px) : U) + 2) >> 2;
                        }
                        w32[x] = zz32(at(src, x, px) - pred);
                    }
                    pforRow(lb, nw, w32.data(), tw, j.d->pfor);
                }
                for (uint32_t x = 0; x < 2 * tw; ++x) mx = std::max(mx, src[x]);
                continue;
            }
            if (y == 0) {
                for (int px = 0; px < 2; ++px) {
                    uint16_t* u = px ? u1 : u0;
                    u[0] = zz(at(src, 0, px) - first);
                    for (uint32_t x = 1; x < tw; ++x) u[x] = zz(at(src, x, px) - at(src, x - 1, px));
                }
                for (uint32_t x = 0; x < 2 * tw; ++x) mx = std::max(mx, src[x]);
            } else {
                mx = std::max({mx, src[0], src[1]});
                u0[0] = zz(at(src, 0, 0) - at(up, 0, 0));
                u1[0] = zz(at(src, 0, 1) - at(up, 0, 1));
                uint32_t x = 1;
#if defined(__ARM_NEON) && defined(__aarch64__)
                // Interior (x + 1 < tw keeps the up-right neighbour in the tile); one
                // set of deinterleaving loads serves both cells of the raw row.
                const uint32x4_t two = vdupq_n_u32(2);
                auto predict = [&](uint16x8_t L, uint16x8_t U, uint16x8_t UL, uint16x8_t UR) {
                    const uint32x4_t sl = vaddq_u32(vaddq_u32(vaddl_u16(vget_low_u16(L), vget_low_u16(U)),
                                                              vaddl_u16(vget_low_u16(UL), vget_low_u16(UR))), two);
                    const uint32x4_t sh = vaddq_u32(vaddq_u32(vaddl_high_u16(L, U), vaddl_high_u16(UL, UR)), two);
                    return vcombine_u16(vshrn_n_u32(sl, 2), vshrn_n_u32(sh, 2));
                };
                auto zzv = [](uint16x8_t v, uint16x8_t pred) {
                    const int16x8_t e = vreinterpretq_s16_u16(vsubq_u16(v, pred));
                    return vreinterpretq_u16_s16(veorq_s16(vshlq_n_s16(e, 1), vshrq_n_s16(e, 15)));
                };
                uint16x8_t vmx = vdupq_n_u16(mx);
                if (j.d->bitDepth <= 14 && x + 8 < tw) {
                    // Samples < 2^14: L + U + UL + UR fits 16 bits, so predict in
                    // 16-bit lanes ((sum + 2) >> 2 as a rounding shift). Only the
                    // current and up rows are loaded; left, up-left and up-right
                    // come from lane shifts of the neighbouring loads (2 loads per
                    // 8 cells instead of 5). The look-ahead up load may read past
                    // the tile; those lanes are never used.
                    auto pred16 = [](uint16x8_t L, uint16x8_t U, uint16x8_t UL, uint16x8_t UR) {
                        return vrshrq_n_u16(vaddq_u16(vaddq_u16(L, U), vaddq_u16(UL, UR)), 2);
                    };
                    // Lane 7 of the "previous" vectors must hold cell x - 1: load
                    // from cell x - 1 (always inside the row) and broadcast lane 0.
                    uint16x8x2_t prevCur = vld2q_u16(src + 2 * x - 2), prevUp = vld2q_u16(up + 2 * x - 2);
                    for (int c = 0; c < 2; ++c) {
                        prevCur.val[c] = vdupq_laneq_u16(prevCur.val[c], 0);
                        prevUp.val[c] = vdupq_laneq_u16(prevUp.val[c], 0);
                    }
                    uint16x8x2_t uu = vld2q_u16(up + 2 * x);
                    for (; x + 8 < tw; x += 8) {
                        const uint16x8x2_t cur = vld2q_u16(src + 2 * x);
                        const uint16x8x2_t nextUp = vld2q_u16(up + 2 * x + 16);
                        vmx = vmaxq_u16(vmx, vmaxq_u16(cur.val[0], cur.val[1]));
                        for (int c = 0; c < 2; ++c) {
                            const uint16x8_t L = vextq_u16(prevCur.val[c], cur.val[c], 7);
                            const uint16x8_t UL = vextq_u16(prevUp.val[c], uu.val[c], 7);
                            const uint16x8_t UR = vextq_u16(uu.val[c], nextUp.val[c], 1);
                            vst1q_u16((c ? u1 : u0) + x, zzv(cur.val[c], pred16(L, uu.val[c], UL, UR)));
                        }
                        prevCur = cur;
                        prevUp = uu;
                        uu = nextUp;
                    }
                }
                for (; x + 8 < tw; x += 8) {
                    const uint16x8x2_t cur = vld2q_u16(src + 2 * x);
                    vmx = vmaxq_u16(vmx, vmaxq_u16(cur.val[0], cur.val[1]));
                    const uint16x8x2_t l = vld2q_u16(src + 2 * x - 2);
                    const uint16x8x2_t uu = vld2q_u16(up + 2 * x);
                    const uint16x8x2_t ul = vld2q_u16(up + 2 * x - 2);
                    const uint16x8x2_t ur = vld2q_u16(up + 2 * x + 2);
                    vst1q_u16(u0 + x, zzv(cur.val[0], predict(l.val[0], uu.val[0], ul.val[0], ur.val[0])));
                    vst1q_u16(u1 + x, zzv(cur.val[1], predict(l.val[1], uu.val[1], ul.val[1], ur.val[1])));
                }
                if (x < tw && tw >= 9) {
                    // Row end: one vector step over the last 8 cells (overlapping
                    // cells already coded rewrites the same values) instead of up
                    // to 7 scalar cells per row; the last cell's up-right clamps to up.
                    x = tw - 8;
                    const uint16x8x2_t cur = vld2q_u16(src + 2 * x);
                    vmx = vmaxq_u16(vmx, vmaxq_u16(cur.val[0], cur.val[1]));
                    const uint16x8x2_t l = vld2q_u16(src + 2 * x - 2);
                    const uint16x8x2_t uu = vld2q_u16(up + 2 * x);
                    const uint16x8x2_t ul = vld2q_u16(up + 2 * x - 2);
                    uint16x8x2_t ur = vld2q_u16(up + 2 * x + 2);
                    for (int c = 0; c < 2; ++c) ur.val[c] = vcopyq_laneq_u16(ur.val[c], 7, uu.val[c], 7);
                    vst1q_u16(u0 + x, zzv(cur.val[0], predict(l.val[0], uu.val[0], ul.val[0], ur.val[0])));
                    vst1q_u16(u1 + x, zzv(cur.val[1], predict(l.val[1], uu.val[1], ul.val[1], ur.val[1])));
                    x = tw;
                }
                mx = vmaxvq_u16(vmx);
#endif
                for (; x < tw; ++x)
                    for (int px = 0; px < 2; ++px) {
                        mx = std::max(mx, src[2 * x + px]);
                        const int32_t L = at(src, x - 1, px), U = at(up, x, px), UL = at(up, x - 1, px);
                        const int32_t UR = x + 1 < tw ? at(up, x + 1, px) : U;
                        (px ? u1 : u0)[x] = zz(at(src, x, px) - ((L + U + UL + UR + 2) >> 2));
                    }
            }
            if (narrow) {
                pforRow(lb, nw, u0, tw, j.d->pfor);
                pforRow(lb, nw, u1, tw, j.d->pfor);
            }
        }
    }
    maxSample = mx;
    const size_t nibBytes = nw.finish(nibBegin);
    out[0] = 0;
    out[1] = uint8_t(nibBytes); out[2] = uint8_t(nibBytes >> 8);
    out[3] = uint8_t(nibBytes >> 16); out[4] = uint8_t(nibBytes >> 24);
    const size_t dataBytes = static_cast<size_t>(lb.out - blocks);
    std::memmove(out + 5 + nibBytes, blocks, dataBytes);
    return 5 + nibBytes + dataBytes;
}

// Splits a tile into the four coded planes (SPEC 3.2): G1, G2-G1, R-G1, B-G1,
// and tracks the largest raw sample for the bit-depth check.
void extractTile(const TileJob& j, const int* L, uint32_t tx, uint32_t ty, uint32_t tw, uint32_t th,
                 int32_t* planes, size_t n, uint16_t& maxSample) {
    uint16_t mx = maxSample;
    for (uint32_t y = 0; y < th; ++y) {
        const uint16_t* r0 = j.raw + (2 * (ty + y)) * j.stride + 2 * tx;
        const uint16_t* r1 = r0 + j.stride;
        int32_t* dst[4];
        for (int i = 0; i < 4; ++i) dst[i] = planes + i * n + size_t{y} * tw;
        uint32_t x = 0;
#if defined(__ARM_NEON) && defined(__aarch64__)
        uint16x8_t vmx = vdupq_n_u16(mx);
        for (; x + 8 <= tw; x += 8) {
            const uint16x8x2_t a = vld2q_u16(r0 + 2 * x), b = vld2q_u16(r1 + 2 * x);
            uint16x8_t pos[4] = {a.val[0], a.val[1], b.val[0], b.val[1]};
            vmx = vmaxq_u16(vmx, vmaxq_u16(vmaxq_u16(pos[0], pos[1]), vmaxq_u16(pos[2], pos[3])));
            const uint16x8_t g1 = pos[L[0]];
            vst1q_s32(dst[0] + x, vreinterpretq_s32_u32(vmovl_u16(vget_low_u16(g1))));
            vst1q_s32(dst[0] + x + 4, vreinterpretq_s32_u32(vmovl_high_u16(g1)));
            for (int i = 1; i < 4; ++i) {
                const uint16x8_t v = pos[L[i]];
                vst1q_s32(dst[i] + x, vreinterpretq_s32_u32(vsubl_u16(vget_low_u16(v), vget_low_u16(g1))));
                vst1q_s32(dst[i] + x + 4, vreinterpretq_s32_u32(vsubl_high_u16(v, g1)));
            }
        }
        mx = vmaxvq_u16(vmx);
#endif
        for (; x < tw; ++x) {
            const uint16_t pos[4] = {r0[2 * x], r0[2 * x + 1], r1[2 * x], r1[2 * x + 1]};
            mx = std::max({mx, pos[0], pos[1], pos[2], pos[3]});
            const int32_t g1 = pos[L[0]];
            dst[0][x] = g1;
            for (int i = 1; i < 4; ++i) dst[i][x] = int32_t(pos[L[i]]) - g1;
        }
    }
    maxSample = mx;
}

#ifdef CRAW_PROFILE
}  // namespace
std::atomic<int64_t> gProfNs[3];
namespace {
struct ProfScope {
    int i; std::chrono::steady_clock::time_point t0 = std::chrono::steady_clock::now();
    ~ProfScope() { gProfNs[i] += (std::chrono::steady_clock::now() - t0).count(); }
};
#define CRAW_PROF(i) ProfScope prof_##i{i}
#else
#define CRAW_PROF(i)
#endif

// Encoded tile bytes. Capacity persists frame to frame without the zero-fill
// std::vector::resize would do on every frame.
struct TileBuf {
    std::unique_ptr<uint8_t[]> data;
    size_t cap = 0, size = 0;
    uint8_t* reserve(size_t n) {
        if (cap < n) { data.reset(new uint8_t[n]); cap = n; }
        return data.get();
    }
    void assign(const uint8_t* p, size_t n) { std::memcpy(reserve(n), p, n); size = n; }
};

size_t scratchBytes(uint32_t tile);


// Desktop Compact (codec 4): tile predictor search and explicit block Rice k.
int compactPred(const int32_t* p, uint32_t x, uint32_t y, uint32_t w, int32_t first, int mode) {
    if (!y) return x ? p[y*w+x-1] : first;
    if (!x) return p[(y-1)*w];
    const int32_t L=p[y*w+x-1], U=p[(y-1)*w+x], UL=p[(y-1)*w+x-1];
    const int32_t UR=p[(y-1)*w+std::min(x+1,w-1)];
    if (mode==1) return med(L,U,UL);
    if (mode==2) return (L+U+1)>>1;
    if (mode==3) return (2*L+U+UR+2)>>2;
    return (L+U+UL+UR+2)>>2;
}
template<int Mode>
void compactResiduals(const int32_t* p,uint32_t w,uint32_t h,int32_t first,uint32_t* u) {
    for(uint32_t y=0;y<h;++y) {
        const int32_t* row=p+size_t(y)*w;
        const int32_t* up=y ? row-w : row;
        uint32_t* dst=u+size_t(y)*w;
        dst[0]=zigzag(row[0]-(y ? up[0] : first));
        if(!y) {for(uint32_t x=1;x<w;++x)dst[x]=zigzag(row[x]-row[x-1]);continue;}
        uint32_t x=1;
#if defined(__ARM_NEON) && defined(__aarch64__)
        for(;x+4<w;x+=4) {
            const int32x4_t L=vld1q_s32(row+x-1),U=vld1q_s32(up+x),UL=vld1q_s32(up+x-1),UR=vld1q_s32(up+x+1);
            int32x4_t pred;
            if constexpr(Mode==1) pred=vminq_s32(vmaxq_s32(vsubq_s32(vaddq_s32(L,U),UL),vminq_s32(L,U)),vmaxq_s32(L,U));
            else if constexpr(Mode==2) pred=vrshrq_n_s32(vaddq_s32(L,U),1);
            else if constexpr(Mode==3) pred=vrshrq_n_s32(vaddq_s32(vaddq_s32(L,L),vaddq_s32(U,UR)),2);
            else pred=vrshrq_n_s32(vaddq_s32(vaddq_s32(L,U),vaddq_s32(UL,UR)),2);
            const int32x4_t e=vsubq_s32(vld1q_s32(row+x),pred);
            vst1q_u32(dst+x,vreinterpretq_u32_s32(veorq_s32(vshlq_n_s32(e,1),vshrq_n_s32(e,31))));
        }
#endif
        for(;x<w;++x)dst[x]=zigzag(row[x]-compactPred(p,x,y,w,first,Mode));
    }
}

int compactK(const uint32_t* u, uint32_t n, uint64_t& cost) {
    int best=0; cost=UINT64_MAX;
    for (int k=0;k<=15;++k) {
        if (uint64_t(n)*(k+1)>=cost) break;
        uint32_t c=0;
#if defined(__ARM_NEON) && defined(__aarch64__)
        if(n==16) {
            uint32x4_t sum=vdupq_n_u32(0);
            const int32x4_t shift=vdupq_n_s32(-k);
            for(int i=0;i<16;i+=4) {
                const uint32x4_t q=vshlq_u32(vld1q_u32(u+i),shift);
                sum=vaddq_u32(sum,vbslq_u32(vcltq_u32(q,vdupq_n_u32(24)),vaddq_u32(q,vdupq_n_u32(1+k)),vdupq_n_u32(49)));
            }
            c=vaddvq_u32(sum);
        } else
#endif
        for (uint32_t i=0;i<n;++i) { const uint32_t q=u[i]>>k; c+=q<24 ? q+1+k : 49; }
        if(c<cost) {cost=c;best=k;}
    }
    return best;
}
void encodeCompact(const TileJob& j,uint32_t tx,uint32_t ty,uint32_t tw,uint32_t th,
                   TileBuf& out,uint16_t& maxSample) {
    const size_t n=size_t(tw)*th, blocks=4*size_t(th)*((tw+15)/16);
    std::vector<int32_t> p(4*n);
    for(int c=0;c<4;++c) for(uint32_t y=0;y<th;++y) for(uint32_t x=0;x<tw;++x) {
        const uint16_t v=j.raw[(2*(ty+y)+(c>>1))*j.stride+2*(tx+x)+(c&1)];
        p[c*n+size_t(y)*tw+x]=v;maxSample=std::max(maxSample,v);
    }
    std::vector<uint32_t> candidate(4*n),best;
    std::vector<uint8_t> ks(blocks),bestKs;
    uint64_t bestCost=UINT64_MAX;int modeBest=0;
    for(int mode=0;mode<4;++mode) {
        uint64_t cost=0;size_t b=0;
        for(int c=0;c<4;++c) {
            auto src=p.data()+c*n;auto dst=candidate.data()+c*n;
            const int first=1<<(j.d->bitDepth-1);
            switch(mode) {
                case 0:compactResiduals<0>(src,tw,th,first,dst);break;
                case 1:compactResiduals<1>(src,tw,th,first,dst);break;
                case 2:compactResiduals<2>(src,tw,th,first,dst);break;
                case 3:compactResiduals<3>(src,tw,th,first,dst);break;
            }
        }
        for(int c=0;c<4;++c) for(uint32_t y=0;y<th;++y) {
            for(uint32_t x=0;x<tw;x+=16) {
                uint64_t bits;ks[b++]=uint8_t(compactK(candidate.data()+c*n+size_t(y)*tw+x,std::min(16u,tw-x),bits));cost+=bits;
            }
        }
        if(cost<bestCost) {bestCost=cost;modeBest=mode;best=candidate;bestKs=ks;}
    }
    const size_t nibBytes=(blocks+1)/2, coded=2+nibBytes+(bestCost+7)/8, stored=1+8*n;
    uint8_t* dst=out.reserve(std::max(stored,coded)+16);
    if(coded>=stored) {
        dst[0]=1;for(size_t i=0;i<4*n;++i){dst[1+2*i]=uint8_t(p[i]);dst[2+2*i]=uint8_t(p[i]>>8);}out.size=stored;return;
    }
    dst[0]=0;dst[1]=uint8_t(modeBest);std::fill(dst+2,dst+2+nibBytes,0);
    for(size_t b=0;b<blocks;++b) dst[2+b/2]|=uint8_t(bestKs[b]<<(4*(b&1)));
    BitWriter bw(dst+2+nibBytes);size_t b=0;
    for(int c=0;c<4;++c) for(uint32_t y=0;y<th;++y) for(uint32_t x=0;x<tw;x+=16)
        putBlock(bw,best.data()+c*n+size_t(y)*tw+x,int(std::min(16u,tw-x)),bestKs[b++]);
    out.size=2+nibBytes+bw.finish();
}

void encodeTile(const TileJob& j, uint32_t tx, uint32_t ty, uint32_t tw, uint32_t th,
                std::vector<int32_t>& planes, std::vector<uint32_t>& u, std::vector<int64_t>& sums,
                uint8_t* scratch, TileBuf& out, uint16_t& maxSample) {
    if (j.d->codec == 4) { encodeCompact(j,tx,ty,tw,th,out,maxSample); return; }
    if (j.d->codec == 2 || j.d->codec == 3) {
        const size_t n = size_t{tw} * th;
        // Encode in place into the tile buffer (no copy).
        (void)scratch;
        // At most 17 bits/sample, byte aligned, plus two width nibbles.
        // Avoid retaining the much larger Rice bound for every Live tile.
        const size_t blocks = size_t{4} * th * ((tw + kBlock - 1) / kBlock);
        uint8_t* dst = out.reserve(5 + blocks * (j.d->codec == 3 ? 48 : 35) + kPackSlack);
        const size_t coded = j.d->codec == 3 ? encodeTileLive2(j, tx, ty, tw, th, dst, maxSample)
                                             : encodeTileLive(j, tx, ty, tw, th, dst, u, maxSample);
        const size_t stored = 1 + 8 * n;
        if (coded < stored) {
            out.size = coded;
            return;
        }
        // Stored: planes in cell order, untransformed.
        out.size = stored;
        dst[0] = 1;
        uint8_t* o = dst + 1;
        for (int cell = 0; cell < 4; ++cell)
            for (uint32_t y = 0; y < th; ++y) {
                const uint16_t* src = j.raw + (2 * (ty + y) + (cell >> 1)) * j.stride + 2 * tx + (cell & 1);
                for (uint32_t x = 0; x < tw; ++x, o += 2) { o[0] = uint8_t(src[2 * x]); o[1] = uint8_t(src[2 * x] >> 8); }
            }
        return;
    }
    const int* L = kLayout[static_cast<int>(j.d->cfa)];
    const size_t n = size_t{tw} * th;
    planes.resize(4 * n);
    {
    CRAW_PROF(0);
    extractTile(j, L, tx, ty, tw, th, planes.data(), n, maxSample);
    }

    const size_t stored = 1 + 8 * n;
    size_t coded;
    CRAW_PROF(1);
    {
        scratch[0] = 0;
        BitWriter bw(scratch + 1);
        for (int i = 0; i < 4; ++i)
            encodePlane(bw, planes.data() + i * n, tw, th,
                        i == 0 ? (1 << (j.d->bitDepth - 1)) : 0, j.riceInit, u, sums);
        coded = 1 + bw.finish();
    }
    if (coded < stored) {
        out.assign(scratch, coded);
        return;
    }
    // Stored form uses the untransformed planes.
    uint8_t* dst = out.reserve(stored);
    out.size = stored;
    dst[0] = 1;
    for (size_t i = 0; i < 4 * n; ++i) {
        const int32_t v = i < n ? planes[i] : planes[i] + planes[i % n];
        dst[1 + 2 * i] = uint8_t(v);
        dst[2 + 2 * i] = uint8_t(v >> 8);
    }
}

// Worst-case coded tile: 25 prefix + 24 escape bits per sample.
size_t scratchBytes(uint32_t tile) {
    // Rice worst case: 25 prefix + 24 escape bits per sample; Live: <= 3 bytes
    // per sample after a worst-case nibble area. Both fit this bound.
    return 1 + (size_t{4} * tile * tile * 49 + 7) / 8 + 8 + size_t{4} * tile * ((tile + 15) / 16) + 16;
}

uint32_t crcRows(const uint16_t* raw, size_t stride, uint32_t width, uint32_t y0, uint32_t y1) {
    uint32_t crc = 0;
    for (uint32_t y = y0; y < y1; ++y) {
        const uint16_t* row = raw + y * stride;
        if constexpr (std::endian::native == std::endian::little) {
            crc = detail::crc32c(row, width * 2, crc);
        } else {
            for (uint32_t x = 0; x < width; ++x) {
                const uint8_t le[2] = {static_cast<uint8_t>(row[x]), static_cast<uint8_t>(row[x] >> 8)};
                crc = detail::crc32c(le, 2, crc);
            }
        }
    }
    return crc;
}

}  // namespace

uint16_t defaultRiceInit(uint8_t bitDepth) {
    return static_cast<uint16_t>(std::max(1, 1 << std::max(bitDepth - 8, 0)) * 4);
}

namespace {

// Encodes all tiles; `emit(header, tiles)` assembles the payload.
template <typename Emit>
void encodeTiles(const uint16_t* raw, size_t stride, const FrameDesc& d, unsigned threads, Emit&& emit) {
    if (!raw || d.width == 0 || d.height == 0 || d.width % 2 || d.height % 2 ||
        d.bitDepth < 1 || d.bitDepth > 16 || d.tile == 0 || stride < d.width ||
        static_cast<int>(d.cfa) > 4 || (d.codec < 1 || d.codec > 4) ||
        (d.cfa == Cfa::RGB && d.codec == 1))
        throw std::invalid_argument("craw::encodeFrame: invalid frame geometry");

    const uint32_t pw = d.width / 2, ph = d.height / 2;
    const uint32_t tilesX = (pw + d.tile - 1) / d.tile, tilesY = (ph + d.tile - 1) / d.tile;
    const uint32_t count = tilesX * tilesY;
    const TileJob job{raw, stride, &d, d.riceInit ? d.riceInit : defaultRiceInit(d.bitDepth)};

    // Per-thread buffers survive across frames: a recorder calls this from a few
    // long-lived worker threads, and re-allocating ~10 MB per frame costs page
    // faults and memory reclaim on phones (measured under live capture).
    struct Workspace {
        std::vector<int32_t> planes;
        std::vector<uint32_t> u;
        std::vector<int64_t> sums;
        std::vector<uint8_t> scratch;
        std::vector<TileBuf> tiles;
    };
    thread_local Workspace caller;
    auto& tiles = caller.tiles;
    tiles.resize(count);  // tile buffers keep their capacity frame to frame

    // Jobs [0, tilesY) checksum one band of image rows each; the rest encode tiles.
    std::vector<uint32_t> bandCrc(tilesY);
    std::vector<uint16_t> maxSample(threads ? threads : 1, 0);
    std::atomic<uint32_t> next{0};
    auto worker = [&](unsigned id) {
        thread_local Workspace ws;
        auto& planes = ws.planes;
        auto& u = ws.u;
        auto& sums = ws.sums;
        if (ws.scratch.size() < scratchBytes(d.tile)) ws.scratch.resize(scratchBytes(d.tile));
        uint8_t* scratch = ws.scratch.data();
        for (uint32_t jb; (jb = next.fetch_add(1)) < tilesY + count;) {
            if (jb < tilesY) {
                if (!d.frameCrc) continue;
                const uint32_t y0 = 2 * jb * d.tile, y1 = std::min<uint32_t>(d.height, y0 + 2 * d.tile);
                bandCrc[jb] = crcRows(raw, stride, d.width, y0, y1);
                continue;
            }
            const uint32_t t = jb - tilesY;
            const uint32_t tx = (t % tilesX) * d.tile, ty = (t / tilesX) * d.tile;
            encodeTile(job, tx, ty, std::min<uint32_t>(d.tile, pw - tx), std::min<uint32_t>(d.tile, ph - ty),
                       planes, u, sums, scratch, tiles[t], maxSample[id]);
        }
    };
    threads = std::clamp(threads, 1u, count);
    maxSample.assign(threads, 0);
    std::vector<std::thread> pool;
    for (unsigned i = 1; i < threads; ++i) pool.emplace_back(worker, i);
    worker(0);
    for (auto& t : pool) t.join();

    if (*std::max_element(maxSample.begin(), maxSample.end()) >= (1u << d.bitDepth))
        throw std::invalid_argument("craw::encodeFrame: sample exceeds bit depth");
    uint32_t crc = d.frameCrc ? bandCrc[0] : 0;
    for (uint32_t b = 1; d.frameCrc && b < tilesY; ++b) {
        const uint32_t rows = std::min<uint32_t>(d.height, 2 * (b + 1) * d.tile) - 2 * b * d.tile;
        crc = detail::crc32cCombine(crc, bandCrc[b], uint64_t{rows} * d.width * 2);
    }

    std::vector<uint8_t> head;
    head.reserve(24 + 4 * (size_t{count} + 1));
    head.push_back(d.codec);
    head.push_back(d.bitDepth);
    head.push_back(static_cast<uint8_t>(d.cfa));
    head.push_back(d.frameCrc ? 0 : 1);  // flags: bit 0 = no frame CRC
    putU32(head, d.width);
    putU32(head, d.height);
    putU16(head, d.tile);
    putU16(head, static_cast<uint16_t>(job.riceInit));
    putU32(head, crc);
    putU32(head, count);
    uint32_t off = 0;
    putU32(head, 0);
    for (auto& t : tiles) putU32(head, off += static_cast<uint32_t>(t.size));
    emit(head, tiles);
}

// Recorder workers encode one frame each. Stream tiles into the caller's
// pooled buffer so each worker retains one tile, rather than a full frame
// of worst-case tile allocations alongside its output buffer.
size_t encodeSerialInto(const uint16_t* raw, size_t stride, const FrameDesc& d,
                        uint8_t* dst, size_t capacity) {
    if (!raw || !d.width || !d.height || d.width % 2 || d.height % 2 ||
        d.bitDepth < 1 || d.bitDepth > 16 || !d.tile || stride < d.width ||
        static_cast<int>(d.cfa) > 4 || (d.codec < 1 || d.codec > 4) ||
        (d.cfa == Cfa::RGB && d.codec == 1))
        throw std::invalid_argument("craw::encodeFrame: invalid frame geometry");
    const uint32_t pw = d.width / 2, ph = d.height / 2;
    const uint32_t nx = (pw + d.tile - 1) / d.tile, ny = (ph + d.tile - 1) / d.tile;
    const uint32_t count = nx * ny;
    const size_t headerBytes = 24 + 4 * (size_t{count} + 1);
    if (!dst || headerBytes > capacity)
        throw std::length_error("craw::encodeFrame: destination too small");
    struct Workspace {
        std::vector<int32_t> planes;
        std::vector<uint32_t> u;
        std::vector<int64_t> sums;
        std::vector<uint8_t> scratch;
        TileBuf tile;
    };
    thread_local Workspace ws;
    if (d.codec == 1 && ws.scratch.size() < scratchBytes(d.tile))
        ws.scratch.resize(scratchBytes(d.tile));
    const uint16_t rice = d.riceInit ? d.riceInit : defaultRiceInit(d.bitDepth);
    const TileJob job{raw, stride, &d, rice};
    uint16_t maxSample = 0;
    size_t written = headerBytes;
    auto offset = [&](uint32_t i) {
        const uint32_t v = static_cast<uint32_t>(written - headerBytes);
        for (int b = 0; b < 4; ++b) dst[24 + size_t{i} * 4 + b] = uint8_t(v >> (8 * b));
    };
    offset(0);
    for (uint32_t t = 0; t < count; ++t) {
        const uint32_t tx = (t % nx) * d.tile, ty = (t / nx) * d.tile;
        encodeTile(job, tx, ty, std::min<uint32_t>(d.tile, pw - tx), std::min<uint32_t>(d.tile, ph - ty),
                   ws.planes, ws.u, ws.sums, ws.scratch.data(), ws.tile, maxSample);
        if (ws.tile.size > capacity - written)
            throw std::length_error("craw::encodeFrame: destination too small");
        std::memcpy(dst + written, ws.tile.data.get(), ws.tile.size);
        written += ws.tile.size;
        offset(t + 1);
    }
    if (maxSample >= (1u << d.bitDepth))
        throw std::invalid_argument("craw::encodeFrame: sample exceeds bit depth");
    const uint32_t crc = d.frameCrc ? crcRows(raw, stride, d.width, 0, d.height) : 0;
    std::vector<uint8_t> head{d.codec, d.bitDepth, static_cast<uint8_t>(d.cfa), uint8_t(d.frameCrc ? 0 : 1)};
    putU32(head, d.width); putU32(head, d.height);
    putU16(head, d.tile); putU16(head, rice);
    putU32(head, crc); putU32(head, count);
    std::memcpy(dst, head.data(), head.size());
    return written;
}

}  // namespace

void encodeFrame(const uint16_t* raw, size_t stride, const FrameDesc& d,
                 std::vector<uint8_t>& out, unsigned threads) {
    encodeTiles(raw, stride, d, threads, [&](const std::vector<uint8_t>& head,
                                             const std::vector<TileBuf>& tiles) {
        out.assign(head.begin(), head.end());
        for (auto& t : tiles) out.insert(out.end(), t.data.get(), t.data.get() + t.size);
    });
}

size_t encodeFrame(const uint16_t* raw, size_t stride, const FrameDesc& d, uint8_t* dst,
                   size_t capacity, unsigned threads) {
    if (threads <= 1) return encodeSerialInto(raw, stride, d, dst, capacity);
    size_t written = 0;
    encodeTiles(raw, stride, d, threads, [&](const std::vector<uint8_t>& head,
                                             const std::vector<TileBuf>& tiles) {
        size_t total = head.size();
        for (auto& t : tiles) total += t.size;
        if (!dst || total > capacity) throw std::length_error("craw::encodeFrame: destination too small");
        std::memcpy(dst, head.data(), head.size());
        written = head.size();
        for (auto& t : tiles) {
            std::memcpy(dst + written, t.data.get(), t.size);
            written += t.size;
        }
    });
    return written;
}

}  // namespace craw
