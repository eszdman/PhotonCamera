//
// Created by eszdman on 21.11.2026.
//

#include "ToneNodes.hpp"
#include "initial_000.hpp"
#include "initial_001.hpp"
#include "initial_010.hpp"
#include "initial_011.hpp"
#include "initial_100.hpp"
#include "initial_101.hpp"
#include "initial_110.hpp"
#include "initial_111.hpp"
#include "modern_0.hpp"
#include "modern_1.hpp"
#include <tuple>
#include <cmath>
#include "headroom.hpp"
#include <algorithm>

namespace photon::post {
namespace {
using namespace shaders;
using Matrix = std::array<std::array<float, 4>, 3>;
Matrix shaderMatrix(const std::array<float, 9>& values) {
    Matrix matrix{};
    for (int col = 0; col < 3; ++col)
        for (int row = 0; row < 3; ++row) matrix[col][row] = values[3 * row + col];
    return matrix;
}
Plane<Format::rgba32f> uploadHsv(PostState& s, const ToneSettings& t) {
    const auto size = t.hsvSize;
    if (size.x < 1 || size.y < 1 || t.hsvMap.size() != uint64_t(size.x) * size.y * 3 ||
        uint64_t(size.x) * size.y > s.gpu.getDeviceProperties().limits.maxStorageBufferRange / 16)
        throw std::invalid_argument("Invalid DCP hue/saturation map");
    auto tex = s.gpu.tensorT<float>(uint64_t(size.x) * size.y * 4);
    for (size_t i = 0; i < t.hsvMap.size() / 3; ++i) {
        std::copy_n(&t.hsvMap[i * 3], 3, tex->data() + i * 4);
        tex->data()[i * 4 + 3] = 1;
    }
    s.upload({tex});
    return {tex, size};
}
template <class Params> void colorTransform(Params& p, const PostState& s, const ToneSettings& tone) {
    auto size = tone.sensorPixels.x > 0 ? tone.sensorPixels : s.image;
    p.activeSize = {2, 2, size.x - 2, size.y - 2};
    p.NEUTRALPOINT = s.settings.whitePoint;
    p.sensorToIntermediate = shaderMatrix(tone.sensorToIntermediate);
}
} // namespace

template <class Shader> void Initial::render(PostState& s) {
    typename Shader::Parameters p;
    colorTransform(p, s, tone);
    const auto& t = tone.initial;
    p.u_fullSize = {float(s.captureSize.x), float(s.captureSize.y)};
    p.adaptiveWhitePoint = s.adaptiveWhitePoint;
    p.SATURATION = p.SATURATION2 = s.settings.demosaicing == Demosaicing::monochrome ? 0 : tone.saturation;
    p.CONTRAST = tone.contrast;
    p.SHADOWS = tone.shadows;
    p.NOISES = s.settings.noiseS;
    p.NOISEO = s.settings.noiseO;
    p.TONEMAPX1 = t.tonemapx1;
    p.TONEMAPX2 = t.tonemapx2;
    p.TONEMAPX3 = t.tonemapx3;
    p.SATURATIONRED = t.saturationRed;
    p.VIGNETTE = t.vignetteCorrection;
    p.LTMMIX = t.ltmMix;
    p.toneMapCoeffs = {-2 + 2 * t.toneMix, 3 - 3 * t.toneMix, t.toneMix, 0};
    if constexpr (Shader::Variant::CCT) {
        p.CUBE0 = shaderMatrix((*tone.colorCube)[0]);
        p.CUBE1 = shaderMatrix((*tone.colorCube)[1]);
        p.CUBE2 = shaderMatrix((*tone.colorCube)[2]);
    } else
        p.intermediateToSRGB = shaderMatrix(tone.intermediateToSRGB);
    auto out = s.nextRgb();
    auto hsvInput = [&] {
        if constexpr (Shader::Variant::USE_HSV)
            return std::make_tuple(Shader::HSVMap(uploadHsv(s, tone)));
        else
            return std::tuple<>{};
    };
    auto lutInput = [&] {
        if constexpr (Shader::Variant::POSTLUT) {
            const auto& image = tone.postLut;
            int tiles = int(.1 + std::cbrt(double(image.size.x)));
            if (tiles < 1 || tiles * tiles * tiles != image.size.x || image.size.x != image.size.y ||
                image.pixels.size() != uint64_t(image.size.x) * image.size.y ||
                image.pixels.size() > s.gpu.getDeviceProperties().limits.maxStorageBufferRange / 4)
                throw std::invalid_argument("Invalid tiled color LUT");
            p.POSTLUTSIZE = tiles * tiles;
            p.POSTLUTSIZETILES = tiles;
            auto tex = s.gpu.tensorT(image.pixels);
            s.upload({tex});
            return std::make_tuple(Shader::PostLut({tex, image.size}));
        } else
            return std::tuple<>{};
    };
    auto profiles = std::tuple_cat(hsvInput(), lutInput());
    std::apply(
        [&](auto... profile) {
            s.passes.dispatch(p, Shader::InputBuffer(s.rgb), Shader::ExposureCurve(s.exposureCurve),
                              Shader::GainMap(s.gainMap), profile..., Shader::Output(out));
        },
        profiles);
    s.rgb = out;
}
void Initial::run(PostState& s) {
    unsigned mode =
        (tone.colorCube ? 4 : 0) | (!tone.hsvMap.empty() ? 2 : 0) | (!tone.postLut.pixels.empty() ? 1 : 0);
    using Render = void (Initial::*)(PostState&);
    static constexpr Render variants[] = {&Initial::render<initial_000>, &Initial::render<initial_001>,
                                          &Initial::render<initial_010>, &Initial::render<initial_011>,
                                          &Initial::render<initial_100>, &Initial::render<initial_101>,
                                          &Initial::render<initial_110>, &Initial::render<initial_111>};
    (this->*variants[mode])(s);
}

template <class Shader> void ModernInitial::render(PostState& s) {
    typename Shader::Parameters p;
    colorTransform(p, s, tone);
    p.adaptiveWhitePoint = s.adaptiveWhitePoint;
    p.SATURATION = s.settings.demosaicing == Demosaicing::monochrome ? 0 : tone.saturation;
    p.CONTRAST = tone.contrast;
    p.SHADOWS = tone.shadows;
    p.BASECONTRAST = tone.modern.baseContrast;
    p.HIGHLIGHTRANGE = tone.modern.highlightRange;
    auto out = s.nextRgb();
    p.intermediateToSRGB = shaderMatrix(tone.intermediateToSRGB);
    auto dispatch = [&](auto... profile) {
        s.passes.dispatch(p, Shader::InputBuffer(s.rgb), Shader::GainMap(s.gainMap), profile...,
                          Shader::ExposureCurve(s.exposureCurve), Shader::Output(out));
    };
    if constexpr (Shader::Variant::USE_HSV)
        dispatch(Shader::HSVMap(uploadHsv(s, tone)));
    else
        dispatch();
    s.rgb = out;
}
void ModernInitial::run(PostState& s) {
    if (tone.hsvMap.empty())
        render<modern_0>(s);
    else
        render<modern_1>(s);
}

void HeadroomRender::run(PostState& s) {
    headroom::Parameters p;
    colorTransform(p, s, tone);
    p.u_fullSize = {float(s.image.x), float(s.image.y)};
    p.intermediateToSRGB = shaderMatrix(tone.intermediateToSRGB);
    p.displayGain = std::max(1.f, s.displayGain);
    p.sceneWhite = std::clamp(tone.sky.headroomScale * p.displayGain, 1.f, tone.sky.sceneWhiteMax);
    p.outputExposureScale = std::max(.01f, tone.sky.outputExposureScale);
    auto out = s.nextRgb();
    s.passes.dispatch(p, headroom::InputBuffer(s.rgb), headroom::GainMap(s.gainMap), headroom::Output(out));
    s.rgb = out;
}
} // namespace photon::post
