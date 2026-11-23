//
// Created by eszdman on 21.11.2026.
//

#include "EstimateNoise.hpp"
#include "BurstState.hpp"
#include "Noise.hpp"
#include "../../halfConvert.h"
#include "EsdShaders.hpp"
#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace photon::esd4d {
namespace {
struct Row {
    int index;
    double brightness, variance;
    int samples;
    bool supported = false;
};
using Rows = std::vector<Row>;
double curveScore(const Rows& rows, NoiseModel m, double gate) {
    double score = 0, weight = 0, lo = INFINITY, hi = 0;
    int count = 0;
    for (const auto& r : rows) {
        if (!r.supported)
            continue;
        double p = m.s * r.brightness + m.o;
        if (p <= 0)
            return INFINITY;
        double residual = std::log(r.variance / p), w = std::sqrt(std::min(r.samples, 128));
        score += w * (residual >= 0 ? 0.2 * residual : -0.8 * residual);
        weight += w;
        if (r.variance <= p * gate && r.variance * gate >= p) {
            ++count;
            lo = std::min(lo, r.brightness);
            hi = std::max(hi, r.brightness);
        }
    }
    return count >= 5 && hi - lo >= 0.04 ? score / weight : INFINITY;
}

std::optional<NoiseModel> fit(const std::array<int, 4096>& hist, double scale, double response,
                              double gate) {
    Rows rows;
    constexpr double brightnessScale = 64 * 1.7320508075688772;
    for (int y = 1; y < 63; ++y) {
        auto h = hist.data() + 64 * y;
        int total = 0;
        for (int x = 0; x < 64; ++x)
            total += h[x];
        if (total < 24 || h[63] > 0.15 * total)
            continue;
        double median = 0;
        for (int x = 0, before = 0; x < 63; before += h[x], ++x) {
            if (h[x] && before + h[x] >= total * .5) {
                median = x + (total * .5 - before) / h[x];
                break;
            }
        }
        if (median < 1)
            continue;
        double sigma = median / (scale * 0.6744897501960817 * response);
        double b = std::pow((y + .5) / brightnessScale, 2);
        if (b > 3 * sigma)
            rows.push_back({y, b, sigma * sigma, total});
    }
    for (auto& r : rows) {
        int n = 0;
        for (auto& neighbor : rows) {
            if (r.index == neighbor.index || std::abs(r.index - neighbor.index) > 4)
                continue;
            auto& low = r.index < neighbor.index ? r : neighbor;
            auto& high = r.index < neighbor.index ? neighbor : r;
            double tol = 1 + std::max(0.2, 3 / std::sqrt(std::min(r.samples, neighbor.samples)));
            if (high.variance * tol >= low.variance &&
                high.variance <= low.variance * high.brightness / low.brightness * tol)
                ++n;
        }
        r.supported = n >= 2;
    }
    double best = INFINITY;
    NoiseModel model{};
    auto consider = [&](NoiseModel m) {
        if (m.s < 0 || m.o < 0)
            return;
        double score = curveScore(rows, m, gate);
        if (score < best) {
            best = score;
            model = m;
        }
    };
    for (auto& a : rows)
        if (a.supported) {
            consider({0, a.variance});
            for (auto& b : rows)
                if (b.supported && b.brightness - a.brightness >= .04) {
                    double s = (b.variance - a.variance) / (b.brightness - a.brightness);
                    consider({s, a.variance - s * a.brightness});
                }
        }
    if (!std::isfinite(best))
        return {};
    for (int pass = 0; pass < 3; ++pass) {
        double sw = 0, sb = 0, sv = 0, sbb = 0, sbv = 0, lo = INFINITY, hi = 0;
        int n = 0, samples = 0;
        for (auto& r : rows) {
            double p = model.s * r.brightness + model.o;
            if (!r.supported || r.variance > p * gate || r.variance * gate < p)
                continue;
            double w = std::min(r.samples, 128) / (p * p);
            sw += w;
            sb += w * r.brightness;
            sv += w * r.variance;
            sbb += w * r.brightness * r.brightness;
            sbv += w * r.brightness * r.variance;
            lo = std::min(lo, r.brightness);
            hi = std::max(hi, r.brightness);
            ++n;
            samples += r.samples;
        }
        if (n < 5 || samples < 128 || hi - lo < .04 || sw <= 0)
            return {};
        double vb = sbb / sw - std::pow(sb / sw, 2);
        if (vb < .005 * .005)
            return {};
        model.s = (sbv / sw - sb / sw * sv / sw) / vb;
        model.o = (sv - model.s * sb) / sw;
        if (model.s < 0)
            model = {0, sv / sw};
        else if (model.o < 0)
            model = {sbv / sbb, 0};
    }
    return model;
}

std::optional<NoiseModel> measureNoise(ComputeContext& ctx, const BurstOptions& opt,
                                       const std::vector<BurstFrame>& frames, NoiseModel prior) {
    const auto& ref = frames.front();
    if (ref.iso <= 0 || std::abs(ref.layerMultiplier - 1) > 1e-4f)
        return {};
    std::vector<std::size_t> eligible;
    for (std::size_t i = 0; i < frames.size(); ++i)
        if (frames[i].iso == ref.iso &&
            std::abs(frames[i].layerMultiplier - ref.layerMultiplier) <= 1e-4f)
            eligible.push_back(i);
    int count = int(std::min<std::size_t>(eligible.size(), opt.tunables.noiseBlendMaxFrames));
    static constexpr int dx[] = {0, 1, -1, 0, 0, 1, 1, -1, -1}, dy[] = {0, 0, 0, 1, -1, 1, -1, 1, -1};
    double sum = 0, squares = 0;
    std::array<double, 9> weights;
    for (int k = 0; k < 9; ++k) {
        weights[k] = std::exp(-.5 * (dx[k] * dx[k] + dy[k] * dy[k]));
        if (k < count) {
            sum += weights[k];
            squares += weights[k] * weights[k];
        }
    }
    auto g = geometryFor(opt.sensor);
    int w = g.rawHalfWidth + g.shiftX, h = g.rawHalfHeight + g.shiftY;
    double response = .4234514529592069;
    if (opt.binned && opt.sensor.cfa >= 0 && opt.sensor.cfa <= 3)
        response = g.shiftX || g.shiftY ? .3732768036438227 : .4114817648384735;
    response *= opt.tunables.noiseBlendCalMpy * std::sqrt(squares) / sum;
    double maxBlack = *std::max_element(opt.sensor.black.begin(), opt.sensor.black.end());
    double range = std::sqrt(prior.s / 3 + prior.o) * opt.tunables.noiseHistogramRange;
    if (range <= 0)
        range = .12;
    range = std::max({std::min(range, 1.), .00025, 8 / std::max(1., opt.sensor.white - maxBlack)});
    float scale = float(63 / (response * range));
    std::array<int, 4096> histogram{};
    int stride = opt.tunables.noiseScanSubsample;
    while (stride > 1 && std::int64_t(w) * h / (stride * stride) < 16384)
        --stride;
    using Shader = shaders::noise;
    auto& gpu = ctx.device();
    const auto area = std::size_t(w) * h;
    if (area > gpu.getDeviceProperties().limits.maxStorageBufferRange / 8)
        throw std::invalid_argument("Burst exceeds Vulkan storage-buffer range");
    auto input = gpu.tensorT<std::uint32_t>(area * 2);
    auto blend = gpu.tensorT<std::uint32_t>(area * 2, kp::Memory::MemoryTypes::eStorage);
    auto bins = gpu.tensorT(std::vector<std::uint32_t>(4096, 0));
    using Objects = std::vector<std::shared_ptr<kp::Memory>>;
    gpu.sequence()->eval<kp::OpSyncDevice>(Objects{bins});
    TensorPass accumulate;
    for (int k = 0; k < count; ++k) {
        auto i = eligible[count == 1 ? 0 : std::lround(double(k) * (eligible.size() - 1) / (count - 1))];
        packBayerInto(opt.sensor, frames[i].pixels, frames[i].format, 1 / ref.layerMultiplier,
                      input->data(), input->size());
        auto seq = gpu.sequence();
        seq->record<kp::OpSyncDevice>(Objects{input});
        auto program = accumulate.useProgram(gpu, *seq, assets::noise_blend, w, h);
        program.setVar(Shader::extent, {w, h});
        program.setVar(Shader::offset, {dx[k], dy[k]});
        program.setVar(Shader::weight, float(weights[k] / sum));
        program.setVar(Shader::first, int(k == 0));
        program.bindInputs(Shader::Frame(input), Shader::Histogram(bins)).writeTo(Shader::Blend(blend));
        seq->record<kp::OpMemoryBarrier>(
               Objects{blend}, vk::AccessFlagBits::eShaderWrite, vk::AccessFlagBits::eShaderRead,
               vk::PipelineStageFlagBits::eComputeShader, vk::PipelineStageFlagBits::eComputeShader)
            ->eval();
    }
    // The original histogram rounded the subsampled grid to whole 8x8 groups.
    // Retain its edge samples without dispatching more rows than the full image.
    auto scanExtent = [stride](int extent) {
        return std::min(extent, ((extent / stride + 7) / 8) * 8);
    };
    TensorPass histogramPass;
    auto seq = gpu.sequence();
    auto scan = histogramPass.useProgram(gpu, *seq, assets::noise_histogram, scanExtent(w), scanExtent(h));
    scan.setVar(Shader::extent, {w, h});
    scan.setVar(Shader::stride, stride);
    scan.setVar(Shader::scale, scale);
    scan.bindInputs(Shader::Frame(input), Shader::Blend(blend)).writeTo(Shader::Histogram(bins));
    seq->record<kp::OpSyncLocal>(Objects{bins})->eval();
    std::copy_n(bins->data(), histogram.size(), histogram.begin());
    return fit(histogram, scale, response, opt.tunables.noiseBrightnessTextureGate);
}
} // namespace

CalibratedNoise calibrateNoise(ComputeContext& ctx, const BurstOptions& opt,
                               const std::vector<BurstFrame>& frames) {
    const auto baseline = opt.sensorNoise;
    if (!opt.tunables.enableAdaptiveNoise)
        return {baseline, {}};
    const auto& c = opt.calibration;
    const auto& tuning = opt.tunables;
    if (tuning.noiseBlendMaxFrames < 1 || tuning.noiseBlendMaxFrames > 9 ||
        tuning.noiseScanSubsample < 1 || tuning.noiseScanSubsample > 8 ||
        !std::isfinite(tuning.noiseHistogramRange) || tuning.noiseHistogramRange < 2 ||
        !std::isfinite(tuning.noiseBrightnessTextureGate) || tuning.noiseBrightnessTextureGate < 1.2 ||
        !std::isfinite(tuning.noiseBlendCalMpy) || tuning.noiseBlendCalMpy <= 0 ||
        !std::isfinite(tuning.lowIsoNoiseVarianceLimit) || tuning.lowIsoNoiseVarianceLimit < 1)
        throw std::invalid_argument("Invalid noise calibration settings");
    const int iso = frames.front().iso;
    auto resolve = [&](NoiseEstimate estimate) {
        auto m = estimate.model;
        if (!std::isfinite(m.s) || m.s < 0 || !std::isfinite(m.o) || m.o < 0)
            throw std::invalid_argument("Invalid noise estimate");
        if (iso > 0 && iso <= std::max(200, opt.analogIso / 8) && !estimate.higherIsoReference &&
            baseline.s + baseline.o > 0) {
            if (m.o > tuning.lowIsoNoiseVarianceLimit * baseline.o && m.s <= 1e-10)
                m.s = tuning.lowIsoNoiseVarianceLimit * baseline.s;
            m.s = std::min(m.s, tuning.lowIsoNoiseVarianceLimit * baseline.s);
            m.o = std::min(m.o, tuning.lowIsoNoiseVarianceLimit * baseline.o);
        }
        m.o = std::max(m.o, .05 * baseline.o);
        return m;
    };
    auto prior = tuning.enableNoiseStore && c.prior ? resolve(*c.prior) : baseline;
    auto measurement = measureNoise(ctx, opt, frames, prior);
    if (!measurement)
        return {prior, {}};
    auto model = resolve({*measurement, tuning.enableNoiseStore && c.prior && c.prior->higherIsoReference});
    model.s = std::max(1e-10, model.s);
    if (tuning.enableNoiseStore && c.commit)
        if (auto stored = c.commit(model))
            model = resolve(*stored);
    if (tuning.enableFitOCorrection)
        model.o += model.s * model.s * 3 / 8;
    return {model, measurement};
}

void EstimateNoise::run(BurstState& state) {
    const auto noise = calibrateNoise(*state.context, state.options, state.frames);
    state.result.appliedNoise = noise.applied;
    state.result.measuredNoise = noise.measured;
}
} // namespace photon::esd4d
