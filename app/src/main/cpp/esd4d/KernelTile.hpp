//
// Created by eszdman on 21.11.2026.
//

#pragma once
#include <algorithm>
#include <cstdint>

namespace photon::esd4d {
constexpr int kernelInferenceThreads = 2;

// One axis of a KernelNet input tile and the half-resolution output range it owns.
struct KernelTile {
    static constexpr int core = 1024, border = 32, full = core + 2 * border;
    int origin, extent, begin, end;

    // A 4000x3000 Bayer frame can add one CFA site per axis and round
    // the half-resolution luma row up to a multiple of four samples.
    static bool fitsSinglePass(int w, int h) {
        return w > 0 && h > 0 && std::int64_t(w) * h <= 2004 * 1501;
    }

    static int count(int size) { return 1 + (std::max(0, size - full) + core - 1) / core; }

    static KernelTile at(int index, int size) {
        int origin = index * core;
        int remaining = size - origin;
        int extent = remaining >= full ? full : std::min(full, 2 * ((remaining + 1) / 2) + 2 * border);
        return {origin, extent, (origin + (index ? border : 0)) / 2,
                remaining <= full ? (size + 1) / 2 : (origin + full - border) / 2};
    }
};
} // namespace photon::esd4d
