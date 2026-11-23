//
// Created by eszdman on 19.11.2026.
//

#include "Alignment.hpp"
#include "halfConvert.h"
#include "HalideBuffer.h"
#include "align_pads.h"
#include "alignburst_base_f16.h"
#include "alignburst_base_precise_f16.h"
#include "alignburst_f16.h"
#include "alignburst_f16_global.h"
#include "alignburst_f16_precise.h"
#include "alignburst_f16_reference.h"
#include "alignflow_base.h"
#include "alignflow_f16.h"
#include <cmath>
#include <stdexcept>

namespace photon::esd4d {
namespace {
template <class T = void> using Buffer = Halide::Runtime::Buffer<T>;
Buffer<> rawView(int w, int h, const std::vector<std::uint16_t>& pixels) {
    if (w < 32 || h < 32 || std::uint64_t(w) * h != pixels.size())
        throw std::invalid_argument("Invalid precise alignment frame");
    return Buffer<>(halide_type_t(halide_type_float, 16), const_cast<std::uint16_t*>(pixels.data()), w,
                    h);
}
float roundHalf(float v) { return halfValue(f32ToF16(v)); }

int tileCount(int levelSize) { return std::max(1, (levelSize - 16) / 8 + 1); }

Buffer<float> medianField(const Buffer<float>& src) {
    const int nx = src.dim(1).extent(), ny = src.dim(2).extent();
    Buffer<float> result(2, nx, ny);
    for (int y = 0; y < ny; ++y)
        for (int x = 0; x < nx; ++x)
            for (int c = 0; c < 2; ++c) {
                std::array<float, 9> v;
                for (int j = -1; j <= 1; ++j)
                    for (int i = -1; i <= 1; ++i)
                        v[(j + 1) * 3 + i + 1] =
                            src(c, std::clamp(x + i, 0, nx - 1), std::clamp(y + j, 0, ny - 1));
                std::nth_element(v.begin(), v.begin() + 4, v.end());
                result(c, x, y) = v[4];
            }
    return result;
}

template <class T> void allocateLevels(std::array<Buffer<T>, 5>& levels, int w, int h) {
    const auto pads = halide_align::level_paddings({});
    for (int i = 0; i < 5; ++i) {
        levels[i] = Buffer<T>((w >> (i + 1)) + 2 * pads[i], (h >> (i + 1)) + 2 * pads[i]);
        levels[i].translate(0, -pads[i]);
        levels[i].translate(1, -pads[i]);
    }
}
} // namespace
struct PreciseAlignment::State {
    int w, h;
    bool precise, accurate, refine;
    std::array<Buffer<std::uint16_t>, 5> q3;
    std::array<Buffer<std::uint8_t>, 5> u8;
    Buffer<std::int32_t> gradients;
    State(int width, int height, const std::vector<std::uint16_t>& ref, bool accurateMode,
          bool refineMode, bool noiseAware)
        : w(width), h(height), precise(accurateMode && !refineMode && noiseAware),
          accurate(accurateMode && !refineMode), refine(refineMode) {
        auto input = rawView(w, h, ref);
        int status;
        if (precise) {
            allocateLevels(q3, w, h);
            status = alignburst_base_precise_f16(input, 1, q3[0], q3[1], q3[2], q3[3], q3[4]);
        } else {
            allocateLevels(u8, w, h);
            status = alignburst_base_f16(input, 1, u8[0], u8[1], u8[2], u8[3], u8[4]);
        }
        if (status)
            throw std::runtime_error("Halide reference pyramid failed");
        if (refine) {
            gradients = Buffer<std::int32_t>(3, tileCount(w / 2), tileCount(h / 2));
            if (alignflow_base(u8[0], gradients))
                throw std::runtime_error("Halide flow gradients failed");
        }
    }
};
PreciseAlignment::PreciseAlignment(int w, int h, const std::vector<std::uint16_t>& base, bool accurate,
                                   bool refine, bool noiseAware)
    : state_(std::make_unique<State>(w, h, base, accurate, refine, noiseAware)) {}
PreciseAlignment::~PreciseAlignment() = default;

Warp PreciseAlignment::estimate(const std::vector<std::uint16_t>& frame, float exposure, float noiseS,
                                float noiseO, bool coarseGlobal) const {
    auto& s = *state_;
    auto input = rawView(s.w, s.h, frame);
    int nx = tileCount(s.w / 4), ny = tileCount(s.h / 4);
    Buffer<float> motion(2, nx, ny);
    int status;
    if (s.precise) {
        auto& l = s.q3;
        status = alignburst_f16_precise(l[0], l[1], l[2], l[3], l[4], input, 1 / exposure,
                                        noiseS * (1 + exposure), noiseO * (1 + exposure * exposure),
                                        coarseGlobal, motion);
    } else {
        auto& l = s.u8;
        auto kernel = s.accurate ? (coarseGlobal ? alignburst_f16_global : alignburst_f16)
                                 : alignburst_f16_reference;
        status = kernel(l[0], l[1], l[2], l[3], l[4], input, 1 / exposure, motion);
    }
    if (status)
        throw std::runtime_error("Halide motion estimation failed");
    motion = medianField(motion);
    if (s.refine) {
        nx = tileCount(s.w / 2);
        ny = tileCount(s.h / 2);
        Buffer<float> dense(2, nx, ny);
        if (alignflow_f16(s.u8[0], s.gradients, input, 1 / exposure, motion, dense))
            throw std::runtime_error("Halide flow refinement failed");
        motion = medianField(dense);
    }
    Warp warp;
    warp.tile = s.refine ? 16 : 32;
    warp.sampling = Sampling::Lanczos2;
    warp.fieldWidth = nx;
    warp.fieldHeight = ny;
    warp.field.resize(std::size_t(nx) * ny * 4);
    for (int y = 0; y < ny; ++y)
        for (int x = 0; x < nx; ++x)
            for (int c = 0; c < 2; ++c) {
                float d = motion(c, x, y), whole = std::floor(d);
                auto at = (std::size_t(y) * nx + x) * 4 + c;
                warp.field[at] = roundHalf(whole / ((c ? s.h : s.w) / 2));
                warp.field[at + 2] = roundHalf(d - whole);
            }
    return warp;
}
} // namespace photon::esd4d
