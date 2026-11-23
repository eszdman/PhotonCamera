//
// Created by eszdman on 19.11.2026.
//

#include "Pyramid.hpp"
#include "halfConvert.h"
#include "framework/esd4d/ComputeContext.hpp"
#include "EsdShaders.hpp"
#include <algorithm>
#include <cmath>
#include <kompute/Kompute.hpp>
#include <stdexcept>

namespace photon::esd4d {
namespace {
using Objects = std::vector<std::shared_ptr<kp::Memory>>;
using QuadTensor = std::shared_ptr<kp::TensorT<std::uint32_t>>;
struct Level {
    int width, height;
    QuadTensor values;
};
} // namespace
struct BlockAlignment::State {
    kp::Manager& gpu;
    Sensor sensor;
    PyramidOptions opts;
    int alignmentStartLevel;
    NoiseModel noise;
    std::array<float, 4> black{};
    std::shared_ptr<kp::TensorT<float>> gains;
    std::vector<Level> reference;
    int levels;
    float prefilterNorm;

    State(ComputeContext& ctx, const BurstOptions& burst, const BurstFrame& ref, NoiseModel model)
        : gpu(ctx.device()), sensor(burst.sensor), opts(burst.pyramid),
          alignmentStartLevel(burst.tunables.alignmentStartLevel), noise(model) {
        if (sensor.width < 6 || sensor.height < 6 || alignmentStartLevel < 0 || alignmentStartLevel > 2 ||
            opts.gainWidth < 1 || opts.gainHeight < 1 ||
            std::uint64_t(opts.gainWidth) * opts.gainHeight != opts.gains.size() ||
            !std::all_of(
                opts.gains.begin(), opts.gains.end(),
                [](float x) { return std::isfinite(x) && x > 0; }) ||
            !std::isfinite(opts.sharpness))
            throw std::invalid_argument("Invalid block-pyramid geometry or settings");
        // The pyramid starts at raw/2. The Java matcher uses length-2 as its coarsest level.
        levels = std::max(alignmentStartLevel + 1, int(std::log2(sensor.width / 2)) - 2);
        double sum = 0, squares = 0;
        for (int i = -2; i <= 2; ++i) {
            double weight = std::exp(-i * i / 4.5);
            sum += weight;
            squares += weight * weight;
        }
        prefilterNorm = float(sum * sum / squares);
        noise.s = std::max(1e-6, model.s * std::exp2(burst.mergeStrength));
        noise.o = std::max(1e-6, model.o * std::exp2(burst.mergeStrength));
        gains = gpu.tensor(opts.gains);
        gpu.sequence()->eval<kp::OpSyncDevice>(Objects{gains});
        reference = pyramid(ref, true);
    }

    Level prepare(const Level& src, int stage, int w, int h, float exposure) {
        Level dst{w, h, gpu.tensorT<std::uint32_t>(std::size_t(w) * h * 2)};
        auto seq = gpu.sequence();
        using Shader = shaders::pyramid_prepare;
        TensorPass pass;
        auto program = pass.useProgram(gpu, *seq, assets::pyramid_prepare, w, h);
        program.setVar(Shader::size, {w, h});
        program.setVar(Shader::sourceSize, {src.width, src.height});
        program.setVar(Shader::gainSize, {opts.gainWidth, opts.gainHeight});
        program.setVar(Shader::stage, stage);
        program.setVar(Shader::precisionMode, int(opts.precision));
        program.setVar(Shader::black, black);
        program.setVar(Shader::exposure, exposure);
        program.setVar(Shader::sharpness, opts.sharpness);
        program.bindInputs(Shader::Source(src.values), Shader::Gain(gains)).writeTo(Shader::Destination(dst.values));
        seq->record<kp::OpMemoryBarrier>(
               Objects{dst.values}, vk::AccessFlagBits::eShaderWrite, vk::AccessFlagBits::eShaderRead,
               vk::PipelineStageFlagBits::eComputeShader, vk::PipelineStageFlagBits::eComputeShader)
            ->eval();
        return dst;
    }

    std::vector<Level> pyramid(const BurstFrame& frame, bool isReference) {
        // The prefilter consumes native-parity quads before ESD4D's CFA shift.
        Sensor unshifted = sensor;
        unshifted.cfa = 0;
        int w = sensor.width / 2, h = sensor.height / 2;
        Level src{w, h, gpu.tensorT(packBayer(unshifted, frame.pixels, frame.format, 1))};
        gpu.sequence()->eval<kp::OpSyncDevice>(Objects{src.values});
        auto temp = prepare(src, 0, w, h, isReference ? 1 : 1 / frame.layerMultiplier);
        src.values.reset();
        if (isReference) {
            gpu.sequence()->eval<kp::OpSyncLocal>(Objects{temp.values});
            std::array<std::array<unsigned, 1024>, 4> hist{};
            unsigned count = 0;
            auto data = temp.values->data();
            for (int y = 0; y < h; y += 3)
                for (int x = 0; x < w; x += 3) {
                    for (int c = 0; c < 4; ++c) {
                        auto bits =
                            std::uint16_t(data[(std::size_t(y) * w + x) * 2 + c / 2] >> (16 * (c % 2)));
                        ++hist[c][std::clamp(int(halfValue(bits) * 64 * 1023), 0, 1023)];
                    }
                    ++count;
                }
            for (int c = 0; c < 4; ++c) {
                unsigned mass = 0;
                for (int j = 0; j < 1024; ++j)
                    if ((mass += hist[c][j]) > .3 * count) {
                        black[c] = float(j) / (1023 * 64);
                        break;
                    }
            }
        }
        std::vector<Level> result{prepare(temp, 1, w, h, 1)};
        temp.values.reset();
        while (int(result.size()) < levels) {
            w = std::max(1, w / 2);
            h = std::max(1, h / 2);
            result.push_back(prepare(result.back(), 2, w, h, 1));
        }
        return result;
    }

    Warp estimate(const BurstFrame& frame) {
        auto alt = pyramid(frame, false);
        auto prev = gpu.tensor(std::vector<float>(4));
        int nx = 1, ny = 1;
        int w = sensor.width / 2, h = sensor.height / 2;
        for (int i = levels - 1; i >= alignmentStartLevel; --i) {
            auto& l = alt[i];
            float norm =
                prefilterNorm *
                std::sqrt(double(w) * h / (std::max(1, l.width / 2) * std::max(1, l.height / 2)));
            const std::array<int, 2> previousSize{nx, ny};
            nx = l.width / 8 + 1;
            ny = l.height / 8 + 1;
            auto out = gpu.tensorT<float>(std::size_t(nx) * ny * 4);
            auto seq = gpu.sequence();
            using Shader = shaders::pyramid_match;
            TensorPass pass;
            auto program = pass.useWorkgroups(gpu, *seq, assets::pyramid_match, {unsigned(nx), unsigned(ny), 1});
            program.setVar(Shader::size, {l.width, l.height});
            program.setVar(Shader::rawHalf, {w, h});
            program.setVar(Shader::previousSize, previousSize);
            program.setVar(Shader::first, int(i == levels - 1));
            program.setVar(Shader::precisionMode, int(opts.precision));
            program.setVar(Shader::noiseS, float(noise.s));
            program.setVar(Shader::noiseO, float(noise.o));
            program.setVar(Shader::exposure, 1.f / frame.layerMultiplier);
            program.setVar(Shader::integralNorm, norm);
            program.bindInputs(Shader::Base(reference[i].values), Shader::Alternate(l.values), Shader::Previous(prev))
                   .writeTo(Shader::Result(out));
            seq->record<kp::OpMemoryBarrier>(
                   Objects{out}, vk::AccessFlagBits::eShaderWrite, vk::AccessFlagBits::eShaderRead,
                   vk::PipelineStageFlagBits::eComputeShader, vk::PipelineStageFlagBits::eComputeShader)
                ->eval();
            prev = out;
        }
        gpu.sequence()->eval<kp::OpSyncLocal>(Objects{prev});
        Warp warp;
        warp.fieldWidth = w / 8 + 1;
        warp.fieldHeight = h / 8 + 1;
        warp.field.resize(std::size_t(warp.fieldWidth) * warp.fieldHeight * 4);
        int step = 1 << alignmentStartLevel;
        for (int y = 0; y < warp.fieldHeight; ++y)
            for (int x = 0; x < warp.fieldWidth; ++x) {
                auto src =
                    prev->data() + 4 * (std::min(y / step, ny - 1) * nx + std::min(x / step, nx - 1));
                auto dst = warp.field.data() + 4 * (y * warp.fieldWidth + x);
                for (int c = 0; c < 2; ++c) {
                    float extent = float(c ? h : w);
                    float d = (std::floor(src[c] * extent + .5f) + src[c + 2]) * step;
                    dst[c] = halfValue(f32ToF16(std::floor(d) / extent));
                    dst[c + 2] = halfValue(f32ToF16(d - std::floor(d)));
                }
            }
        return warp;
    }
};
BlockAlignment::BlockAlignment(ComputeContext& ctx, const BurstOptions& opts, const BurstFrame& ref,
                               NoiseModel model)
    : state_(std::make_unique<State>(ctx, opts, ref, model)) {}
BlockAlignment::~BlockAlignment() = default;
Warp BlockAlignment::estimate(const BurstFrame& frame) { return state_->estimate(frame); }
} // namespace photon::esd4d
