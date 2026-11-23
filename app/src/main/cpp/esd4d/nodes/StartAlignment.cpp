//
// Created by eszdman on 21.11.2026.
//

#include "StartAlignment.hpp"
#include "BurstState.hpp"

namespace photon::esd4d {

void StartAlignment::run(BurstState& state) {
    state.alignment.emplace(state.options, state.frames, state.result.appliedNoise);
}

} // namespace photon::esd4d
