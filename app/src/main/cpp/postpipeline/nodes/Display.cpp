//
// Created by eszdman on 21.11.2026.
//

#include "PostNodes.hpp"
#include "capturesharpening.hpp"
#include "correctingflow.hpp"
#include "lsharpening3.hpp"
#include "rotate0.hpp"
#include "rotate1.hpp"
#include <algorithm>

namespace photon::post {
using namespace shaders;
void CaptureSharpening::run(PostState& s) {
    capturesharpening::Parameters p;
    p.SHARPSTR = display.captureIntensity * (.2f + std::min(display.sharpness, 0.f)) / .2f * s.sharpenScale;
    p.SHARPSIZEKER = display.captureRadius;
    auto out = s.nextRgb();
    s.passes.dispatch(p, capturesharpening::InputBuffer(s.rgb), capturesharpening::Output(out));
    s.rgb = out;
}

void CorrectingFlow::run(PostState& s) {
    const auto& c = display.aberration;
    correctingflow::Parameters p;
    p.C = {c[0], c[1]};
    p.RC = {c[2], c[3]};
    p.GC = {c[4], c[5]};
    p.BC = {c[6], c[7]};
    auto out = s.nextRgb();
    s.passes.dispatch(p, correctingflow::InputBuffer(s.rgb), correctingflow::Output(out));
    s.rgb = out;
}

void Sharpen2::run(PostState& s) {
    lsharpening3::Parameters p;
    p.strength = std::max(0.f, display.sharpness) * s.sharpenScale;
    p.size = p.SHARPSIZE = display.sharpen.sharpSize;
    p.SHARPMAX = display.sharpen.sharpMax;
    p.SHARPMIN = display.sharpen.sharpMin;
    p.INTENSE = display.sharpen.denoiseActivity;
    p.NOISES = s.settings.noiseS;
    p.NOISEO = s.settings.noiseO;
    auto out = s.nextRgb();
    s.passes.dispatch(p, lsharpening3::InputBuffer(s.rgb), lsharpening3::Output(out));
    s.rgb = out;
}

namespace {
template <class Pass> void rotate(PostState& s, const DisplaySettings& display, Rgba watermark, Sdr out) {
    typename Pass::Parameters p;
    p.cropSize = p.rawSize = s.image;
    p.rotate = (4 - display.rotationDegrees / 90) % 4;
    p.mirror = display.mirror;
    // The Java shader currently excludes the dither term from the final color.
    s.passes.dispatch(p, Pass::InputBuffer(s.rgb), Pass::Watermark(watermark), Pass::Noise(watermark),
                      Pass::Output(out));
}
} // namespace

void RotateWatermark::run(PostState& s) {
    const auto& w = display.watermark;
    Sdr out = sink_;
    if (w.pixels.empty()) {
        rotate<rotate0>(s, display, s.rgb, out);
    } else {
        if (w.size.x <= 0 || w.size.y <= 0 || w.pixels.size() != uint64_t(w.size.x) * w.size.y * 2 ||
            w.pixels.size() > s.gpu.getDeviceProperties().limits.maxStorageBufferRange / 4)
            throw std::invalid_argument("Invalid RGBA16F watermark");
        auto values = s.gpu.tensorT(w.pixels);
        s.upload({values});
        rotate<rotate1>(s, display, {values, w.size}, out);
    }
}

} // namespace photon::post
