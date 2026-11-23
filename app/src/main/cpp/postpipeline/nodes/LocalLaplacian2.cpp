//
// Created by eszdman on 21.11.2026.
//

#include "PostNodes.hpp"
#include "down_rgb.hpp"
#include "down_luma.hpp"
#include "remap_packed.hpp"
#include "reduce_packed.hpp"
#include "coarse_recon.hpp"
#include "recon_luma.hpp"
#include "recon_rgb.hpp"
#include "Bayer.hpp"
#include "halfConvert.h"
#include <algorithm>
#include <cmath>

namespace photon::post {
using namespace shaders;
namespace {
constexpr int anchors = 24, samples = 512, maxLevels = 12;
Scalar remapLut(PostState& s, const settings::LocalLaplacian2& t) {
    const float sigma = std::clamp(t.midtone, .001f, 1.f);
    std::vector<float> values(samples * anchors);
    for (int k = 0; k < anchors; ++k) {
        float anchor = (k + .5f) / anchors;
        for (int i = 0; i < samples; ++i) {
            float c = float(i) / (samples - 1) - anchor;
            float slope = c > 0 ? t.shadows : t.highlights;
            float d = std::abs(c), w = std::min(d / (2 * sigma), 1.f);
            float v = d > 2 * sigma ? sigma + slope * (d - sigma)
                                    : sigma * 2 * (1 - w) * w + w * w * (sigma + sigma * slope);
            v = anchor + (c > 0 ? v : -v) + t.detail * c * std::exp(-c * c / (2 * sigma * sigma / 3));
            values[k * samples + i] = esd4d::halfValue(f32ToF16(v));
        }
    }
    auto tensor = s.gpu.tensorT(values);
    s.upload({tensor});
    return {tensor, {samples, anchors}};
}

template <class Pass, Format F> Scalar down(PostState& s, Plane<F> input) {
    auto out = s.scalar({(input.size.x + 1) / 2, (input.size.y + 1) / 2});
    typename Pass::Parameters p;
    p.inputSize = input.size;
    s.passes.dispatch(p, Pass::InputBuffer(input), Pass::Output(out));
    return out;
}
template <class Pass, Format F>
void reconstruct(PostState& s, Plane<F> fine, Scalar coarse, Scalar previous, Scalar lut, Plane<F> out) {
    typename Pass::Parameters p;
    p.coarseSize = coarse.size;
    s.passes.dispatch(p, Pass::FineBuffer(fine), Pass::CoarseBuffer(coarse), Pass::ReconstructedBuffer(previous),
                      Pass::RemapLut(lut), Pass::Output(out));
}
} // namespace

void LocalLaplacian2::run(PostState& s) {
    if (s.image.x < 8 || s.image.y < 8) return;
    auto lut = remapLut(s, display.localContrast);
    std::vector<Scalar> gaussian{down<down_rgb>(s, s.rgb)};
    while (gaussian.size() < maxLevels && std::max(gaussian.back().size.x, gaussian.back().size.y) > 2)
        gaussian.push_back(down<down_luma>(s, gaussian.back()));

    int last = int(gaussian.size()) - 1, start = 0;
    while (start < last - 1 && std::max(gaussian[start].size.x, gaussian[start].size.y) > 64) ++start;
    auto packedPlane = [&](Size size) { return s.scalar({anchors * size.x, size.y}); };
    std::vector<Scalar> packed(gaussian.size());
    packed[start] = packedPlane(gaussian[start].size);
    remap_packed::Parameters remap;
    remap.columnSize = gaussian[start].size;
    s.passes.dispatch(remap, remap_packed::InputBuffer(gaussian[start]), remap_packed::RemapLut(lut),
                      remap_packed::Output(packed[start]));
    for (int i = start + 1; i <= last; ++i) {
        packed[i] = packedPlane(gaussian[i].size);
        reduce_packed::Parameters p;
        p.inSize = gaussian[i - 1].size;
        s.passes.dispatch(p, reduce_packed::InputBuffer(packed[i - 1]), reduce_packed::Output(packed[i]));
    }

    auto current = gaussian[last];
    for (int i = last - 1; i >= start; --i) {
        auto out = s.scalar(gaussian[i].size);
        coarse_recon::Parameters p;
        p.fineSize = gaussian[i].size;
        p.coarseSize = gaussian[i + 1].size;
        s.passes.dispatch(p, coarse_recon::PrevRecon(current), coarse_recon::PackedFine(packed[i]),
                          coarse_recon::PackedCoarse(packed[i + 1]), coarse_recon::FineBuffer(gaussian[i]),
                          coarse_recon::Output(out));
        current = out;
    }
    for (int i = start - 1; i >= 0; --i) {
        auto out = s.scalar(gaussian[i].size);
        reconstruct<recon_luma>(s, gaussian[i], gaussian[i + 1], current, lut, out);
        current = out;
    }
    auto out = s.nextRgb();
    reconstruct<recon_rgb>(s, s.rgb, gaussian.front(), current, lut, out);
    s.rgb = out;
}
} // namespace photon::post
