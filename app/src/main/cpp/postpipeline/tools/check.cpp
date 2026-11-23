//
// Created by eszdman on 21.11.2026.
//

#include "PostPipeline.hpp"
#include "Bayer.hpp"
#include "halfConvert.h"
#include <algorithm>
#include <cmath>
#include <iostream>
#include <stdexcept>

using namespace photon::post;
using photon::esd4d::halfValue;
namespace {
void require(bool ok, const char* reason) {
    if (!ok) throw std::runtime_error(reason);
}
void checkTunables() {
    PostPipelineSettings shot;
    const auto* gainMap = shot.bayer.gainMap.data();
    shot.loadTunables({{"pref_tunable_esd3d2_version", "steered_prod"},
                       {"pref_tunable_esd3d2_luma", "0.35"},
                       {"pref_tunable_esd3d2_maxsize", "13"},
                       {"pref_tunable_bayer2float_hlinpaintopposed", "0"},
                       {"pref_tunable_postpipeline_tonepipeline", "new"}}, 3, 1);
    require(shot.linear.esd3d.version == ESD3D2::Version::steered_prod && shot.linear.esd3d.luma == .35f &&
            shot.linear.esd3d.maxSize == 13, "ESD3D2 field overrides");
    require(!shot.bayer.hlInpaintOpposed && shot.tone.mode == ToneMode::modern, "base and graph fields");
    try {
        shot.loadTunables({{"pref_tunable_esd3d2_luma", "0.1oops"}}, 3, 2);
        throw std::runtime_error("accepted malformed tunable");
    } catch (const std::invalid_argument&) {}
    require(shot.linear.esd3d.luma == .35f && shot.tone.mode == ToneMode::modern, "partial tuning update");
    shot.loadTunables({}, 3, 2);
    require(shot.linear.esd3d.luma == ESD3D2{}.luma && shot.bayer.hlInpaintOpposed &&
            shot.linear.demosaicing == Demosaicing::mergedRgb, "reload annotation defaults");
    require(shot.bayer.gainMap.data() == gainMap, "tunable load replaced the shot gain map");
}
void checkFinite(const std::vector<uint32_t>& output) {
    for (size_t i = 0; i < output.size(); i += 2) {
        require(std::isfinite(halfValue(output[i])) && std::isfinite(halfValue(output[i] >> 16)) &&
                    std::isfinite(halfValue(output[i + 1])),
                "nonfinite RGB");
        require((output[i + 1] >> 16) == 0x3c00, "invalid alpha");
    }
}
} // namespace
int main() try {
    checkTunables();
    photon::esd4d::ComputeContext context;
    DisplaySettings display;
    display.localContrast.enabled = false;
    RgbaImage redGreen{{2, 1}, {0x00003c00u, 0x3c000000u, 0x3c000000u, 0x3c000000u}};
    const auto tail = finishDisplay(context, redGreen, display);
    require(tail.pixels == std::vector<uint32_t>{0xff0000ffu, 0xff00ff00u}, "display-only checkpoint");
    const Size image{134, 94};
    std::vector<uint16_t> raw(image.x * image.y);
    for (int y = 0; y < image.y; ++y)
        for (int x = 0; x < image.x; ++x) raw[y * image.x + x] = f32ToF16(.1f + ((x * 17 + y * 43) % 193) / 256.f);
    std::vector<float> cfa(raw.size());
    std::transform(raw.begin(), raw.end(), cfa.begin(), halfValue);
    LinearSettings s;
    s.blackLevels.enable = false;
    BayerSettings sensor;
    auto reference = renderLinear(context, image, cfa, s);
    s.tileSize = 64;
    // A zero CFA border produces binary16 gradient weights below the smallest
    // normal half. A driver flushing them to zero used to blacken nearby colors.
    auto edge = cfa;
    for (int y = 0; y < image.y; ++y) {
        for (int x = 0; x < image.x; ++x)
            edge[y * image.x + x] = (x == image.x - 1 || y == image.y - 1) ? 0.f : .375f;
    }
    auto edgeRgb = renderLinear(context, image, edge, s);
    size_t site = size_t(25 * image.x + image.x - 2) * 2;
    require(halfValue(edgeRgb[site]) > .1f && halfValue(edgeRgb[site + 1]) > .1f, "AMaZE FP16 edge weights");
    require(renderMergedLinear(context, image, raw, sensor, s) == reference,
            "calibration or tile seam changed AMaZE");
    s.demosaicing = Demosaicing::monochrome;
    auto mono = renderLinear(context, image, cfa, s);
    for (size_t i = 0; i < raw.size(); ++i)
        require(mono[2 * i] == (raw[i] | (uint32_t(raw[i]) << 16)) && mono[2 * i + 1] == (raw[i] | 0x3c000000u),
                "mono channel mismatch");
    s.demosaicing = Demosaicing::legacy;
    for (int scale = 1; scale <= 4; ++scale) {
        s.denoise = true;
        s.noiseO = std::pow(scale * s.esd3d.noiseTarget, 2);
        s.esd3d.version = scale % 2 == 0 ? ESD3D2::Version::steered_prod : ESD3D2::Version::steered;
        auto filtered = renderLinear(context, image, cfa, s);
        checkFinite(filtered);
        auto withoutMoire = s;
        withoutMoire.esd3d.moire = 0.f;
        require(filtered != renderLinear(context, image, cfa, withoutMoire), "ESD3D2 MOIRE had no effect");
    }
    s.esd3d.useColorDenoising = false;
    checkFinite(renderLinear(context, image, cfa, s));
    s.demosaicing = Demosaicing::quad;
    checkFinite(renderMergedLinear(context, image, raw, sensor, s));
    // Exercise the clipped-cell mask, row readback and highlight shader.
    s.demosaicing = Demosaicing::amaze;
    s.denoise = false;
    s.whitePoint = {.35f, 1.f, .8f};
    sensor.cfaPattern = 3;
    auto repaired = renderMergedLinear(context, image, raw, sensor, s);
    checkFinite(repaired);
    sensor.hlInpaintOpposed = false;
    require(repaired != renderMergedLinear(context, image, raw, sensor, s),
            "opposed highlight switch had no effect");
    PostPipelineSettings options;
    options.linear.noiseS = .0001f;
    options.linear.noiseO = .000001f;
    options.linear.denoise = true;
    options.linear.whitePoint = {.51f, 1.f, .72f};
    options.bayer.cfaPattern = 3;
    options.display.rotationDegrees = 90;
    for (ToneMode tone : {ToneMode::curve, ToneMode::modern, ToneMode::sky, ToneMode::off}) {
        options.tone.mode = tone;
        options.sharpening.radius = tone == ToneMode::sky ? 1.5f : .75f;
        SdrImage gain;
        auto result = renderPostPipeline(context, image, raw, options, &gain);
        require(gain.pixels.size() == raw.size(), "HDR gain size");
        for (auto px : gain.pixels)
            require((px >> 24) == 255 && (px & 255) == ((px >> 8) & 255) && (px & 255) == ((px >> 16) & 255),
                    "HDR gain channels");
        require(result.size.x == image.y && result.size.y == image.x, "rotated output size");
        require(result.pixels.size() == raw.size(), "RGBA8 byte count");
        for (int y = 0; y < result.size.y; ++y)
            require(result.pixels[size_t(y)*result.size.x] == 0xff000000, "rotated GLES border");
        for (auto pixel : result.pixels) require(pixel >> 24 == 255, "SDR alpha");
        require(std::any_of(result.pixels.begin(), result.pixels.end(),
                            [](uint32_t p) { return (p & 255) > 0 && (p & 255) < 255; }),
                "SDR output has no midtones");
    }
    options.fullRawSize = {image.x * 2, image.y * 2};
    options.display.unsharp = true;
    options.display.captureSharpening = true;
    options.display.watermark = {{1, 1}, {0x3c003c00u, 0x00003c00u}};
    options.display.aberration = {.5f, .5f, .04f, .01f, 0, 0, -.03f, -.01f};
    auto crop = renderPostPipeline(context, image, raw, options);
    for (auto pixel : crop.pixels) require(pixel >> 24 == 255, "cropped SDR alpha");
    require(crop.size.x == (image.y * 2 & ~3) && crop.size.y == (image.x * 2 & ~3), "cropped output size");
    options.fullRawSize = {};
    options.wideAspect = true;
    options.display.rotationDegrees = 0;
    options.loadTunables({}, 3, 2);
    options.tone.colorCube = std::array<std::array<float, 9>, 3>{
        options.tone.intermediateToSRGB, options.tone.intermediateToSRGB, options.tone.intermediateToSRGB};
    options.tone.hsvSize = {1, 1};
    options.tone.hsvMap = {0, 1, 1};
    options.tone.postLut = {{1, 1}, {0xff887733u}};
    std::vector<uint16_t> rgb(raw.size() * 3);
    for (size_t i = 0; i < rgb.size(); ++i) rgb[i] = raw[i / 3];
    auto profiled = renderPostPipeline(context, image, rgb, options);
    require(profiled.size.x == (image.x & ~3) && profiled.size.y == ((image.x * 9 / 16) & ~3), "wide output size");
    std::cout << "PostPipeline checks passed\n";
    return 0;
} catch (const std::exception& e) {
    std::cerr << e.what() << '\n';
    return 1;
}
