//
// Created by eszdman on 21.11.2026.
//

#include "ToneNodes.hpp"
#include "Histogram.hpp"
#include "Bayer.hpp"
#include "halfConvert.h"
#include <algorithm>
#include <numeric>
#include <cmath>

namespace photon::post {
namespace {
float mix(float a, float b, float w) { return a * (1 - w) + b * w; }
float encode(float x) { return x <= .0031308f ? x * 12.92f : 1.055f * std::pow(x, 1.f / 2.4f) - .055f; }
float reinhard(float v, float white) { return v * (1.f + v / (white * white)) / (1.f + v); }

struct ExposureEstimate {
    float gain, white;
};
ExposureEstimate estimate(const std::vector<uint32_t>& hist, const std::vector<float>& mapped,
                          const settings::AutoExposureCurve& t, double variance) {
    const int n = t.histSize;
    const uint64_t total = std::accumulate(hist.begin(), hist.end(), uint64_t(0));
    float sum = 0;
    uint64_t count = 0;
    for (int i = 0; i < n - 1 && count <= total * t.fillCoefficient; ++i) {
        float weighted = 0;
        for (int c = 0; c < 3; ++c) {
            weighted += hist[c * n + i] * std::min(mapped[c * n + i], float(n - 1));
            count += hist[c * n + i];
        }
        sum += weighted;
    }
    const float target = (n / 256.f) * t.target;
    const float avg = count ? sum / count : target;
    const float gain = std::min(
        {target / std::max(avg, 1e-4f), t.gainMax, std::max(1.f, float(t.noiseMax / std::sqrt(variance)))});
    float white = std::numeric_limits<float>::max();
    for (int c = 0; c < 3; ++c) {
        uint64_t mass = std::accumulate(hist.begin() + c * n, hist.begin() + (c + 1) * n, uint64_t(0));
        count = 0;
        sum = 0;
        for (int i = n - 1; i > std::max(n * 2. / 3., n / (gain + .001)); --i) {
            sum += hist[c * n + i] * mapped[c * n + i];
            count += hist[c * n + i];
            if (count > mass * .005f) break;
        }
        white = std::min(white, (count ? sum / count : n - 1.f) / n);
    }
    return {gain, white};
}
} // namespace

void AutoExposureCurve::run(PostState& s) {
    const auto& t = tone.exposure;
    const int n = t.histSize;
    std::array<float, 3> extent{}, inverse{};
    for (int c = 0; c < 3; ++c) {
        float wp = s.settings.whitePoint[c];
        extent[c] = wp > 0 && wp < 1 ? 1 / wp : 1;
    }
    extent[1] = std::max(extent[1],
                         float(std::pow((std::cbrt(double(extent[0])) + std::cbrt(double(extent[2]))) * .5, 3)));
    for (int c = 0; c < 3; ++c) inverse[c] = 1 / extent[c];
    auto hist = rgbHistogram(s, n, inverse);
    std::vector<float> mapped(3 * n);
    auto measure = [&] {
        for (int c = 0; c < 3; ++c)
            for (int i = 0; i < n; ++i)
                mapped[c * n + i] = encode((i + .5f) / (n - 1) * extent[c] / s.adaptiveWhitePoint) * (n - 1);
        return estimate(hist, mapped, t, s.settings.noiseS * .5 + s.settings.noiseO);
    };
    auto scene = measure();
    if (t.adaptiveWhitePointEnable && t.enableWP && scene.white > 1) {
        s.adaptiveWhitePoint = std::pow((scene.white + .055f) / 1.055f, 2.4f);
        scene = measure();
    }
    float gain = scene.gain, left = 0, right = 0;
    for (int i = 0; i < n; ++i) {
        float v = float(i) / (n - 1) * gain;
        left += std::min(v, 1.f);
        right += reinhard(v, gain);
    }
    gain *= left / right;
    float white = t.enableWP ? mix(gain, scene.white * gain, t.whiteApply) : gain;
    auto response = [&](float x) { return reinhard(mix(x, std::sqrt(x), t.applyGammaMix) * gain, white); };
    float knee = 1;
    if (t.highlightCompression) {
        uint64_t clipped = 0, total = 0;
        for (size_t i = 0; i < hist.size(); ++i) {
            total += hist[i];
            if (response(mapped[i] / (n - 1)) > 1 + t.clipTolerance) clipped += hist[i];
        }
        float frac = total ? float(clipped) / total : 0;
        knee = mix(t.kneeMax, std::min(t.kneeMin, t.kneeMax), std::min(frac / std::max(t.kneeRef, .0001f), 1.f));
    }
    std::vector<float> curve(1024);
    for (size_t i = 0; i < curve.size(); ++i) {
        float v = response(float(i) / float(curve.size() - 1));
        v = mix(v, v * v, t.applyGammaMix);
        if (v > knee && knee < 1) {
            float g = (v - knee) / (1 - knee);
            v = knee + (1 - knee) * g * g;
        }
        curve[i] = esd4d::halfValue(f32ToF16(std::clamp(v, 0.f, 1.f)));
    }
    s.exposureCurve = {s.gpu.tensorT(curve), {int(curve.size()), 1}};
    s.upload({s.exposureCurve.tensor});
}

void LinearExposure::run(PostState& s) {
    const auto& t = tone.linear;
    const int n = t.histSize;
    auto hist = rgbHistogram(s, n, {1, 1, 1});
    uint64_t total = 0;
    std::vector<uint64_t> cumulative(n);
    for (int i = 0; i < n; ++i) cumulative[i] = total += uint64_t(hist[i]) + hist[n + i] + hist[2 * n + i];
    auto percentile = [&](float fraction) {
        auto it = std::upper_bound(cumulative.begin(), cumulative.end(), uint64_t(total * fraction));
        return it == cumulative.end() ? 1.f : float(it - cumulative.begin()) / (n - 1);
    };
    float mid = std::max(1.f, t.midAnchor / std::max(percentile(.5f), 1e-4f));
    float high = std::max(1.f, t.highAnchor / std::max(percentile(.9f), 1e-4f));
    s.displayGain = total ? std::clamp(std::sqrt(mid * high), t.gainMin, t.gainMax) : t.gainMax;
}
} // namespace photon::post
