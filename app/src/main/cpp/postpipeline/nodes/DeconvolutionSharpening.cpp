//
// Created by eszdman on 21.11.2026.
//

#include "PostNodes.hpp"
#include "rl_initialize0.hpp"
#include "rl_initialize1.hpp"
#include "rl_mask0.hpp"
#include "rl_mask1.hpp"
#include "rl_smoothmask0.hpp"
#include "rl_smoothmask1.hpp"
#include "rl_finish0.hpp"
#include "rl_finish1.hpp"
#include "rl_iterate0.hpp"
#include "rl_iterate1.hpp"
#include "rl_horizontal.hpp"
#include "rl_divide.hpp"
#include "rl_multiply.hpp"
#include <cmath>

namespace photon::post {
using namespace shaders;
namespace {
struct Gaussian {
    float sigma;
    int radius;
    float center, pair, offset, corner;

    explicit Gaussian(float s)
        : sigma(std::clamp(s, .2f, 2.f)), radius(sigma < .6f      ? 1
                                                 : sigma <= .84f  ? 2
                                                 : sigma <= 1.15f ? 3
                                                 : sigma <= 1.5f  ? 4
                                                                  : 6) {
        double g1 = weight(1), g2 = radius == 2 ? weight(4) : 0, sum = 1 + 2 * (g1 + g2);
        center = float(1 / sum);
        pair = float((g1 + g2) / sum);
        offset = float((g1 + 2 * g2) / (g1 + g2));
        corner = float((g2 / sum) * (g2 / sum));
    }
    double weight(int squared) const { return std::exp(-squared / (2. * sigma * sigma)); }
    std::vector<float> circularWeights() const {
        double support = radius == 3 ? 11.9025 : radius == 4 ? 20.25 : 36;
        std::vector<double> values;
        double sum = 0;
        for (int y = -radius; y <= radius; ++y)
            for (int x = -radius; x <= radius; ++x) {
                int r = x * x + y * y;
                values.push_back(r <= support ? weight(r) : 0.);
                sum += values.back();
            }
        std::vector<float> result;
        for (double w : values) result.push_back(float(std::round(w / sum * 1e10) / 1e10));
        return result;
    }
    template <class Pass> typename Pass::Parameters packed(Size size) const {
        typename Pass::Parameters p;
        p.validSize = size;
        p.CENTER_WEIGHT = center;
        p.PAIR_WEIGHT = pair;
        p.PAIR_OFFSET = offset;
        return p;
    }
};
struct Scratch {
    Rgba original, ratio, estimateA, estimateB;
};

Rgba solvePacked(PostState& s, Size size, Size grid, const Scratch& b, const Gaussian& g, int iterations) {
    auto horizontal = g.packed<rl_horizontal>(size);
    auto divide = g.packed<rl_divide>(size);
    divide.CORNER_WEIGHT = g.corner;
    auto multiply = g.packed<rl_multiply>(size);
    multiply.CORNER_WEIGHT = g.corner;
    auto blur = [&](Rgba source) {
        s.passes.compute(grid, horizontal, rl_horizontal::SourceBuffer(source),
                         rl_horizontal::CornerBuffer(source), rl_horizontal::OutputBuffer(b.estimateB));
    };
    auto estimate = b.original;
    for (int i = 0; i < iterations; ++i) {
        blur(estimate);
        s.passes.compute(grid, divide, rl_divide::SourceBuffer(b.estimateB), rl_divide::CornerBuffer(estimate),
                         rl_divide::OriginalBuffer(b.original), rl_divide::OutputBuffer(b.ratio));
        blur(b.ratio);
        multiply.firstIteration = (i == 0);
        s.passes.compute(grid, multiply, rl_multiply::SourceBuffer(b.estimateB),
                         rl_multiply::CornerBuffer(b.ratio), rl_multiply::OriginalBuffer(b.original),
                         rl_multiply::EstimateBuffer(b.estimateA), rl_multiply::OutputBuffer(b.estimateA));
        estimate = b.estimateA;
    }
    return estimate;
}

Rgba solveCircular(PostState& s, Size size, const Scratch& b, Plane<Format::words> weights, int radius,
                   int iterations) {
    auto estimate = b.original;
    rl_iterate0::Parameters divide;
    divide.validSize = size;
    divide.radius = radius;
    rl_iterate1::Parameters update;
    update.validSize = size;
    update.radius = radius;
    for (int i = 0; i < iterations; ++i) {
        s.passes.compute(size, divide, rl_iterate0::SourceBuffer(estimate),
                         rl_iterate0::OriginalBuffer(b.original), rl_iterate0::KernelWeights(weights),
                         rl_iterate0::OutputBuffer(b.ratio));
        auto next = estimate.tensor == b.estimateA.tensor ? b.estimateB : b.estimateA;
        s.passes.compute(size, update, rl_iterate1::SourceBuffer(b.ratio), rl_iterate1::EstimateBuffer(estimate),
                         rl_iterate1::KernelWeights(weights), rl_iterate1::OutputBuffer(next));
        estimate = next;
    }
    return estimate;
}

template <bool packed>
void renderBands(PostState& s, const Gaussian& g, const settings::DeconvolutionSharpening& t, float strength) {
    using Init = std::conditional_t<packed, rl_initialize1, rl_initialize0>;
    using Mask = std::conditional_t<packed, rl_mask1, rl_mask0>;
    using Smooth = std::conditional_t<packed, rl_smoothmask1, rl_smoothmask0>;
    using Finish = std::conditional_t<packed, rl_finish1, rl_finish0>;
    int iterations = std::clamp(t.iterations, 0, 10), halo = std::max(6, 2 * g.radius * iterations);
    int width = packed ? (s.image.x + 3) / 4 : s.image.x;
    Size scratchSize{width, std::min(s.image.y, 512 + 2 * halo)};
    Scratch b{s.rgba(scratchSize), s.rgba(scratchSize), s.rgba(scratchSize), s.rgba(scratchSize)};
    Plane<Format::words> weights;
    if constexpr (!packed) {
        auto kernel = g.circularWeights();
        weights = {s.gpu.tensorT(kernel), {int(kernel.size()), 1}};
        s.upload({weights.tensor});
    }
    bool maskNeeded = t.contrastRatio > 0 || s.settings.noiseS > 0 || s.settings.noiseO > 0;
    auto output = s.nextRgb();
    for (int y = 0; y < s.image.y; y += 512) {
        int origin = std::max(0, y - halo), rows = std::min(512, s.image.y - y);
        Size valid{s.image.x, std::min(s.image.y, y + rows + halo) - origin}, grid{width, valid.y};
        typename Init::Parameters init;
        init.originY = origin;
        init.validSize = valid;
        s.passes.compute(grid, init, Init::InputBuffer(s.rgb), Init::OutputBuffer(b.original));
        Rgba estimate;
        if constexpr (packed)
            estimate = solvePacked(s, valid, grid, b, g, iterations);
        else
            estimate = solveCircular(s, valid, b, weights, g.radius, iterations);
        if (maskNeeded) {
            typename Mask::Parameters mask;
            mask.validSize = valid;
            mask.originY = origin;
            mask.contrastRatio = t.contrastRatio;
            mask.noiseModel = {s.settings.noiseS, s.settings.noiseO};
            mask.clipLevel = s.rawClipLevel;
            s.passes.compute(grid, mask, Mask::InputBuffer(s.rgb), Mask::OutputBuffer(b.original));
            typename Smooth::Parameters smooth;
            smooth.validSize = valid;
            s.passes.compute(grid, smooth, Smooth::MaskBuffer(b.original), Smooth::OutputBuffer(b.ratio));
        }
        typename Finish::Parameters finish;
        finish.validSize = valid;
        finish.outputY = y;
        finish.outputRows = rows;
        finish.originY = origin;
        finish.amount = strength;
        finish.useMask = maskNeeded;
        finish.clipLevel = s.rawClipLevel;
        s.passes.compute({s.image.x, rows}, finish, Finish::InputBuffer(s.rgb), Finish::EstimateBuffer(estimate),
                         Finish::MaskBuffer(b.ratio), Finish::LocalMaskBuffer(b.original),
                         Finish::OutputBuffer(output));
    }
    s.rgb = output;
}
} // namespace

void DeconvolutionSharpening::run(PostState& s) {
    Gaussian kernel(tuning_.radius);
    if (kernel.radius <= 2)
        renderBands<true>(s, kernel, tuning_, strength_);
    else
        renderBands<false>(s, kernel, tuning_, strength_);
}
} // namespace photon::post
