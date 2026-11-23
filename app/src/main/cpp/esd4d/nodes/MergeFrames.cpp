//
// Created by eszdman on 21.11.2026.
//

#include "MergeFrames.hpp"
#include "BurstState.hpp"
#include <algorithm>
#include <cmath>

namespace photon::esd4d {

void MergeFrames::run(BurstState& state) {
    const auto& options = state.options;
    const auto& geometry = state.result.geometry;
    RobustMerge merge(state.context, geometry, state.takePackedReference(), state.result.kernelMap,
                      state.settings<MergeFrames>().cfaResidualRobustness);

    auto& alignment = state.prepareAlignmentForMerge();
    const auto noise = state.result.appliedNoise;
    const float strength = std::exp2(options.mergeStrength);
    const float noiseS = float(std::max(noise.s * strength, 1e-10));
    const float noiseO = float(std::max(noise.o * strength, 1e-10));
    while (auto matched = alignment.awaitNextFrame()) {
        const auto& frame = matched->frame;
        merge.submitBayer(options.sensor, frame.pixels, frame.format, state.referenceExposure,
                          matched->warp, noiseS, noiseO, 1 / frame.layerMultiplier);
    }
    state.result.normalizedBayer = unpackBayer(geometry, merge.result());
}

} // namespace photon::esd4d
