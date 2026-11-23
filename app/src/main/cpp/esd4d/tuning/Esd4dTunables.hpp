//
// Created by eszdman on 20.11.2026.
//

#pragma once

#include "../nodes/RepairHotPixels.hpp"
#include "../nodes/EstimateNoise.hpp"
#include "../nodes/StartAlignment.hpp"
#include "../nodes/MergeFrames.hpp"
#include "../nodes/GenerateKernelMap.hpp"

namespace photon::esd4d {

// One preferences/JNI snapshot. Each node declares its own annotated settings.
struct TUNABLE_GROUP("ESD4D") Esd4dTunables : RepairHotPixels::Settings,
                                             EstimateNoise::Settings,
                                             StartAlignment::Settings,
                                             MergeFrames::Settings,
                                             GenerateKernelMap::Settings {};
} // namespace photon::esd4d
