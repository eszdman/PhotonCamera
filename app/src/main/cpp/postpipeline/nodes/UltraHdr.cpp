//
// Created by eszdman on 21.11.2026.
//

#include "PostNodes.hpp"
#include "bicubic.hpp"
#include "scene_luma.hpp"
#include "gainmap_histogram.hpp"
#include "hdr_gainmap.hpp"
#include <cmath>
#include <numeric>

namespace photon::post {
using namespace shaders;

void CaptureSceneLuma::run(PostState& s) {
    auto input = s.rgb;
    if (target_.x != s.captureSize.x || target_.y != s.captureSize.y) {
        auto expanded = s.rgba(target_);
        bicubic::Parameters p;
        p.size = target_;
        s.passes.dispatch(p, bicubic::InputBuffer(input), bicubic::Output(expanded));
        input = expanded;
    }
    Size oriented = target_;
    if (display_.rotationDegrees % 180) std::swap(oriented.x, oriented.y);
    s.sceneLuma = s.scalar(oriented);
    scene_luma::Parameters p;
    p.rotate = (4 - display_.rotationDegrees / 90) % 4;
    p.mirror = display_.mirror;
    p.rawSize = s.image;
    p.cropSize = s.captureSize;
    p.uLinFullSize = p.uLinGridSize = oriented;
    p.uInFull = input.size;
    s.passes.dispatch(p, scene_luma::InputBuffer(input), scene_luma::GainMap(s.gainMap),
                      scene_luma::Output(s.sceneLuma));
}

namespace {
float median(const uint32_t* bins) {
    auto total = std::accumulate(bins, bins + 256, uint64_t(0));
    if (!total) return 0;
    auto left = total / 2;
    for (int i = 255; i >= 0; --i) {
        if (left < bins[i]) return float((i + 1 - double(left) / bins[i]) / 255.);
        left -= bins[i];
    }
    return 1;
}
} // namespace
void RenderGainMap::run(PostState& s) {
    if (!(scale_ > 0) || !std::isfinite(scale_)) throw std::invalid_argument("Invalid HDR gain map scale");
    hdr_gainmap::Parameters p;
    if (tone_.mode == ToneMode::sky) {
        float gain = std::max(1.f, s.displayGain);
        p.uAnchor = gain / std::clamp(tone_.sky.headroomScale * gain, 1.f, tone_.sky.sceneWhiteMax);
    } else {
        auto counts = s.gpu.tensorT(std::vector<uint32_t>(512));
        s.upload({counts});
        s.passes.compute(base_.size, {}, gainmap_histogram::Base(base_), gainmap_histogram::Scene(s.sceneLuma),
                         gainmap_histogram::Medians({counts, {256, 2}}));
        s.download({counts});
        float mid = std::clamp(median(counts->data()), 0.f, 1.f);
        float linear = mid <= .04045f ? mid / 12.92f : std::pow((mid + .055f) / 1.055f, 2.4f);
        p.uAnchor = linear / std::max(.0001f, median(counts->data() + 256));
    }
    p.uScale = scale_;
    p.uDown = 1;
    p.uEps = 1.f / 64;
    auto output = s.gpu.tensorT<uint32_t>(size_t(base_.size.x) * base_.size.y);
    s.passes.dispatch(p, hdr_gainmap::InputBuffer(base_), hdr_gainmap::LBuffer(s.sceneLuma),
                      hdr_gainmap::Output({output, base_.size}));
    s.download({output});
    output_ = {base_.size, output->vector()};
}
} // namespace photon::post
