//
// Created by eszdman on 19.11.2026.
//

#include "KernelMap.hpp"
#include "KernelTile.hpp"
#include "halfConvert.h"
#include "Bayer.hpp"
#include "framework/esd4d/ComputeContext.hpp"
#include "framework/esd4d/PassParameters.hpp"
#include <algorithm>
#include <cmath>
#include <cmrc/cmrc.hpp>
#include <kompute/Kompute.hpp>
#include <stdexcept>

CMRC_DECLARE(esdmodel);

namespace photon::esd4d {
namespace {
struct Model {
    std::array<Layer, 6> layers{};
    std::vector<float> values;
    Model() {
        auto file = cmrc::esdmodel::get_filesystem().open("kernelnet_aniso_v2_2_params.ncnn.bin");
        auto pos = file.begin();
        auto read = [&](auto& value) {
            if (std::size_t(file.end() - pos) < sizeof(value))
                throw std::runtime_error("Truncated KernelNet weights");
            std::memcpy(&value, pos, sizeof(value));
            pos += sizeof(value);
        };
        for (int k = 0; k < 6; ++k) {
            auto& l = layers[k];
            l.inputs = k == 0 ? 2 : 16;
            l.outputs = k == 5 ? 3 : 16;
            l.step = k == 0 ? 2 : 1;
            l.radius = k == 5 ? 0 : 1;
            int side = l.radius * 2 + 1;
            l.weights = int(values.size());
            std::uint32_t tag;
            read(tag);
            if (tag != 0x01306b47)
                throw std::runtime_error("Expected FP16 KernelNet convolution");
            const int taps = l.inputs * side * side;
            values.resize(values.size() + l.outputs * taps);
            for (int i = 0; i < l.outputs * taps; ++i) {
                std::uint16_t h;
                read(h);
                // Hidden layers compute four outputs from each input tap. Interleave
                // their coefficients so the shader reads one aligned vec4 per tap.
                const int channel = i / taps;
                const int offset = l.outputs == 16 ? (channel / 4 * taps + i % taps) * 4 + channel % 4 : i;
                values[l.weights + offset] = halfValue(h);
            }
            l.biases = int(values.size());
            for (int i = 0; i < l.outputs; ++i) {
                float bias;
                read(bias);
                values.push_back(bias);
            }
        }
        if (pos != file.end())
            throw std::runtime_error("Unexpected KernelNet data");
    }
};
} // namespace

PackedQuads inferKernelMap(ComputeContext& ctx, const std::vector<float>& luma, int w, int h,
                           float sigma) {
    if (w <= 0 || h <= 0 || std::uint64_t(w) * h != luma.size() || !std::isfinite(sigma) || sigma < 0)
        throw std::invalid_argument("Invalid KernelNet brightness or sigma");
    static const Model model;
    auto& gpu = ctx.device();
    auto shape = imageWorkgroup(gpu, "kernel_conv", KernelTile::full / 2);
    const auto first = shape.shader("kernel_conv_first");
    const auto hidden = shape.shader("kernel_conv_hidden");
    const auto last = shape.shader("kernel_conv");
    auto weights = gpu.tensor(model.values);
    using Objects = std::vector<std::shared_ptr<kp::Memory>>;
    gpu.sequence()->eval<kp::OpSyncDevice>(Objects{weights});
    // Match the app's tile origins and right/bottom replicated padding. Only
    // materialize the halo actually consumed by the five 3x3 convolutions.
    int ow = (w + 1) / 2, oh = (h + 1) / 2;
    PackedQuads map(std::size_t(ow) * oh * 2);
    for (int ty = 0; ty < KernelTile::count(h); ++ty)
        for (int tx = 0; tx < KernelTile::count(w); ++tx) {
            auto horizontal = KernelTile::at(tx, w), vertical = KernelTile::at(ty, h);
            int ox = horizontal.origin, oy = vertical.origin;
            int tw = horizontal.extent, th = vertical.extent;
            auto input = gpu.tensorT<float>(std::size_t(tw) * th * 2);
            float* packed = input->data();
            for (int y = 0; y < th; ++y)
                for (int x = 0; x < tw; ++x) {
                    auto at = (std::size_t(y) * tw + x) * 2;
                    packed[at] = luma[std::size_t(std::min(oy + y, h - 1)) * w + std::min(ox + x, w - 1)];
                    packed[at + 1] = sigma;
                }

            auto featureCount = std::size_t(tw / 2) * (th / 2) * 16;
            auto a = gpu.tensorT<float>(featureCount, kp::Memory::MemoryTypes::eStorage);
            auto b = gpu.tensorT<float>(featureCount, kp::Memory::MemoryTypes::eStorage);
            auto output = gpu.tensorT<float>(featureCount / 16 * 3);
            auto seq = gpu.sequence();
            seq->record<kp::OpSyncDevice>(Objects{input});
            auto previous = input;
            for (unsigned k = 0; k < model.layers.size(); ++k) {
                auto l = model.layers[k];
                l.width = k ? tw / 2 : tw;
                l.height = k ? th / 2 : th;
                auto next = k == 5 ? output : (k % 2 ? b : a);
                auto groups = shape.groups(tw / 2, th / 2, l.outputs == 16 ? 4 : 3);
                const auto& code = k == 0 ? first : (k == 5 ? last : hidden);
                auto pass = gpu.algorithm<float, Layer>({previous, weights, next}, code, groups, {}, {l});
                seq->record<kp::OpAlgoDispatch>(pass);
                if (k != 5)
                    seq->record<kp::OpMemoryBarrier>(Objects{next}, vk::AccessFlagBits::eShaderWrite,
                                                     vk::AccessFlagBits::eShaderRead,
                                                     vk::PipelineStageFlagBits::eComputeShader,
                                                     vk::PipelineStageFlagBits::eComputeShader);
                previous = next;
            }
            seq->record<kp::OpSyncLocal>(Objects{output})->eval();
            auto pixels = output->data();
            for (int y = vertical.begin; y < vertical.end; ++y)
                for (int x = horizontal.begin; x < horizontal.end; ++x) {
                    auto p = pixels + 3 * ((y - oy / 2) * (tw / 2) + x - ox / 2);
                    auto at = (std::size_t(y) * ow + x) * 2;
                    map[at] = f32ToF16(p[0]) | (std::uint32_t(f32ToF16(p[1])) << 16);
                    map[at + 1] = f32ToF16(p[2]) | 0x3c000000u;
                }
        }
    return map;
}
} // namespace photon::esd4d
