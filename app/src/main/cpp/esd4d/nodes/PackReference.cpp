//
// Created by eszdman on 21.11.2026.
//

#include "PackReference.hpp"
#include "BurstState.hpp"

namespace photon::esd4d {

void PackReference::run(BurstState& state) {
    const auto& frame = state.frames.front();
    state.referenceExposure = 1 / frame.layerMultiplier;
    state.reference = packBayer(state.options.sensor, frame.pixels, frame.format, state.referenceExposure);
}

} // namespace photon::esd4d
