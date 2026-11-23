//
// Created by eszdman on 21.11.2026.
//

#include "PostNodes.hpp"
#include "Histogram.hpp"
#include "rgb_histogram.hpp"
#include "levelcorrection.hpp"
#include <algorithm>
#include <cmath>
#include <numeric>

namespace photon::post {
using namespace shaders;
std::vector<uint32_t> rgbHistogram(PostState& s, int bins, std::array<float, 3> exposure, float offset) {
    if (bins < 16 || bins > 16384) throw std::invalid_argument("Invalid post histogram size");
    auto counts = s.gpu.tensorT(std::vector<uint32_t>(3 * bins, 0));
    s.upload({counts});
    rgb_histogram::Parameters p;
    p.bins = bins;
    for (int c = 0; c < 3; ++c) p.exposure[c] = exposure[c] * float(bins - 1);
    p.binOffset = offset;
    Size grid{(s.rgb.size.x + 2) / 3, (s.rgb.size.y + 2) / 3};
    s.passes.compute(grid, p, rgb_histogram::InputBuffer(s.rgb), rgb_histogram::Histogram({counts, {bins, 3}}));
    s.download({counts});
    return counts->vector();
}

void ABLC::run(PostState& s) {
    const auto& t = s.settings.blackLevels;
    const int bins = t.histSize;
    const double noise = std::sqrt(s.settings.noiseO) * std::exp2(t.noiseEV);
    const double floor = std::exp2(-t.maxEV), gain = 1 / (16 * noise + floor);
    const float g = float(gain);
    auto hist = rgbHistogram(s, bins, {g, g, g}, float(4 * noise * gain * (bins - 1)));
    levelcorrection::Parameters p;
    for (int c = 0; c < 3; ++c) {
        auto begin = hist.begin() + c * bins;
        auto target = std::accumulate(begin, begin + bins, uint64_t(0)) / 100;
        uint64_t sum = 0;
        for (int i = 0; i < bins; ++i) {
            sum += begin[i];
            if (sum >= target) {
                double black = float(i) / float(bins - 1) - (4 - 2.326) * noise * gain;
                p.blackLevel[c] = float(std::clamp(black, 0., t.minExposureMpy * (noise + floor) * gain)) / g;
                break;
            }
        }
    }
    auto output = s.nextRgb();
    s.passes.dispatch(p, levelcorrection::InputBuffer(s.rgb), levelcorrection::Output(output));
    s.rgb = output;
}
} // namespace photon::post
