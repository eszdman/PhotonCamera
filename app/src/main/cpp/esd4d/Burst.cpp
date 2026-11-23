//
// Created by eszdman on 19.11.2026.
//

#include "Burst.hpp"
#include "BurstState.hpp"
#include "nodes/NormalizeFrames.hpp"
#include "nodes/EstimateNoise.hpp"
#include "nodes/RepairHotPixels.hpp"
#include "nodes/PackReference.hpp"
#include "nodes/StartAlignment.hpp"
#include "nodes/GenerateKernelMap.hpp"
#include "nodes/MergeFrames.hpp"
#include "framework/esd4d/ComputeContext.hpp"
#include "framework/kompute/KomputePipeline.hpp"
#include <utility>

namespace photon::esd4d {
namespace {
KomputePipeline<BurstState> buildDefaultPipeline() {
    KomputePipeline<BurstState> pipeline;
    pipeline.add<NormalizeFrames>();
    pipeline.add<EstimateNoise>();
    pipeline.add<RepairHotPixels>();
    pipeline.add<PackReference>();
    pipeline.add<StartAlignment>();
    pipeline.add<GenerateKernelMap>();
    pipeline.add<MergeFrames>();
    return pipeline;
}
} // namespace

BurstResult processBurst(const BurstOptions& options, std::vector<BurstFrame> frames) {
    auto context = std::make_shared<ComputeContext>(options.pipelineCacheDirectory);
    return processBurst(options, std::move(frames), std::move(context));
}

BurstResult processBurst(const BurstOptions& options, std::vector<BurstFrame> frames,
                         std::shared_ptr<ComputeContext> context) {
    BurstState state{options, frames, std::move(context)};
    buildDefaultPipeline().runAll(state);
    return std::move(state.result);
}

} // namespace photon::esd4d
