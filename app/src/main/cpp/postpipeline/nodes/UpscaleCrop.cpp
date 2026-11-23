//
// Created by eszdman on 21.11.2026.
//

#include "PostNodes.hpp"
#include "KernelMap.hpp"
#include "Bayer.hpp"
#include "halfConvert.h"
#include "anisoupscale.hpp"
#include "bicubic.hpp"
#include <algorithm>
#include <cmath>

namespace photon::post {
using namespace shaders;
void KernelNetPrep::run(PostState& s) {
    const float sigma = float(std::sqrt(.5 * s.settings.noiseS + s.settings.noiseO) * sigmaMpy_);
    s.singleKernelMap = std::async(std::launch::async, [size = s.image, sigma, &raw = merged_, &ctx = ctx_] {
        const int width = ((size.x / 2 + 3) / 4) * 4, height = size.y / 2;
        std::vector<float> luma(size_t(width) * height);
        for (int y = 0; y < height; ++y)
            for (int x = 0; x < width; ++x) {
                size_t i = size_t(2 * y) * size.x + 2 * std::min(x, size.x / 2 - 1);
                float sum = esd4d::halfValue(raw[i]) + esd4d::halfValue(raw[i + 1]) +
                            esd4d::halfValue(raw[i + size.x]) + esd4d::halfValue(raw[i + size.x + 1]);
                luma[y * width + x] = esd4d::halfValue(f32ToF16(std::sqrt(std::clamp(sum * .25f, 0.f, 1.f))));
            }
        return RgbaImage{{(width + 1) / 2, (height + 1) / 2},
                         esd4d::inferKernelMap(ctx, luma, width, height, sigma)};
    });
}

namespace {
bool healthy(const RgbaImage& map) {
    if (map.size.x <= 0 || map.size.y <= 0 || map.pixels.size() != size_t(map.size.x) * map.size.y * 2)
        return false;
    for (size_t i : {size_t(0), map.pixels.size() - 2}) {
        float s1 = esd4d::halfValue(map.pixels[i]), s2 = esd4d::halfValue(map.pixels[i] >> 16),
              rho = esd4d::halfValue(map.pixels[i + 1]);
        if (!(s1 >= 0 && s1 <= 4 && s2 >= 0 && s2 <= 4 && rho >= -2 && rho <= 2)) return false;
    }
    return true;
}
} // namespace

void UpscaleCrop::run(PostState& s) {
    RgbaImage inferred;
    if (s.singleKernelMap.valid()) {
        try {
            inferred = s.singleKernelMap.get();
        } catch (const std::runtime_error&) { /* GLES falls back to bicubic when inference fails. */
        }
    }
    const auto& map = options_.kernelMap.pixels.empty() ? inferred : options_.kernelMap;
    auto out = s.rgba(target_);
    if (healthy(map)) {
        const auto& t = options_.upscale;
        auto params = s.gpu.tensorT(map.pixels);
        s.upload({params});
        const float zx = float(s.image.x) / target_.x, zy = float(s.image.y) / target_.y;
        anisoupscale::Parameters p;
        p.fullSize = target_;
        p.scaleRatio = {1 / zx, 1 / zy};
        p.sigmaScale = t.sigmaScale;
        p.sigmaMinPx = {std::min(std::max(t.absMinPx, t.outFloorPx * zx), t.sigmaMaxPx),
                        std::min(std::max(t.absMinPx, t.outFloorPx * zy), t.sigmaMaxPx)};
        p.sigmaMaxPx = t.sigmaMaxPx;
        p.strength = t.anisoStrength;
        p.kernelRadius = t.kernelRadius;
        p.sharpAmt = t.sharpAmt;
        p.sharpWide = t.sharpWide;
        p.maxElong = t.maxElong;
        p.debugMode = t.debugUpscale;
        p.u_winFullSize = {float(s.image.x), float(s.image.y)};
        s.passes.dispatch(p, anisoupscale::InputBuffer(s.rgb), anisoupscale::KernelsMap({params, map.size}),
                          anisoupscale::Output(out));
    } else {
        bicubic::Parameters p;
        p.size = target_;
        s.passes.dispatch(p, bicubic::InputBuffer(s.rgb), bicubic::Output(out));
    }
    s.image = target_;
    s.rgb = out;
    s.destination = out;
    s.alternate = {};
}
} // namespace photon::post
