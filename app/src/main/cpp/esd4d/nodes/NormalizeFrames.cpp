//
// Created by eszdman on 21.11.2026.
//

#include "NormalizeFrames.hpp"
#include "BurstState.hpp"
#include "framework/esd4d/ComputeContext.hpp"
#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace photon::esd4d {

namespace {
Geometry validateInputs(const BurstState& state) {
    const auto& options = state.options;
    if (!state.context) throw std::invalid_argument("Missing compute context");
    if (options.tunables.alignDebugCompare || options.tunables.profileGpuSync)
        throw std::invalid_argument("Selected ESD4D diagnostics require GLES");
    if (options.tunables.alignerSelect == AlignerSelect::flownet && !options.tunables.gyroOnly && !options.motion)
        throw std::invalid_argument("FlowNet alignment requires a motion provider");
    const auto& sensor = options.sensor;
    const auto geometry = geometryFor(sensor);
    if (state.frames.empty() || !std::isfinite(sensor.white) || !std::isfinite(options.sensorNoise.s) ||
        options.sensorNoise.s < 0 || !std::isfinite(options.sensorNoise.o) || options.sensorNoise.o < 0)
        throw std::invalid_argument("Invalid burst or sensor noise model");
    if (!std::all_of(sensor.black.begin(), sensor.black.end(),
                     [&](float b) { return std::isfinite(b) && b < sensor.white; }) ||
        !std::isfinite(std::exp2(options.mergeStrength)) ||
        !std::isfinite(options.tunables.noiseMpy) || options.tunables.noiseMpy < 0)
        throw std::invalid_argument("Invalid sensor levels or merge settings");
    return geometry;
}
} // namespace

void NormalizeFrames::run(BurstState& state) {
    state.result.geometry = validateInputs(state);
    for (auto& frame : state.frames) {
        if (!std::isfinite(frame.layerMultiplier) || frame.layerMultiplier <= 0 ||
            !std::isfinite(1 / frame.layerMultiplier))
            throw std::invalid_argument("Invalid exposure multiplier");
        normalizeBayer(state.options.sensor, frame.pixels, frame.format);
        frame.format = SampleFormat::NormalizedF16;
    }
    state.context->checkMergeLimits(state.result.geometry);
}

} // namespace photon::esd4d
