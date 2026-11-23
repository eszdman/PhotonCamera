//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include "Burst.hpp"
#include <string>

namespace photon::esd4d {
// Load the optional ncnn model once and reuse it across equal-geometry bursts.
MotionProvider makeFlowNetMotion(Sensor, const std::string& parameters, const std::string& weights,
                                 int threads = 4);
} // namespace photon::esd4d
