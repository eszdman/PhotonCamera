//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include "Burst.hpp"
namespace photon::esd4d {
struct CalibratedNoise {
    NoiseModel applied;
    std::optional<NoiseModel> measured;
};
CalibratedNoise calibrateNoise(ComputeContext&, const BurstOptions&, const std::vector<BurstFrame>&);
} // namespace photon::esd4d
