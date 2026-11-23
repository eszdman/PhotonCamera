//
// Created by eszdman on 22.11.2026.
//

#include "PostGraph.hpp"
#include "Bayer.hpp"
#include <algorithm>
#include <cmath>

namespace photon::post {
namespace {

void validate(kp::Manager& gpu, Size image, size_t count, const LinearSettings& settings,
              const BayerSettings* raw) {
    if (image.x <= 0 || image.y <= 0 || image.x % 2 || image.y % 2 || count != size_t(image.x) * image.y)
        throw std::invalid_argument("Expected an even image with one CFA sample per pixel");
    const auto limits = gpu.getDeviceProperties().limits;
    if (count > limits.maxStorageBufferRange / 8 ||
        (unsigned(image.x) + 7) / 8 > limits.maxComputeWorkGroupCount[0] ||
        (unsigned(image.y) + 7) / 8 > limits.maxComputeWorkGroupCount[1])
        throw std::length_error("Full RGB image exceeds Vulkan device limits");
    if (settings.demosaicing == Demosaicing::amaze) {
        const int tile = settings.tileSize;
        if (tile <= 0 || tile % 2) throw std::invalid_argument("AMaZE tile size must be positive and even");
        auto w = uint64_t(std::min(tile, image.x)) + 96, h = uint64_t(std::min(tile, image.y)) + 96;
        if (w * h > limits.maxStorageBufferRange / 8 || (w + 7) / 8 > limits.maxComputeWorkGroupCount[0] ||
            (h + 7) / 8 > limits.maxComputeWorkGroupCount[1] ||
            std::min(limits.maxPerStageDescriptorStorageBuffers, limits.maxDescriptorSetStorageBuffers) < 6)
            throw std::length_error("AMaZE window exceeds Vulkan device limits");
    }
    if (!std::isfinite(settings.noiseS) || !std::isfinite(settings.noiseO) || settings.noiseS < 0 ||
        settings.noiseO < 0)
        throw std::invalid_argument("Invalid post noise model");
    if (settings.esd3d.enabledFor(settings)) settings.esd3d.validate();
    if (raw && (raw->mapSize.x < 1 || raw->mapSize.y < 1 ||
                raw->gainMap.size() != uint64_t(raw->mapSize.x) * raw->mapSize.y * 2 ||
                raw->gainMap.size() > limits.maxStorageBufferRange / 4))
        throw std::invalid_argument("Invalid RGBA16F gain map");
}

template <typename Sample>
std::vector<uint32_t> run(esd4d::ComputeContext& context, Size image, const std::vector<Sample>& cfa,
                          const LinearSettings& settings, const BayerSettings* raw) {
    auto& gpu = context.device();
    if (settings.demosaicing == Demosaicing::mergedRgb)
        throw std::invalid_argument("This checkpoint requires a Bayer input");
    validate(gpu, image, cfa.size(), settings, raw);
    auto input = gpu.tensorT<float>(cfa.size());
    if constexpr (std::is_same_v<Sample, uint16_t>)
        std::transform(cfa.begin(), cfa.end(), input->data(), esd4d::halfValue);
    else
        std::copy(cfa.begin(), cfa.end(), input->data());
    auto output = gpu.tensorT<uint32_t>(2 * cfa.size());
    auto sequence = gpu.sequence();
    PostState state{gpu, *sequence, image, settings, {input, image}, {output, image}, {output, image}};
    if (settings.esd3d.enabledFor(settings) != settings.blackLevels.enable) state.rgb = state.nextRgb();
    const LinearStages linear{settings, raw, raw ? raw->offset : Size{}};
    state.upload({input});
    auto nodes = buildDefaultPipeline(&linear, nullptr, nullptr);
    nodes.runAll(state);
    state.download({output});
    return output->vector();
}
} // namespace

SdrImage renderPostPipeline(esd4d::ComputeContext& context, Size image, const std::vector<uint16_t>& merged,
                            const PostPipelineSettings& p, SdrImage* ultraHdrGainMap, float gainMapScale) {
    auto& gpu = context.device();
    const bool rgbInput = p.linear.demosaicing == Demosaicing::mergedRgb;
    const size_t channels = rgbInput ? 3 : 1;
    if (merged.size() % channels) throw std::invalid_argument("Incomplete post pixel");
    validate(gpu, image, merged.size() / channels, p.linear, &p.bayer);
    Size capture = image;
    Size offset = p.bayer.offset;
    if (p.wideAspect) {
        if (image.x > image.y) {
            capture = {image.x & ~3, std::max(4, (image.x * 9 / 16) & ~3)};
            offset.y += 2 * ((image.y - image.x * 9 / 16) / 4);
        } else {
            capture = {std::max(4, (image.y * 9 / 16) & ~3), image.y & ~3};
            offset.x += 2 * ((image.x - image.y * 9 / 16) / 4);
        }
    }
    Size work = capture;
    if (p.fullRawSize.x > 0 && p.fullRawSize.y > 0)
        work = {std::max(4, p.fullRawSize.x & ~3), std::max(4, p.fullRawSize.y & ~3)};
    bool cropped = p.fullRawSize.x > 0 && p.fullRawSize.y > 0 && (work.x != image.x || work.y != image.y);
    Size oriented = work;
    if (p.display.rotationDegrees % 180) std::swap(oriented.x, oriented.y);
    if (p.display.rotationDegrees < 0 || p.display.rotationDegrees > 270 || p.display.rotationDegrees % 90 ||
        uint64_t(work.x) * work.y > gpu.getDeviceProperties().limits.maxStorageBufferRange / 8)
        throw std::invalid_argument("Invalid post output dimensions or rotation");

    auto sink = gpu.tensorT<uint32_t>(size_t(work.x) * work.y);
    auto seq = gpu.sequence();
    PostState s{gpu, *seq, image, p.linear, {}, {}, {}};
    s.captureSize = capture;
    s.rgb = s.destination = s.rgba(image);
    if (rgbInput) {
        auto input = gpu.tensorT<uint32_t>(merged.size() / 3 * 2);
        for (size_t i = 0; i < merged.size() / 3; ++i) {
            input->data()[2 * i] = merged[3 * i] | (uint32_t(merged[3 * i + 1]) << 16);
            input->data()[2 * i + 1] = merged[3 * i + 2] | 0x3c000000u;
        }
        s.rgb = {input, image};
        s.upload({input});
    } else {
        auto input = gpu.tensorT<float>(merged.size());
        std::transform(merged.begin(), merged.end(), input->data(), esd4d::halfValue);
        s.cfa = {input, image};
        s.upload({input});
    }
    s.sharpenScale = cropped ? p.display.cropSharpenScale : 1.f;
    const LinearStages linear{p.linear, &p.bayer, offset};
    const CaptureStages captureStages{p, context, merged, work, cropped, ultraHdrGainMap, gainMapScale};
    const DisplayStages display{p.display, {sink, oriented}};
    auto nodes = buildDefaultPipeline(&linear, &captureStages, &display);
    nodes.runAll(s);
    s.download({sink});
    return {oriented, sink->vector()};
}
std::vector<uint32_t> renderLinear(esd4d::ComputeContext& context, Size image, const std::vector<float>& cfa,
                                   const LinearSettings& settings) {
    return run(context, image, cfa, settings, nullptr);
}
std::vector<uint32_t> renderMergedLinear(esd4d::ComputeContext& context, Size image,
                                         const std::vector<uint16_t>& merged, const BayerSettings& raw,
                                         const LinearSettings& settings) {
    return run(context, image, merged, settings, &raw);
}

SdrImage finishDisplay(esd4d::ComputeContext& ctx, const RgbaImage& input, const DisplaySettings& display,
                       float noiseS, float noiseO) {
    const auto pixels = uint64_t(input.size.x) * input.size.y;
    auto& gpu = ctx.device();
    if (input.size.x < 1 || input.size.y < 1 || pixels != input.pixels.size() / 2 || input.pixels.size() % 2 ||
        pixels > gpu.getDeviceProperties().limits.maxStorageBufferRange / 8 || display.rotationDegrees < 0 ||
        display.rotationDegrees > 270 || display.rotationDegrees % 90)
        throw std::invalid_argument("Invalid display image or rotation");
    LinearSettings settings;
    settings.noiseS = noiseS;
    settings.noiseO = noiseO;
    auto in = gpu.tensorT(input.pixels);
    auto out = gpu.tensorT<uint32_t>(pixels);
    auto seq = gpu.sequence();
    Size size = input.size;
    if (display.rotationDegrees % 180) std::swap(size.x, size.y);
    PostState s{gpu, *seq, input.size, settings, {}, {in, input.size}, {}};
    s.sharpenScale = display.cropSharpenScale;
    s.destination = s.rgba(input.size);
    s.upload({in});
    const DisplayStages stages{display, {out, size}};
    auto nodes = buildDefaultPipeline(nullptr, nullptr, &stages);
    nodes.runAll(s);
    s.download({out});
    return {size, out->vector()};
}
} // namespace photon::post
