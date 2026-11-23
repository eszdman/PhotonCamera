//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include "Bayer.hpp"
#include "tuning/Esd4dTunables.hpp"
#include <functional>
#include <filesystem>
#include <optional>

namespace photon::esd4d {
struct BurstFrame {
    std::vector<std::uint16_t> pixels;
    SampleFormat format = SampleFormat::SensorU16;
    float layerMultiplier = 1;
    int iso = 100;
    std::array<int, 2> cropOrigin{};
    bool binned = false;
    // Column-major raw-pixel homography from reference to this frame.
    std::optional<std::array<float, 9>> homography;
};
struct NoiseModel {
    double s = 0, o = 0;
};
struct NoiseEstimate {
    NoiseModel model;
    bool higherIsoReference = false;
};
struct NoiseCalibration {
    // The caller owns storage and binds the camera/ISO key for this burst.
    std::optional<NoiseEstimate> prior;
    std::function<std::optional<NoiseEstimate>(NoiseModel)> commit;
};
using MotionProvider = std::function<Warp(std::size_t, const BurstFrame&, const BurstFrame&)>;
struct PyramidOptions {
    enum class Precision { Half, SqrtU8, LinearU8 } precision = Precision::Half;
    float sharpness = 1;
    // Mean of the four lens-shading gains per map entry, sampled bilinearly.
    int gainWidth = 1, gainHeight = 1;
    std::vector<float> gains{1};
};
struct BurstOptions {
    Sensor sensor{};
    Esd4dTunables tunables;
    NoiseModel sensorNoise{};
    NoiseCalibration calibration;
    float mergeStrength = 0;
    bool binned = false;
    int analogIso = 100;
    // Optional external matcher (gyro, dense flow, or an existing atlas).
    // The callback receives normalized half-float Bayer pixels.
    MotionProvider motion;
    PyramidOptions pyramid;
    std::filesystem::path pipelineCacheDirectory;
};
struct BurstResult {
    std::vector<std::uint16_t> normalizedBayer;
    PackedQuads kernelMap;
    Geometry geometry;
    std::vector<std::size_t> hotPixelSites;
    NoiseModel appliedNoise;
    std::optional<NoiseModel> measuredNoise;
};
BurstResult processBurst(const BurstOptions&, std::vector<BurstFrame> frames);
BurstResult processBurst(const BurstOptions&, std::vector<BurstFrame> frames,
                         std::shared_ptr<ComputeContext> context);
} // namespace photon::esd4d
