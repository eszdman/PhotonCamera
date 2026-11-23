//
// Created by eszdman on 19.11.2026.
//

#include "framework/esd4d/MergeQueue.hpp"
#include "EsdShaders.hpp"
#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace photon::esd4d {
namespace {
ShaderAsset<shaders::warp> warpShader(const Warp& warp) {
    using namespace assets;
    auto fieldVariant = [&](auto tiles, auto interpolated, auto residual) {
        if (warp.residualStrength > 0) return residual;
        return warp.interpolateMotion ? interpolated : tiles;
    };
    switch (warp.sampling) {
    case Sampling::FlowNet: return warp_flow;
    case Sampling::Gyro: return warp_gyro;
    case Sampling::Floor: return warp_floor;
    case Sampling::Nearest: return fieldVariant(warp_1_0, warp_1_1, warp_1_residual);
    case Sampling::Bilinear: return fieldVariant(warp_2_0, warp_2_1, warp_2_residual);
    case Sampling::Cubic: return fieldVariant(warp_3_0, warp_3_1, warp_3_residual);
    case Sampling::Lanczos2: return fieldVariant(warp_4_0, warp_4_1, warp_4_residual);
    }
    throw std::invalid_argument("Unknown CFA sampler");
}
} // namespace

void RobustMerge::State::recordWarp(kp::Sequence& sequence, Slot& slot, unsigned current,
                                    const Warp& warp, float exposure) {
    using Shader = shaders::warp;
    auto program = slot.warp.useProgram(gpu, sequence, warpShader(warp),
        geometry.rawHalfWidth + geometry.shiftX, geometry.rawHalfHeight + geometry.shiftY);
    Shader::Parameters params{};
    params.rawHalf = {geometry.rawHalfWidth, geometry.rawHalfHeight};
    params.cfaShift = {geometry.shiftX, geometry.shiftY};
    params.alignmentSize = {warp.fieldWidth, warp.fieldHeight};
    params.tileSize = warp.tile;
    params.exposure = exposure;
    params.cfaResidualStrength = warp.residualStrength;
    params.cfaNoiseS = warp.sensorNoiseS;
    params.cfaNoiseO = warp.sensorNoiseO;
    for (int col = 0; col < 3; ++col)
        std::copy_n(warp.homography.data() + 3 * col, 3, params.gyroHomography[col].begin());
    program.setVars(params);
    program.bindInputs(Shader::Source(sourceFrame), Shader::Accumulator(accumulator[current]),
                       Shader::Motion(slot.motion)).writeTo(Shader::Destination(alignedFrame));
}

// One combine per alternate frame. sourceForUpload() has already waited for this slot.
void RobustMerge::State::submitCombine(const Warp* warp, float noiseS, float noiseO, float exposure) {
    if (!std::isfinite(noiseS) || noiseS < 0 ||
        !std::isfinite(noiseO) || noiseO < 0 || !std::isfinite(exposure) || exposure <= 0)
        throw std::invalid_argument("Invalid frame or noise/exposure parameters");

    const unsigned current = frames % slots.size();
    auto& slot = slots[current];
    auto& exposureCount = exposure >= 0.95f ? longCount : shortCount;
    auto sequence = gpu.sequence();
    recordInputUpload(*sequence, slot, current, warp);
    if (warp) {
        recordWarp(*sequence, slot, current, *warp, exposure);
        recordWarpBarrier(*sequence);
    }

    using Shader = shaders::combine;
    const auto asset = residualRobustness ? assets::combine : assets::combine_scheduled;
    auto program = slot.combine.useProgram(gpu, *sequence, asset,
        geometry.rawHalfWidth + geometry.shiftX, geometry.rawHalfHeight + geometry.shiftY);
    Shader::Parameters params{};
    params.rawHalf = {geometry.rawHalfWidth, geometry.rawHalfHeight};
    params.cfaShift = {geometry.shiftX, geometry.shiftY};
    params.kernelSize = {geometry.kernelWidth, geometry.kernelHeight};
    params.noiseS = noiseS;
    params.noiseO = noiseO;
    params.exposure = exposure;
    params.frameIndex = frames + 1;
    params.weight = 1.f / exposureCount;
    program.setVars(params);
    program.bindInputs(Shader::Base(accumulator[current]), Shader::Aligned(alignedFrame),
                       Shader::Support(supportWeights), Shader::KernelMap(kernelMap))
           .writeTo(Shader::Output(accumulator[1 - current]));
    slot.pending = sequence->evalAsync();
    ++exposureCount;
    ++frames;
}

} // namespace photon::esd4d
