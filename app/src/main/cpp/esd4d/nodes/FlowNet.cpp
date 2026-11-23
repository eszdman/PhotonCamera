//
// Created by eszdman on 19.11.2026.
//

#include "FlowNet.hpp"
#include "halfConvert.h"
#include "flownetcorrfused.h"
#include "flownetcorrlookup.h"
#include "flownetupsample.h"
#include "net.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace photon::esd4d {
namespace {
constexpr int width = 512, height = 384;
template <class T> ncnn::Layer* create(void*) { return new T; }

ncnn::Mat flowInput(const Sensor& sensor, const BurstFrame& frame, float exposure) {
    ncnn::Mat input(width, height, 3);
    if (input.empty())
        throw std::bad_alloc();
    int w = sensor.width / 2, h = sensor.height / 2;
    auto quad = [&](int x, int y) {
        auto i = std::size_t(2 * y) * sensor.width + 2 * x;
        float q[] = {halfValue(frame.pixels[i]), halfValue(frame.pixels[i + 1]),
                     halfValue(frame.pixels[i + sensor.width]),
                     halfValue(frame.pixels[i + sensor.width + 1])};
        // Keep optical/flowRGB's trained channel order for each CFA.
        switch (sensor.cfa) {
        case 0:
            return std::array<float, 3>{q[3], q[1], q[0]};
        case 1:
            return std::array<float, 3>{q[2], (q[0] + q[3]) * .5f, q[1]};
        case 2:
            return std::array<float, 3>{q[1], (q[0] + q[3]) * .5f, q[3]};
        default:
            return std::array<float, 3>{q[0], (q[1] + q[2]) * .5f, q[3]};
        }
    };
    for (int y = 0; y < height; ++y)
        for (int x = 0; x < width; ++x) {
            float u = (x + .5f) * w / width, v = (y + .5f) * h / height;
            int ix = std::clamp(int(u), 0, w - 2), iy = std::clamp(int(v), 0, h - 2);
            float fx = u - std::floor(u), fy = v - std::floor(v);
            auto a = quad(ix, iy), b = quad(ix + 1, iy), c = quad(ix, iy + 1), d = quad(ix + 1, iy + 1);
            for (int k = 0; k < 3; ++k) {
                float top = a[k] + fx * (b[k] - a[k]), bottom = c[k] + fx * (d[k] - c[k]);
                float value = std::clamp(top + fy * (bottom - top), 0.f, 1.f) * exposure * 255;
                input.channel(k).row(y)[x] = halfValue(f32ToF16(std::clamp(value, 0.f, 255.f)));
            }
        }
    return input;
}
} // namespace
MotionProvider makeFlowNetMotion(Sensor sensor, const std::string& params, const std::string& weights,
                                 int threads) {
    geometryFor(sensor);
    if (sensor.width < 4 || sensor.height < 4 || sensor.cfa < 0 || sensor.cfa > 3 || threads < 1)
        throw std::invalid_argument("Invalid FlowNet geometry or thread count");
    auto net = std::make_shared<ncnn::Net>();
    net->opt.num_threads = threads;
    net->opt.use_fp16_storage = false;
    net->opt.use_bf16_storage = false;
    net->opt.use_fp16_arithmetic = false;
    net->opt.use_vulkan_compute = false;
    net->register_custom_layer("FlownetUpsample", create<ncnn::FlownetUpsample>);
    net->register_custom_layer("FlownetCorrLookup", create<ncnn::FlownetCorrLookup>);
    net->register_custom_layer("FlownetCorrFused", create<ncnn::FlownetCorrFused>);
    if (net->load_param(params.c_str()) || net->load_model(weights.c_str()))
        throw std::runtime_error("Could not load the FlowNet ncnn model");
    return [net, sensor](std::size_t, const BurstFrame& ref, const BurstFrame& alt) {
        const auto count = std::size_t(sensor.width) * sensor.height;
        if (ref.pixels.size() != count || alt.pixels.size() != count ||
            ref.format != SampleFormat::NormalizedF16 || alt.format != SampleFormat::NormalizedF16)
            throw std::invalid_argument("FlowNet requires a matching normalized Bayer pair");
        auto ex = net->create_extractor();
        ncnn::Mat output;
        if (ex.input("in0", flowInput(sensor, ref, alt.layerMultiplier)) ||
            ex.input("in1", flowInput(sensor, alt, 1)) || ex.extract("out0", output) ||
            output.w != width || output.h != height || output.c != 2 || output.elemsize != 4 ||
            output.elempack != 1)
            throw std::runtime_error("Invalid FlowNet inference result");
        Warp warp;
        warp.sampling = Sampling::FlowNet;
        warp.fieldWidth = width;
        warp.fieldHeight = height;
        warp.field.resize(std::size_t(width) * height * 4);
        for (int c = 0; c < 2; ++c) {
            const float* plane = output.channel(c);
            float scale = c ? float(sensor.height / 2) / height : float(sensor.width / 2) / width;
            for (int i = 0; i < width * height; ++i)
                warp.field[i * 4 + c] = halfValue(f32ToF16(plane[i] * scale));
        }
        return warp;
    };
}
} // namespace photon::esd4d
