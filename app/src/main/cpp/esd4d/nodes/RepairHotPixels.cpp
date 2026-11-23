//
// Created by eszdman on 21.11.2026.
//

#include "RepairHotPixels.hpp"
#include "BurstState.hpp"
#include "HotPixels.hpp"
#include "../../halfConvert.h"
#include "EsdShaders.hpp"
#include <algorithm>
#include <cmath>

namespace photon::esd4d {
namespace {
using Index = std::ptrdiff_t;
float sample(const BurstFrame& frame, Index i) {
    return std::clamp(halfValue(frame.pixels[i]), 0.f, 1.f);
}

std::array<float, 8> neighbors(const BurstFrame& frame, Index i, int width) {
    std::array<float, 8> result;
    unsigned k = 0;
    for (int dy = -2; dy <= 2; dy += 2)
        for (int dx = -2; dx <= 2; dx += 2)
            if (dx || dy)
                result[k++] = sample(frame, i + dy * width + dx);
    std::sort(result.begin(), result.end());
    return result;
}

float noiseThreshold(NoiseModel noise, float value, float sigma) {
    return sigma * std::sqrt(std::max(0., noise.s * value + noise.o));
}

using Patch = std::array<float, 81>;
using Residuals = std::array<float, 25>;

Residuals localResiduals(const Patch& patch) {
    Residuals result;
    for (int y = 2; y < 7; ++y)
        for (int x = 2; x < 7; ++x) {
            int i = y * 9 + x;
            std::array<float, 9> values;
            unsigned k = 0;
            for (int dy = -2; dy <= 2; dy += 2)
                for (int dx = -2; dx <= 2; dx += 2)
                    values[k++] = patch[i + dy * 9 + dx];
            std::nth_element(values.begin(), values.begin() + 4, values.end());
            result[(y - 2) * 5 + x - 2] = patch[i] - values[4];
        }
    return result;
}

bool textureOutlier(const Residuals& residuals) {
    float sum = 0, largest = 0;
    for (unsigned i = 0; i < residuals.size(); ++i)
        if (i != 12) {
            float r = std::abs(residuals[i]);
            sum += r;
            largest = std::max(largest, r);
        }
    return residuals[12] > 21 * std::max(0.f, sum - largest) / 23;
}

bool persistent(const std::vector<const BurstFrame*>& samples, Index site, int width, NoiseModel noise,
                float sigma, float weak) {
    int misses = 0, strong = 0;
    std::array<float, 10> values{}, excess{};
    const int n = static_cast<int>(samples.size());
    for (int j = 0; j < n; ++j) {
        auto& f = *samples[j];
        auto v = neighbors(f, site, width);
        float background = (v[3] + v[4]) * .5f;
        values[j] = sample(f, site);
        excess[j] = values[j] - background;
        if (values[j] <= v.back() || excess[j] <= std::max(.02f, noiseThreshold(noise, background, weak)))
            if (++misses > n / 5)
                return false;
        if (excess[j] > std::max(.02f, noiseThreshold(noise, background, sigma)))
            ++strong;
    }
    if (strong <= n / 2)
        return false;
    std::sort(values.begin(), values.begin() + n);
    std::sort(excess.begin(), excess.begin() + n);
    int trim = n >= 5;
    if (excess[n - 1 - trim] - excess[trim] > std::max(.02f, noiseThreshold(noise, values[n / 2], sigma)))
        return false;

    std::array<Patch, 10> patches;
    Patch median;
    auto temporalMedian = [&](auto value) {
        for (int j = 0; j < n; ++j)
            values[j] = value(j);
        std::sort(values.begin(), values.begin() + n);
        return (values[(n - 1) / 2] + values[n / 2]) * .5f;
    };
    for (int y = 0; y < 9; ++y)
        for (int x = 0; x < 9; ++x) {
            int k = y * 9 + x;
            median[k] = temporalMedian([&](int j) {
                return patches[j][k] = sample(*samples[j], site + (y - 4) * width + x - 4);
            });
        }
    if (textureOutlier(localResiduals(median)))
        return true;
    std::array<Residuals, 10> residuals;
    for (int j = 0; j < n; ++j)
        residuals[j] = localResiduals(patches[j]);
    Residuals local;
    for (unsigned k = 0; k < local.size(); ++k)
        local[k] = temporalMedian([&](int j) { return residuals[j][k]; });
    return textureOutlier(local);
}

float replacement(const BurstFrame& f, Index i, const Sensor& sensor, NoiseModel noise,
                  const std::vector<std::size_t>& defects) {
    int width = sensor.width, parity = (i % width + i / width) % 2;
    int diagonal = (parity == (sensor.cfa == 0 || sensor.cfa == 3)) ? 1 : 2;
    std::array<int, 4> offsets{2, 2 * width, diagonal * (width + 1), diagonal * (width - 1)};
    double sum = 0, mass = 0;
    for (unsigned k = 0; k < 4; ++k) {
        int d = offsets[k];
        if (std::binary_search(defects.begin(), defects.end(), i - d) ||
            std::binary_search(defects.begin(), defects.end(), i + d))
            continue;
        float a = sample(f, i - d), b = sample(f, i + d);
        double mean = .5 * (a + b);
        double epsilon = std::max(1. / 65535, std::sqrt(noise.s * mean + noise.o));
        double weight = 1 / ((k < 2 ? 2. : std::sqrt(2.) * diagonal) * (std::abs(a - b) + epsilon));
        sum += weight * mean;
        mass += weight;
    }
    return mass ? float(sum / mass) : sample(f, i);
}

struct Thresholds {
    float strong, weak;
};

Thresholds detectionThresholds(double sensitivity) {
    const float strong = std::max(6.f, 4 * float(sensitivity));
    return {strong, std::max(3.f, strong * .5f)};
}

bool canCompareFrames(const Sensor& sensor, const std::vector<BurstFrame>& frames) {
    if (frames.size() < 3 || sensor.cfa < 0 || sensor.cfa > 3) return false;
    const auto& reference = frames.front();
    return std::all_of(frames.begin(), frames.end(), [&](const BurstFrame& frame) {
        return frame.cropOrigin == reference.cropOrigin && frame.binned == reference.binned;
    });
}

// Each shader entry describes a Bayer quad. Decode only populated entries,
// so the full reference and proposal buffers can be released before verification.
std::vector<std::size_t> detectHotPixelCandidates(ComputeContext& context, const Sensor& sensor,
                                                 const BurstFrame& reference, NoiseModel noise,
                                                 float threshold, unsigned maxQuads) {
    const auto geometry = geometryFor(sensor);
    const int width = geometry.rawHalfWidth + geometry.shiftX;
    const int height = geometry.rawHalfHeight + geometry.shiftY;
    auto& gpu = context.device();
    const unsigned capacity = std::min<std::size_t>(maxQuads, std::size_t(width) * height);
    if (4 * (1 + std::uint64_t(capacity) * 2) > gpu.getDeviceProperties().limits.maxStorageBufferRange)
        throw std::invalid_argument("Hot-pixel proposal list exceeds device range");

    const float exposure = 1 / reference.layerMultiplier;
    auto packedReference = gpu.tensorT(packBayer(sensor, reference.pixels, reference.format, exposure));
    auto candidates = gpu.tensorT(std::vector<std::uint32_t>(1 + 2 * capacity, 0));
    using Shader = shaders::hot_pixels;
    using Buffers = std::vector<std::shared_ptr<kp::Memory>>;
    auto sequence = gpu.sequence();
    sequence->record<kp::OpSyncDevice>(Buffers{packedReference, candidates});
    TensorPass detector;
    auto program = detector.useProgram(gpu, *sequence, assets::hot_pixels, width, height);
    program.setVar(Shader::rawSize, {sensor.width, sensor.height});
    program.setVar(Shader::shift, {geometry.shiftX, geometry.shiftY});
    program.setVar(Shader::exposure, exposure);
    program.setVar(Shader::noiseS, float(noise.s));
    program.setVar(Shader::noiseO, float(noise.o));
    program.setVar(Shader::threshold, threshold);
    program.setVar(Shader::capacity, capacity);
    program.bindInputs(Shader::Reference(packedReference)).writeTo(Shader::Proposals(candidates));
    sequence->record<kp::OpSyncLocal>(Buffers{candidates})->eval();

    const auto* words = candidates->data();
    const unsigned count = words[0];
    if (count > capacity) return {};
    std::vector<std::size_t> sites;
    sites.reserve(count);
    for (unsigned i = 0; i < count; ++i) {
        const auto quad = words[1 + 2 * i];
        const auto mask = words[2 + 2 * i];
        const int x = 2 * int(quad % width) - geometry.shiftX;
        const int y = 2 * int(quad / width) - geometry.shiftY;
        for (int channel = 0; channel < 4; ++channel)
            if (mask & (1u << channel))
                sites.push_back(Index(y + channel / 2) * sensor.width + x + channel % 2);
    }
    return sites;
}

std::vector<std::size_t> confirmPersistentDefects(const std::vector<std::size_t>& candidates,
                                                 const std::vector<BurstFrame>& frames, int width,
                                                 NoiseModel noise, Thresholds thresholds, std::size_t limit) {
    if (candidates.empty()) return {};
    const auto count = std::min<std::size_t>(10, frames.size());
    std::vector<const BurstFrame*> samples;
    for (std::size_t i = 0; i < count; ++i)
        samples.push_back(&frames[i * (frames.size() - 1) / (count - 1)]);

    std::vector<std::size_t> confirmed;
    for (auto site : candidates) {
        if (persistent(samples, site, width, noise, thresholds.strong, thresholds.weak)) {
            if (confirmed.size() == limit) return {};
            confirmed.push_back(site);
        }
    }
    std::sort(confirmed.begin(), confirmed.end());
    return confirmed;
}

void repairFrames(std::vector<BurstFrame>& frames, const std::vector<std::size_t>& sites,
                  const Sensor& sensor, NoiseModel noise, float threshold) {
    for (auto& frame : frames) {
        std::vector<std::uint16_t> repaired;
        for (auto site : sites) {
            auto v = neighbors(frame, site, sensor.width);
            float b = (v[3] + v[4]) * .5f;
            float center = sample(frame, site);
            repaired.push_back(center > v.back() && center - b > noiseThreshold(noise, b, threshold)
                                   ? f32ToF16(replacement(frame, site, sensor, noise, sites))
                                   : frame.pixels[site]);
        }
        for (std::size_t i = 0; i < sites.size(); ++i)
            frame.pixels[sites[i]] = repaired[i];
    }
}

} // namespace

void RepairHotPixels::run(BurstState& state) {
    const auto& settings = state.settings<RepairHotPixels>();
    if (!settings.enableHotPixelCorrection) return;
    state.result.hotPixelSites = run(*state.context, state.options.sensor, state.frames,
                                     state.result.appliedNoise, settings);
}

std::vector<std::size_t> RepairHotPixels::run(ComputeContext& context, const Sensor& sensor,
                                              std::vector<BurstFrame>& frames, NoiseModel noise,
                                              const Settings& settings) {
    if (!canCompareFrames(sensor, frames)) return {};
    const auto thresholds = detectionThresholds(settings.detectThr);
    auto candidates = detectHotPixelCandidates(context, sensor, frames.front(), noise,
                                               thresholds.weak, settings.MAX_HOT_PIXELS);
    auto defects = confirmPersistentDefects(candidates, frames, sensor.width, noise,
                                           thresholds, settings.MAX_REASONABLE_HOTPIXELS);
    repairFrames(frames, defects, sensor, noise, thresholds.weak);
    return defects;
}

std::vector<std::size_t> correctHotPixels(ComputeContext& context, const Sensor& sensor,
                                          std::vector<BurstFrame>& frames, NoiseModel noise,
                                          const RepairHotPixels::Settings& settings) {
    return RepairHotPixels::run(context, sensor, frames, noise, settings);
}
} // namespace photon::esd4d
