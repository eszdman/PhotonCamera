//
// Created by eszdman on 21.11.2026.
//

#pragma once
#include "framework/esd4d/ComputeContext.hpp"
#include "PostSettings.hpp"
#include "nodes/ESD3D2.hpp"
#include <array>
#include <cstdint>
#include <vector>
#include <optional>
#include <unordered_map>

namespace photon::post {
struct Size {
    std::int32_t x, y;
};
enum class Demosaicing { amaze, legacy, quad, monochrome, mergedRgb };

struct LinearSettings {
    settings::ABLC blackLevels;
    Demosaicing demosaicing = Demosaicing::amaze;
    int tileSize = 1024;
    float noiseS = 0, noiseO = 0, quadGradSize = 1.5f;
    std::array<float, 3> whitePoint{1, 1, 1};
    std::array<float, 4> monoBlackLevel{};
    bool denoise = false;
    ESD3D2 esd3d;
};

struct RgbaImage {
    Size size;
    std::vector<std::uint32_t> pixels;
};
// One packed little-endian RGBA8 word per pixel, as in the Android bitmap sink.
struct SdrImage {
    Size size;
    std::vector<std::uint32_t> pixels;
};
struct DisplaySettings {
    settings::LocalLaplacian2 localContrast;
    settings::Sharpen2 sharpen;
    bool unsharp = false, captureSharpening = false, mirror = false;
    float sharpness = 0, cropSharpenScale = settings::PostPipeline{}.upscaleSharpenScale, captureRadius = 1,
          captureIntensity = 0;
    std::array<float, 8> aberration{};
    int rotationDegrees = 0;
    RgbaImage watermark{};
};
// Starts after tone mapping and crop expansion, returns the oriented SDR image.
SdrImage finishDisplay(esd4d::ComputeContext&, const RgbaImage&, const DisplaySettings&, float noiseS = 0,
                       float noiseO = 0);

struct BayerSettings : settings::Bayer2Float {
    int cfaPattern = 0;
    Size offset{};
    Size mapSize{1, 1};
    // Row-major RGBA16F lens shading grid, two words per texel (R,G1,G2,B).
    std::vector<std::uint32_t> gainMap{0x3c003c00, 0x3c003c00};
};

enum class ToneMode { curve, modern, sky, off };
struct ToneSettings {
    ToneMode mode = ToneMode::curve;
    settings::AutoExposureCurve exposure;
    settings::LinearExposure linear;
    settings::Initial initial;
    settings::ModernInitial modern;
    settings::HeadroomRender sky;
    // Row-major camera color transforms in the same order as GLProg.setVar.
    std::array<float, 9> sensorToIntermediate{1, 0, 0, 0, 1, 0, 0, 0, 1};
    std::array<float, 9> intermediateToSRGB{1, 0, 0, 0, 1, 0, 0, 0, 1};
    // Shot-resolved matrices, as passed to Initial.renderInitialBinds.
    std::optional<std::array<std::array<float, 9>, 3>> colorCube;
    Size hsvSize{};
    std::vector<float> hsvMap; // DCP hue/saturation adjustments, interleaved RGB32F.
    SdrImage postLut{};        // Decoded square, tiled lookup bitmap.
    Size sensorPixels{};
    float saturation = 1, contrast = 0, shadows = 0;
};
struct PostPipelineSettings {
    settings::PostPipeline graph;
    BayerSettings bayer;
    LinearSettings linear;
    ToneSettings tone;
    bool deconvolution = true;
    settings::DeconvolutionSharpening sharpening;
    DisplaySettings display;
    // Set wideAspect only when the capture controller has not already zoom-cropped the sensor.
    bool wideAspect = false;
    using Preferences = std::unordered_map<std::string, std::string>;
    void loadTunables(const Preferences&, int sensorMode, int alignmentMode);
    Size fullRawSize{};    // Set on a digitally cropped capture.
    RgbaImage kernelMap{}; // ESD4D's packed FP16 (s1, s2, rho, 1) map, if available.
    settings::KernelNetPrep kernelNet;
    settings::UpscaleCrop upscale;
};
// Supply normalized binary16 samples. mergedRgb takes interleaved R,G,B triples.
SdrImage renderPostPipeline(esd4d::ComputeContext&, Size image, const std::vector<uint16_t>& merged,
                            const PostPipelineSettings&, SdrImage* ultraHdrGainMap = nullptr,
                            float gainMapScale = 6.f);

// Merged, already black/white-normalized binary16 samples from ESD4D.
std::vector<std::uint32_t> renderMergedLinear(esd4d::ComputeContext&, Size,
                                              const std::vector<std::uint16_t>& merged, const BayerSettings&,
                                              const LinearSettings&);

// Starts at the Bayer2Float output boundary. Neutral and blackLevel are in
// the domains passed to the Java demosaic nodes. Returns packed RGBA16F.
std::vector<std::uint32_t> renderLinear(esd4d::ComputeContext& context, Size image,
                                        const std::vector<float>& calibratedCfa, const LinearSettings& settings);
} // namespace photon::post
