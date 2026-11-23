//
// Created by eszdman on 22.11.2026.
//

#pragma once
#include "esd4d/Merge.hpp"
#include "TensorPass.hpp"

namespace photon::esd4d {
struct RobustMerge::State {
    std::shared_ptr<ComputeContext> context;
    kp::Manager& gpu;
    Geometry geometry;
    const bool residualRobustness;
    std::shared_ptr<kp::TensorT<std::uint32_t>> kernelMap;
    std::array<std::shared_ptr<kp::TensorT<std::uint32_t>>, 2> accumulator;
    std::shared_ptr<kp::TensorT<std::uint32_t>> alignedFrame;
    std::shared_ptr<kp::TensorT<float>> supportWeights;
    std::shared_ptr<kp::TensorT<std::uint32_t>> sourceFrame;
    std::uint32_t frames = 0;
    // The reference already contributes one long exposure to the running average.
    std::uint32_t longCount = 2, shortCount = 1;

    // Each slot owns the host writes and descriptors for one accumulator parity.
    // Both slots submit to the same compute queue in exposure order.
    struct Slot {
        std::shared_ptr<kp::TensorT<std::uint32_t>> upload;
        std::shared_ptr<kp::TensorT<float>> motion;
        TensorPass warp, combine;
        std::shared_ptr<kp::Sequence> pending;

        void wait() {
            if (pending) {
                pending->evalAwait();
                pending.reset();
            }
        }
    };
    std::array<Slot, 2> slots;

    void wait() {
        for (auto& slot : slots) slot.wait();
    }
    ~State() {
        // A later frame can fail after either slot has submitted.
        for (auto& slot : slots) {
            try { slot.wait(); } catch (...) { /* Device lost during teardown. */ }
        }
    }
    std::uint32_t* sourceForUpload() {
        auto& slot = slots[frames % slots.size()];
        slot.wait();
        if (!slot.upload)
            slot.upload = gpu.tensorT<std::uint32_t>(alignedFrame->size(), kp::Memory::MemoryTypes::eHost);
        return slot.upload->data();
    }
    void uploadMotion(kp::Sequence&, Slot&, const Warp&);
    void recordWarp(kp::Sequence&, Slot&, unsigned current, const Warp&, float exposure);
    void recordInputUpload(kp::Sequence&, Slot&, unsigned current, const Warp*);
    void recordWarpBarrier(kp::Sequence&);
    void submitCombine(const Warp* warp, float noiseS, float noiseO, float exposure);

    State(std::shared_ptr<ComputeContext>, Geometry, const PackedQuads& base,
          const PackedQuads& kernels, bool robust);
};
} // namespace photon::esd4d
