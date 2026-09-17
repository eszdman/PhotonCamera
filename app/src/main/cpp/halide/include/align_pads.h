#pragma once

// Level paddings for the alignburst AOT kernels - the "buffer ABI" shared
// by everything that allocates the base pyramid buffers:
//
//   - the generator pipeline itself (include/align_pipeline.h),
//   - the AOT harness (app/aot_test.cpp),
//   - the JNI wrappers (PhotonCamera cpp/halide/align_jni.cpp on Android,
//     pcamMerge native/pcam_align_jni.cpp on the PC).
//
// Stage 2 derives the raw size from base_l0's extent using the SAME baked
// paddings, so buffers allocated with different values silently corrupt
// the alignment (observed as p99 errors of ~70 texels in the AOT harness
// after a padding change rebuilt only one side). Keep this header next to
// the generated alignburst*.h when vendoring artifacts.
//
// The coarsest level has no seed offset, but its tile grid is ceil(lh/T)
// tiles, so the tile extent alone can overhang the image by up to T-1
// texels - and the BASE tile read (no candidate offset) reaches that far
// into the padding. The base levels are runtime buffers with bounds
// checks, so the padding must cover the worst overhang or stage 2 faults
// on any geometry whose coarsest level is not a tile multiple (e.g.
// 4096x2304 -> 128x72 at level 4, overhang 8 > the old R+2=6; 4000x3000
// passed only by luck with overhang 3). Alt-side reads that spill past the
// padded window (possible by the same overhang) hit the internal mirror
// boundary - correct, just not the ideal contiguous-load path.
//
// No Halide dependency on purpose: usable from JNI wrappers that only link
// the AOT static libraries + runtime.

#include <algorithm>
#include <vector>

namespace halide_align {

struct PadParams {
    int tile_size = 16;
    int search_radius = 4;
    int max_level = 4;
};

// Mirror-pad amount per level: large enough that every matcher read (tile
// extent + upsampled seed + search radius) and every gauss read of the
// level below (2x-1 .. 2x+1) is inside the stored level.
inline std::vector<int> level_paddings(const PadParams &p) {
    std::vector<int> B(p.max_level + 1, 0);
    B[p.max_level] = std::max(p.search_radius + 2, p.tile_size - 1);
    for (int l = p.max_level - 1; l >= 0; l--) {
        int matcher_need = p.search_radius * (1 << (p.max_level - l + 1)) + 2;
        int gauss_need = 2 * B[l + 1] + 1;  // level l feeds padded level l+1
        B[l] = std::max(matcher_need, gauss_need);
    }
    return B;
}

}  // namespace halide_align
