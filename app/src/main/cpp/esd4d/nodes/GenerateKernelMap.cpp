//
// Created by eszdman on 21.11.2026.
//

#include "GenerateKernelMap.hpp"
#include "BurstState.hpp"
#include "KernelMap.hpp"
#include <cmath>

namespace photon::esd4d {

void GenerateKernelMap::run(BurstState& state) {
    const auto& geometry = state.result.geometry;
    const auto noise = state.result.appliedNoise;
    const auto luma = kernelLuma(geometry, state.reference);
    const float sigma = state.settings<GenerateKernelMap>().noiseMpy * std::sqrt(.5 * noise.s + noise.o);
    state.result.kernelMap = inferKernelMap(*state.context, luma, kernelLumaWidth(geometry),
                                            geometry.rawHalfHeight + geometry.shiftY, sigma);
}

} // namespace photon::esd4d
