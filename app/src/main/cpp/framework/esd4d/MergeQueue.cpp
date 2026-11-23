//
// Created by eszdman on 22.11.2026.
//

#include "MergeQueue.hpp"
#include "esd4d/Bayer.hpp"
#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace photon::esd4d {
namespace {
using Objects = std::vector<std::shared_ptr<kp::Memory>>;
void publishWrites(kp::Sequence& sequence, const Objects& buffers, vk::AccessFlagBits nextAccess) {
    sequence.record<kp::OpMemoryBarrier>(
        buffers, vk::AccessFlagBits::eShaderWrite, nextAccess,
        vk::PipelineStageFlagBits::eComputeShader, vk::PipelineStageFlagBits::eComputeShader);
}
} // namespace

RobustMerge::State::State(std::shared_ptr<ComputeContext> ctx, Geometry g, const PackedQuads& base,
                          const PackedQuads& kernels, bool robust)
    : context(std::move(ctx)),
      gpu(context ? context->device() : throw std::invalid_argument("Missing compute context")),
      geometry(g), residualRobustness(robust) {
    context->checkMergeLimits(g);
    const auto width = std::uint64_t(g.rawHalfWidth) + g.shiftX;
    const auto height = std::uint64_t(g.rawHalfHeight) + g.shiftY;
    if (base.size() != width * height * 2 ||
        kernels.size() != std::uint64_t(g.kernelWidth) * g.kernelHeight * 2)
        throw std::invalid_argument("Packed quad buffer size mismatch");
    accumulator[0] = gpu.tensorT(base);
    accumulator[1] = gpu.tensorT<std::uint32_t>(base.size());
    alignedFrame = gpu.tensorT<std::uint32_t>(base.size(), kp::Memory::MemoryTypes::eStorage);
    supportWeights = gpu.tensorT<float>(base.size() / 2, kp::Memory::MemoryTypes::eStorage);
    kernelMap = gpu.tensorT(kernels);
    gpu.sequence()->eval<kp::OpSyncDevice>(Objects{accumulator[0], kernelMap});
}

RobustMerge::RobustMerge(Geometry geometry, const PackedQuads& reference, const PackedQuads& kernelMap,
                         bool robust)
    : RobustMerge(std::make_shared<ComputeContext>(), geometry, reference, kernelMap, robust) {}
RobustMerge::RobustMerge(std::shared_ptr<ComputeContext> ctx, Geometry geometry,
                         const PackedQuads& reference, const PackedQuads& kernelMap, bool robust)
    : state_(std::make_unique<State>(std::move(ctx), geometry, reference, kernelMap, robust)) {}
RobustMerge::~RobustMerge() = default;

void RobustMerge::addAligned(const PackedQuads& frame, float noiseS, float noiseO, float exposure) {
    if (frame.size() != state_->alignedFrame->size())
        throw std::invalid_argument("Invalid aligned frame size");
    std::copy(frame.begin(), frame.end(), state_->sourceForUpload());
    state_->submitCombine(nullptr, noiseS, noiseO, exposure);
    state_->wait();
}

void RobustMerge::addFrame(const PackedQuads& frame, const Warp& warp, float noiseS, float noiseO,
                           float exposure) {
    submitFrame(frame, warp, noiseS, noiseO, exposure);
    state_->wait();
}

void RobustMerge::submitFrame(const PackedQuads& frame, const Warp& warp, float noiseS, float noiseO,
                              float exposure) {
    if (frame.size() != state_->alignedFrame->size())
        throw std::invalid_argument("Invalid source frame size");
    std::copy(frame.begin(), frame.end(), state_->sourceForUpload());
    state_->submitCombine(&warp, noiseS, noiseO, exposure);
}

void RobustMerge::submitBayer(const Sensor& sensor, const std::vector<std::uint16_t>& pixels,
                              SampleFormat format, float referenceExposure, const Warp& warp,
                              float noiseS, float noiseO, float exposure) {
    const auto g = geometryFor(sensor);
    const auto& expected = state_->geometry;
    if (g.rawHalfWidth != expected.rawHalfWidth || g.rawHalfHeight != expected.rawHalfHeight ||
        g.shiftX != expected.shiftX || g.shiftY != expected.shiftY)
        throw std::invalid_argument("Sensor geometry does not match the merge");
    packBayerInto(sensor, pixels, format, referenceExposure, state_->sourceForUpload(),
                  state_->alignedFrame->size());
    state_->submitCombine(&warp, noiseS, noiseO, exposure);
}

void RobustMerge::State::uploadMotion(kp::Sequence& sequence, Slot& slot, const Warp& warp) {
    const auto area = std::int64_t(warp.fieldWidth) * warp.fieldHeight;
    if (warp.fieldWidth < 1 || warp.fieldHeight < 1 || warp.tile < 1 ||
        area > gpu.getDeviceProperties().limits.maxStorageBufferRange / 16 ||
        std::uint64_t(area) * 4 != warp.field.size() || !std::isfinite(warp.residualStrength) ||
        warp.residualStrength < 0 || warp.residualStrength > 1)
        throw std::invalid_argument("Invalid motion field or reconstruction strength");

    if (!sourceFrame)
        sourceFrame = gpu.tensorT<std::uint32_t>(alignedFrame->size(), kp::Memory::MemoryTypes::eStorage);
    if (!slot.motion || slot.motion->size() != warp.field.size())
        slot.motion = gpu.tensorT<float>(warp.field.size());
    std::copy(warp.field.begin(), warp.field.end(), slot.motion->data());
    sequence.record<kp::OpSyncDevice>(Objects{slot.motion});
}

void RobustMerge::State::recordInputUpload(kp::Sequence& sequence, Slot& slot, unsigned current,
                                           const Warp* warp) {
    if (warp) uploadMotion(sequence, slot, *warp);

    // Each slot has a private staging buffer. Both slots reuse the device buffers.
    const auto& destination = warp ? sourceFrame : alignedFrame;
    constexpr auto storageAccess = static_cast<vk::AccessFlagBits>(VK_ACCESS_SHADER_READ_BIT |
                                                                   VK_ACCESS_SHADER_WRITE_BIT);
    if (frames)
        sequence.record<kp::OpMemoryBarrier>(Objects{destination}, storageAccess,
                                             vk::AccessFlagBits::eTransferWrite,
                                             vk::PipelineStageFlagBits::eComputeShader,
                                             vk::PipelineStageFlagBits::eTransfer);
    sequence.record<kp::OpCopy>(Objects{slot.upload, destination});

    if (frames)
        publishWrites(sequence, {accumulator[current], supportWeights, alignedFrame}, storageAccess);
}

void RobustMerge::State::recordWarpBarrier(kp::Sequence& sequence) {
    publishWrites(sequence, {alignedFrame}, vk::AccessFlagBits::eShaderRead);
}

PackedQuads RobustMerge::result() const {
    auto& s = *state_;
    s.wait();
    auto latest = s.accumulator[s.frames % 2];
    if (s.frames)
        s.gpu.sequence()->eval<kp::OpSyncLocal>(Objects{latest});
    return latest->vector();
}
} // namespace photon::esd4d
