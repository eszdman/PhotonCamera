//
// Created by eszdman on 21.11.2026.
//

#include "BurstAlignment.hpp"
#include "Alignment.hpp"
#include "Pyramid.hpp"
#include <atomic>
#include <future>
#include <stdexcept>
#include <utility>

namespace photon::esd4d {
namespace {
std::vector<std::size_t> mergeOrder(const std::vector<BurstFrame>& frames) {
    std::size_t shortest = 0;
    float minimum = 1;
    for (std::size_t i = 1; i < frames.size(); ++i) {
        float exposure = 1 / frames[i].layerMultiplier;
        if (exposure < minimum) {
            shortest = i;
            minimum = exposure;
        }
    }
    std::vector<std::size_t> order;
    order.reserve(frames.size() - 1);
    if (shortest) order.push_back(shortest);
    for (std::size_t i = 1; i < frames.size(); ++i)
        if (i != shortest) order.push_back(i);
    return order;
}

#ifdef ESD4D_HAS_PRECISE_ALIGNMENT
Sampling samplingFor(HalideMergeSampling mode) {
    switch (mode) {
    case HalideMergeSampling::texel: return Sampling::Floor;
    case HalideMergeSampling::nearest: return Sampling::Nearest;
    case HalideMergeSampling::bilinear: return Sampling::Bilinear;
    case HalideMergeSampling::cubic: return Sampling::Cubic;
    case HalideMergeSampling::lanczos2: return Sampling::Lanczos2;
    }
    throw std::invalid_argument("Unknown Halide merge sampler");
}

Warp estimateHalideWarp(const PreciseAlignment& align, const BurstOptions& options,
                        const BurstFrame& frame, NoiseModel noise) {
    auto prewarped = frame.homography ? prewarpBayer(options.sensor, frame.pixels, *frame.homography)
                                      : std::vector<std::uint16_t>{};
    auto warp = align.estimate(frame.homography ? prewarped : frame.pixels, 1 / frame.layerMultiplier,
                               float(noise.s), float(noise.o), options.tunables.halideCoarseGlobal);
    warp.sampling = samplingFor(options.tunables.halideMergeSampling);
    warp.interpolateMotion = options.tunables.halideInterpolateMotion;
    warp.residualStrength = options.tunables.cfaResidualFusion;
    return warp;
}
#endif

AlignerSelect selectMatcher(const BurstOptions& options) {
    auto matcher = options.tunables.gyroOnly ? AlignerSelect::off : options.tunables.alignerSelect;
#ifdef ESD4D_HAS_PRECISE_ALIGNMENT
    const auto& sensor = options.sensor;
    // The AOT matcher vectorizes four output tiles at raw/4 (tile 16, stride 8).
    if (matcher == AlignerSelect::halide && (sensor.width / 4 < 16 + 3 * 8 || sensor.height < 32))
        matcher = AlignerSelect::gl;
#else
    if (matcher == AlignerSelect::halide)
        matcher = AlignerSelect::gl;
#endif
    return matcher;
}
} // namespace

#ifdef ESD4D_HAS_PRECISE_ALIGNMENT
// Only motion fields cross threads. A field becomes ready after the last CPU
// read of its frame, so the merge can immediately release the Bayer buffer.
class BurstAlignment::HalideWorker {
    std::atomic<bool> stop_{false};
    std::vector<std::future<Warp>> fields_;
    std::future<void> worker_;

  public:
    HalideWorker(const BurstOptions& options, std::vector<BurstFrame>& frames,
                 const std::vector<std::size_t>& order, NoiseModel noise) {
        std::vector<std::promise<Warp>> slots(order.size());
        for (auto& slot : slots) fields_.push_back(slot.get_future());
        worker_ = std::async(std::launch::async, [this, &options, &frames, &order, noise,
                            reference = std::move(frames.front().pixels), slots = std::move(slots)]() mutable {
            std::size_t i = 0;
            try {
                const auto& sensor = options.sensor;
                PreciseAlignment align(sensor.width, sensor.height, reference, options.tunables.halideAccurateSubpixel,
                                       options.tunables.halideRefineFlow, noise.s > 0 || noise.o > 0);
                std::vector<std::uint16_t>().swap(reference);
                for (; i < order.size() && !stop_.load(); ++i)
                    slots[i].set_value(estimateHalideWarp(align, options, frames[order[i]], noise));
            } catch (...) {
                slots[i].set_exception(std::current_exception());
            }
        });
    }
    Warp get(std::size_t i) { return fields_[i].get(); }
    ~HalideWorker() {
        stop_.store(true);
        worker_.wait();
    }
};
#endif

BurstAlignment::BurstAlignment(const BurstOptions& options, std::vector<BurstFrame>& frames, NoiseModel noise)
    : options_(options), frames_(frames), noise_(noise), matcher_(selectMatcher(options)), order_(mergeOrder(frames)) {
#ifdef ESD4D_HAS_PRECISE_ALIGNMENT
    if (!order_.empty() && !options_.motion && matcher_ == AlignerSelect::halide)
        halide_ = std::make_unique<HalideWorker>(options_, frames_, order_, noise_);
#endif
}

BurstAlignment::~BurstAlignment() = default;

void BurstAlignment::prepareForMerge(ComputeContext& context) {
    if (!order_.empty() && !options_.motion && matcher_ == AlignerSelect::gl)
        blocks_ = std::make_unique<BlockAlignment>(context, options_, frames_.front(), noise_);
    // An external motion provider can read the reference on every invocation.
    if (!options_.motion)
        std::vector<std::uint16_t>().swap(frames_.front().pixels);
}

Warp BurstAlignment::warpAt(std::size_t position) {
    const auto index = order_[position];
    const auto& frame = frames_[index];
    Warp warp;
    if (options_.motion)
        warp = options_.motion(index, frames_.front(), frame);
    else if (blocks_)
        warp = blocks_->estimate(frame);
#ifdef ESD4D_HAS_PRECISE_ALIGNMENT
    else if (halide_)
        warp = halide_->get(position);
#endif
    if (!options_.motion && !blocks_ && frame.homography) {
        warp.sampling = Sampling::Gyro;
        warp.homography = *frame.homography;
    }
    warp.sensorNoiseS = float(noise_.s);
    warp.sensorNoiseO = float(noise_.o);
    return warp;
}

std::optional<MatchedFrame> BurstAlignment::awaitNextFrame() {
    if (nextPosition_ == order_.size()) return std::nullopt;
    auto warp = warpAt(nextPosition_);
    auto& frame = frames_[order_[nextPosition_++]];
    return MatchedFrame{std::move(frame), std::move(warp)};
}
} // namespace photon::esd4d
