// SPDX-License-Identifier: Apache-2.0
//
// Lossless encoder for .CRAW frame payloads: 1 Balanced, 2 Live v1 — Legacy,
// 3 Live, 4 Compact — Offline. Names do not imply a universal speed/size ordering.
// Wire format: CinematicRAWStudio-Spec/SPEC.md section 3.
#pragma once

#include <craw/CodecNames.h>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace craw {

// RGB (SPEC 3.8): a W x H RGB image stored as a 2W x 2H cell frame, cells
// (0,0)=R, (0,1)=G, (1,0)=B, (1,1)=0. Codecs 2, 3 and 4.
#ifndef CRAW_CFA_DEFINED
#define CRAW_CFA_DEFINED
enum class Cfa : uint8_t { RGGB = 0, GRBG = 1, GBRG = 2, BGGR = 3, RGB = 4 };
#endif

struct FrameDesc {
    uint32_t width = 0;      // even
    uint32_t height = 0;     // even
    uint8_t bitDepth = 10;   // 1..16, every sample < 2^bitDepth
    Cfa cfa = Cfa::RGGB;
    uint16_t tile = 128;     // tile edge in plane samples
    uint16_t riceInit = 0;   // 0 = default noise class for bitDepth (codec 1)
    // 1 = CRAW Balanced (legacy CRAW-Fast; block-adaptive Rice),
    // 2 = CRAW Live v1 — Legacy (left prediction, fixed-width block packing),
    // 3 = CRAW Live (legacy Live v2; 4-neighbour prediction, fixed packing),
    // 4 = CRAW Compact — Offline (tile predictor search + explicit Rice; desktop only).
    uint8_t codec = 1;
    // Store a CRC32C of the raw samples in the frame header. The container's
    // per-chunk CRC32C covers the payload either way; turning this off saves a
    // full pass over the frame (live capture).
    bool frameCrc = true;
    // Codec 3 only: search patched (PFOR) blocks. ~0.2-0.5 % smaller files for
    // about twice the encode time; off for live capture.
    bool pfor = false;
};

uint16_t defaultRiceInit(uint8_t bitDepth);

// Encodes one Bayer frame of `height` rows, `strideSamples` uint16 apart.
// `threads` > 1 encodes tiles in parallel; output is identical either way.
// `out` is cleared first and its capacity reused.
// Throws std::invalid_argument on bad geometry or out-of-range samples.
void encodeFrame(const uint16_t* raw, size_t strideSamples, const FrameDesc& desc,
                 std::vector<uint8_t>& out, unsigned threads = 1);

// Same, into caller memory (e.g. a pooled direct buffer); returns the payload
// size. Throws std::length_error when `capacity` is too small.
size_t encodeFrame(const uint16_t* raw, size_t strideSamples, const FrameDesc& desc,
                   uint8_t* dst, size_t capacity, unsigned threads = 1);

}  // namespace craw
