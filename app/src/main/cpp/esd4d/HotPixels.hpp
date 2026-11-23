//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include "Burst.hpp"

namespace photon::esd4d {
// All frames must be normalized and share the same sensor-coordinate mapping.
std::vector<std::size_t> correctHotPixels(ComputeContext&, const Sensor&, std::vector<BurstFrame>&,
                                          NoiseModel, const RepairHotPixels::Settings&);
} // namespace photon::esd4d
