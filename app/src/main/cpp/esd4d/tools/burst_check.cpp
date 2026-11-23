//
// Created by eszdman on 20.11.2026.
//

#include "halfConvert.h"
#include "packedRaw.hpp"
#include "Alignment.hpp"
#include "Burst.hpp"
#include "Tunables.hpp"
#include "framework/esd4d/ComputeContext.hpp"
#include "HotPixels.hpp"
#include "KernelMap.hpp"
#include "Noise.hpp"
#include "Pyramid.hpp"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <iostream>
#include <random>
#include <sstream>
#include <string_view>
#include <stdexcept>

using namespace photon::esd4d;

namespace {
void require(bool pass, const char* message) {
    if (!pass)
        throw std::runtime_error(message);
}

void testTunables() {
    std::istringstream file("cfaResidualRobustness=false\n"
                            "halideMergeSampling=nearest\n"
                            "noiseMpy=1.25\n"
                            "noiseScanSubsample=2\n");
    auto settings = loadTunables(file);
    std::vector<double> packed{Esd4dSchema::tag};
    Esd4dSchema::visit(settings, [&](const char*, auto value, double, double, std::initializer_list<std::string_view>) {
        packed.push_back(static_cast<double>(value));
    });
    auto decoded = unpackTunables(packed.data(), packed.size());
    require(!decoded.cfaResidualRobustness && decoded.halideMergeSampling == HalideMergeSampling::nearest &&
            decoded.noiseMpy == 1.25f && decoded.noiseScanSubsample == 2 && decoded.enableAdaptiveNoise,
            "Tunable values changed in transit");

    auto rejects = [](auto action) {
        try { action(); } catch (const std::invalid_argument&) { return true; }
        return false;
    };
    require(rejects([&] { unpackTunables(packed.data(), packed.size() - 1); }), "Accepted short snapshot");
    packed.front()++;
    require(rejects([&] { unpackTunables(packed.data(), packed.size()); }), "Accepted stale schema");
    for (auto text : {"unknown=1", "noiseScanSubsample=1.5", "cfaResidualRobustness=2",
                      "noiseMpy=nan", "halideMergeSampling=unknown", "noiseMpy=2\nnoiseMpy=1"}) {
        std::istringstream invalid(text);
        require(rejects([&] { loadTunables(invalid); }), "Accepted invalid tunable");
    }
}

void testBayer() {
    for (int cfa = 0; cfa < 4; ++cfa) {
        Sensor sensor{46, 38, cfa, {64.25f, 62.5f, 60.f, 58.75f}, 4095};
        BurstFrame ref;
        ref.pixels.resize(sensor.width * sensor.height);
        for (std::size_t i = 0; i < ref.pixels.size(); ++i)
            ref.pixels[i] = (i * 173) % 4100;
        auto expected = ref.pixels;
        for (int y = 0; y < sensor.height; ++y)
            for (int x = 0; x < sensor.width; ++x) {
                auto& v = expected[y * sensor.width + x];
                float black = sensor.black[(y % 2) * 2 + x % 2];
                v = f32ToF16(std::clamp((v - black) / (sensor.white - black), 0.f, 1.f));
            }
        BurstOptions opts;
        opts.sensor = sensor;
        opts.tunables.alignerSelect = AlignerSelect::off;
        opts.tunables.enableAdaptiveNoise = false;
        auto output = processBurst(opts, {ref});
        require(output.normalizedBayer == expected,
                "Sensor normalization, CFA padding or unpack mismatch");
        require(output.kernelMap.size() ==
                    std::size_t(output.geometry.kernelWidth) * output.geometry.kernelHeight * 2,
                "Kernel map extent mismatch");
        for (unsigned i = 0; i < output.kernelMap.size(); i += 2) {
            float sx = halfValue(output.kernelMap[i]), sy = halfValue(output.kernelMap[i] >> 16);
            float rho = halfValue(output.kernelMap[i + 1]);
            require(sx >= 0 && sx <= 2 && sy >= 0 && sy <= 2 && std::abs(rho) <= 1 &&
                        (output.kernelMap[i + 1] >> 16) == 0x3c00,
                    "Invalid KernelNet output");
        }
    }
    Sensor small{2, 2, 0, {}, 1023};
    const std::uint8_t bytes[] = {0xff, 0x03, 0x10, 0x80, 0xff};
    require(unpackSensorBits(small, bytes, sizeof(bytes), 10) ==
                std::vector<std::uint16_t>({1023, 0, 1, 1022}),
            "Packed 10-bit decode mismatch");
}

void testHalfPacking() {
    Sensor sensor{256, 256, 0, {}, 1};
    std::vector<std::uint16_t> pixels(65536), expected(pixels.size());
    for (unsigned i = 0; i < pixels.size(); ++i) {
        pixels[i] = std::uint16_t(i);
        const int exponent = (i >> 10) & 31;
        const int fraction = i & 1023;
        float reference = std::ldexp(float(fraction + (exponent ? 1024 : 0)),
                                     exponent ? exponent - 25 : -24);
        if (i & 0x8000) reference = -reference;
        if (exponent == 31) reference = fraction ? NAN : std::copysign(INFINITY, reference);
        const float actual = halfValue(pixels[i]);
        require(std::memcmp(&actual, &reference, sizeof(float)) == 0, "Half decode changed bits");
        expected[i] = f32ToF16(std::clamp(reference, 0.f, 1.f));
    }
    for (int cfa = 0; cfa < 4; ++cfa) {
        sensor.cfa = cfa;
        auto g = geometryFor(sensor);
        auto packed = packBayer(sensor, pixels, SampleFormat::NormalizedF16, 1);
        int width = g.rawHalfWidth + g.shiftX;
        for (int y = 0; y < g.rawHalfHeight + g.shiftY; ++y)
            for (int x = 0; x < width; ++x)
                for (int c = 0; c < 4; ++c) {
                    int sx = std::clamp(2 * x - g.shiftX + c % 2, 0, sensor.width - 1);
                    int sy = std::clamp(2 * y - g.shiftY + c / 2, 0, sensor.height - 1);
                    auto word = packed[2 * (y * width + x) + c / 2];
                    require(std::uint16_t(word >> (16 * (c % 2))) == expected[sy * sensor.width + sx],
                            "Packed half or replicated border differs");
                }
        require(unpackBayer(g, packed) == expected, "Unpacked half differs");
    }
}

void testStagingAndLuma() {
    std::mt19937 rng(191);
    for (int cfa = 0; cfa < 4; ++cfa) {
        Sensor sensor{134, 82, cfa, {64.5f, 61, 60, 63}, 1023};
        auto g = geometryFor(sensor);
        int w = g.rawHalfWidth + g.shiftX, h = g.rawHalfHeight + g.shiftY;
        std::vector<std::uint16_t> pixels(sensor.width * sensor.height);
        for (auto& v : pixels) v = rng() % 1024;
        for (auto format : {SampleFormat::SensorU16, SampleFormat::NormalizedF16}) {
            if (format == SampleFormat::NormalizedF16)
                for (auto& v : pixels) v = rng() % 0x3c01;
            for (float exp : {1.f, .625f, 2.f}) {
                auto packed = packBayer(sensor, pixels, format, exp);
                PackedQuads mapped(packed.size(), 0xdeadbeef);
                packBayerInto(sensor, pixels, format, exp, mapped.data(), mapped.size());
                require(mapped == packed, "Reused staging buffer retained stale bits");
                // Include subnormals, zero, negative zero and nonfinite fallback inputs.
                packed[0] = 1;
                packed[1] = 0;
                packed[11] = 0x80007e00;
                auto luma = kernelLuma(g, packed);
                int pitch = (w + 3) / 4 * 4;
                for (int y = 0; y < h; ++y)
                    for (int x = 0; x < pitch; ++x) {
                        const auto* q = &packed[2 * (y * w + std::min(x, w - 1))];
                        float sum = 0;
                        for (int c = 0; c < 4; ++c)
                            sum += halfValue(std::uint16_t(q[c/2] >> (16 * (c%2))));
                        float expected = halfValue(f32ToF16(std::sqrt(sum * .25f)));
                        require(std::memcmp(&expected, &luma[y * pitch + x], sizeof(float)) == 0,
                                "KernelNet brightness changed bits");
                    }
            }
        }
    }
}

void testPackedRaw() {
    std::mt19937 rng(43);
    for (unsigned bits = 1; bits <= 16; ++bits)
        for (int count = 1; count <= 33; ++count) {
            std::vector<std::uint16_t> expected(count), decoded(count);
            std::vector<std::uint8_t> bytes((bits * count + 7) / 8);
            for (int i = 0; i < count; ++i) {
                expected[i] = rng() & ((1u << bits) - 1);
                for (unsigned bit = 0; bit < bits; ++bit) {
                    unsigned offset = bits * i + bit;
                    bytes[offset / 8] |= ((expected[i] >> bit) & 1u) << (offset % 8);
                }
            }
            photon::unpackSensorSamples(bytes.data(), decoded.data(), count, bits);
            require(decoded == expected, "RAW unpack changed samples");
        }
}

void testKernelNet() {
    // Independent v3 NCNN reference for the single-pass path, including its odd-height edge.
    constexpr int w = 1104, h = 23;
    std::vector<float> luma(w * h);
    for (int y = 0; y < h; ++y)
        for (int x = 0; x < w; ++x)
            luma[y * w + x] = float((x * 17 + y * 13) % 113) / 112;
    const auto geometry = geometryFor({2 * w, 2 * h, 0, {}, 1023});
    require(warmKernelMap(geometry), "First KernelNet warmup was skipped");
    require(!warmKernelMap(geometry), "KernelNet warmup ran twice");
    ComputeContext ctx;
    auto map = inferKernelMap(ctx, luma, w, h, .0125f);
    struct Expected {
        int x, y;
        float values[3];
    };
    const Expected samples[] = {
        {0, 0, {.416250f, .436606f, -.525966f}},   {8, 8, {.303559f, .209664f, -.463688f}},
        {527, 11, {.108305f, .215215f, -.104805f}}, {528, 0, {.160910f, .186959f, -.076894f}},
        {529, 1, {.404116f, .267627f, -.671441f}},  {551, 11, {.257000f, .178467f, .676845f}}};
    // ARM FP16 convolution on the same map.
    const float fp16[][3] = {{.416992f, .436768f, -.525391f}, {.304199f, .210571f, -.460938f},
                             {.107483f, .215698f, -.104004f}, {.161133f, .187378f, -.074219f},
                             {.404053f, .267578f, -.669434f}, {.256836f, .178711f, .675781f}};
    for (size_t i = 0; i < sizeof(samples) / sizeof(samples[0]); ++i)
        for (int c = 0; c < 3; ++c) {
            const auto& sample = samples[i];
            auto word = map[2 * (sample.y * (w / 2) + sample.x) + c / 2];
            float v = halfValue(std::uint16_t(word >> (16 * (c % 2))));
            float error = std::abs(v - sample.values[c]);
            error = std::min(error, std::abs(v - fp16[i][c]));
            require(error < .001f, "KernelNet differs from ncnn reference");
        }
}

void testHotPixels() {
    BurstOptions opt;
    opt.sensor = {48, 38, 1, {}, 4096};
    opt.tunables.alignerSelect = AlignerSelect::off;
    opt.sensorNoise = {.0001, .000001};
    opt.tunables.enableHotPixelCorrection = true;
    std::vector<BurstFrame> frames(5);
    for (auto& f : frames) {
        f.pixels.assign(48 * 38, 1024);
        f.pixels[500] = 3900;
    }
    frames[0].pixels[700] = 3900;
    auto output = processBurst(opt, frames);
    require(output.hotPixelSites == std::vector<std::size_t>{500},
            "Transient or persistent hot-pixel classification failed");
    require(output.normalizedBayer[500] == 0x3400, "Persistent site was not repaired");
    opt.tunables.enableHotPixelCorrection = false;
    auto uncorrected = processBurst(opt, frames);
    require(uncorrected.hotPixelSites.empty() && uncorrected.normalizedBayer[500] > output.normalizedBayer[500],
            "Disabling the hot-pixel node still repaired the persistent site");

    // The proposal cap applies to GPU quads, before the transient site is rejected.
    opt.tunables.enableHotPixelCorrection = true;
    opt.tunables.MAX_HOT_PIXELS = 1;
    auto overflow = processBurst(opt, frames);
    require(overflow.hotPixelSites.empty() && overflow.normalizedBayer == uncorrected.normalizedBayer,
            "A truncated proposal list partially repaired the burst");

    // A second persistent site exceeds the confirmation cap. No frame may be partially repaired.
    for (auto& frame : frames) frame.pixels[1000] = 3900;
    opt.tunables.enableHotPixelCorrection = false;
    auto twoDefects = processBurst(opt, frames);
    opt.tunables.enableHotPixelCorrection = true;
    opt.tunables.MAX_HOT_PIXELS = 32;
    opt.tunables.MAX_REASONABLE_HOTPIXELS = 1;
    auto overLimit = processBurst(opt, frames);
    require(overLimit.hotPixelSites.empty() && overLimit.normalizedBayer == twoDefects.normalizedBayer,
            "Exceeding the confirmed-site limit partially repaired the burst");
}

std::pair<BurstFrame, BurstFrame> translatedPair(Sensor sensor) {
    BurstFrame a, b;
    a.format = b.format = SampleFormat::NormalizedF16;
    int w = sensor.width, h = sensor.height;
    a.pixels.resize(w * h);
    b.pixels.resize(w * h);
    for (int y = 0; y < h; ++y)
        for (int x = 0; x < w; ++x) {
            float f = .4f + .13f * std::sin(x * .12f + y * .07f) + .1f * std::cos(y * .242f - x * .03f) +
                      .08f * std::sin(x * .051f + y * .162f) + .06f * std::sin(x * .3f - y * .14f);
            a.pixels[y * w + x] = f32ToF16(f);
        }
    for (int y = 0; y < h; ++y)
        for (int x = 0; x < w; ++x)
            b.pixels[y * w + x] = a.pixels[std::clamp(y + 2, 0, h - 1) * w + std::clamp(x - 4, 0, w - 1)];
    return {a, b};
}

void testAlignment() {
    BurstOptions options;
    options.sensor = {256, 224, 0, {}, 4095};
    options.sensorNoise = {.0001, .000001};
    auto [ref, alt] = translatedPair(options.sensor);
    auto verify = [&](const Warp& warp, float tolerance) {
        std::vector<float> errors;
        for (int y = 1; y < warp.fieldHeight - 1; ++y)
            for (int x = 1; x < warp.fieldWidth - 1; ++x) {
                auto p = &warp.field[4 * (y * warp.fieldWidth + x)];
                float dx = std::round(p[0] * 128) + p[2], dy = std::round(p[1] * 112) + p[3];
                errors.push_back(std::hypot(dx - 2, dy + 1));
            }
        auto mid = errors.begin() + errors.size() / 2;
        std::nth_element(errors.begin(), mid, errors.end());
        if (*mid > tolerance)
            throw std::runtime_error("Incorrect translation, median vector error " +
                                     std::to_string(*mid));
    };
    ComputeContext ctx;
    BlockAlignment blocks(ctx, options, ref, options.sensorNoise);
    verify(blocks.estimate(alt), 1.0f);
#ifdef ESD4D_HAS_PRECISE_ALIGNMENT
    for (bool refine : {false, true}) {
        PreciseAlignment matcher(256, 224, ref.pixels, true, refine, true);
        verify(matcher.estimate(alt.pixels, 1, .0001f, .000001f, false), .65f);
    }
    options.tunables.alignerSelect = AlignerSelect::halide;
    options.tunables.enableAdaptiveNoise = false;
    auto shorter = alt;
    shorter.layerMultiplier = 1.5f;
    auto parallel = processBurst(options, {ref, alt, shorter});
    PreciseAlignment sequential(256, 224, ref.pixels, options.tunables.halideAccurateSubpixel, options.tunables.halideRefineFlow, true);
    std::vector<std::size_t> visited;
    options.motion = [&, &expectedPixels = ref.pixels](std::size_t index, const BurstFrame& reference,
                                                       const BurstFrame& frame) {
        require(reference.pixels == expectedPixels, "External matcher lost the reference frame");
        visited.push_back(index);
        return sequential.estimate(frame.pixels, 1 / frame.layerMultiplier,
                                   float(options.sensorNoise.s), float(options.sensorNoise.o), false);
    };
    auto serial = processBurst(options, {ref, alt, shorter});
    require(visited == std::vector<std::size_t>{2, 1} && serial.normalizedBayer == parallel.normalizedBayer &&
            serial.kernelMap == parallel.kernelMap, "Overlapped matching changed HDR order or output");
    options.motion = {};
    shorter.homography = std::array<float,9>{};
    try {
        processBurst(options, {ref, alt, shorter});
        throw std::runtime_error("Failed gyro match did not reach the caller");
    } catch (const std::invalid_argument&) {}
#endif
    std::array<float, 9> matrix{1, 0, 0, 0, 1, 0, 4, -2, 1};
    auto prewarp = prewarpBayer(options.sensor, alt.pixels, matrix);
    for (int y = 4; y < 220; ++y)
        for (int x = 4; x < 252; ++x)
            require(prewarp[y * 256 + x] == ref.pixels[y * 256 + x], "Gyro prewarp changed Bayer parity");
}

void testNoiseCalibration() {
    BurstOptions opts;
    opts.sensor = {640, 384, 0, {}, 4095};
    opts.sensorNoise = {.00015, .000004};
    std::vector<BurstFrame> frames(3);
    std::mt19937 rng(54);
    std::normal_distribution<float> normal;
    for (auto& frame : frames) {
        frame.iso = 800;
        frame.format = SampleFormat::NormalizedF16;
        for (int y = 0; y < opts.sensor.height; ++y)
            for (int x = 0; x < opts.sensor.width; ++x) {
                float mean = .015f + .30f * x / opts.sensor.width;
                float noisy =
                    mean + normal(rng) * std::sqrt(mean * opts.sensorNoise.s + opts.sensorNoise.o);
                frame.pixels.push_back(f32ToF16(std::clamp(noisy, 0.f, 1.f)));
            }
    }
    bool committed = false;
    opts.calibration.commit = [&](NoiseModel model) {
        committed = true;
        return std::optional<NoiseEstimate>{{model, true}};
    };
    ComputeContext ctx;
    auto result = calibrateNoise(ctx, opts, frames);
    require(result.measured && committed, "Identifiable noise model was not fitted");
    double fit = result.applied.s * .15 + result.applied.o,
           expected = opts.sensorNoise.s * .15 + opts.sensorNoise.o;
    require(std::abs(fit / expected - 1) < .15, "Incorrect calibrated sensor noise");
    opts.calibration.prior = NoiseEstimate{{.00023, .000008}, true};
    frames.front().iso = 0;
    committed = false;
    auto prior = calibrateNoise(ctx, opts, frames);
    require(!committed && !prior.measured && prior.applied.s == .00023,
            "Stored prior was lost on an ineligible burst");
}

void testWarp() {
    Sensor s{82, 70, 1, {}, 4095};
    auto g = geometryFor(s);
    auto [ref, alt] = translatedPair(s);
    auto source = packBayer(s, alt.pixels, alt.format, 1);
    auto base = packBayer(s, ref.pixels, ref.format, 1);
    PackedQuads kernels(std::size_t(g.kernelWidth) * g.kernelHeight * 2);
    for (std::size_t i = 0; i < kernels.size(); i += 2)
        kernels[i] = 0x3c003c00;
    for (auto mode : {Sampling::Floor, Sampling::Nearest, Sampling::Bilinear, Sampling::Cubic,
                      Sampling::Lanczos2, Sampling::Gyro, Sampling::FlowNet}) {
        Warp warp;
        warp.sampling = mode;
        if (mode == Sampling::FlowNet)
            warp.field = {2, -1, 0, 0};
        else if (mode == Sampling::Gyro)
            warp.homography = {1, 0, 0, 0, 1, 0, 4, -2, 1};
        else
            warp.field = {2.f / g.rawHalfWidth, -1.f / g.rawHalfHeight, 0, 0};
        for (bool robust : {false, true}) {
            RobustMerge merge(g, base, kernels, robust);
            merge.addFrame(source, warp, 0, 1, 1);
            auto out = unpackBayer(g, merge.result());
            for (int y = 12; y < s.height - 12; ++y)
                for (int x = 12; x < s.width - 12; ++x) {
                    auto i = y * s.width + x;
                    if (std::abs(halfValue(out[i]) - halfValue(ref.pixels[i])) >= .002f)
                        throw std::runtime_error("Translated warp " + std::to_string(int(mode)) +
                                                 " robust=" + std::to_string(robust) + " at " +
                                                 std::to_string(x) + "," + std::to_string(y) +
                                                 " produced " + std::to_string(halfValue(out[i])) +
                                                 " expected " + std::to_string(halfValue(ref.pixels[i])));
                }
        }
    }
}
} // namespace
int main() try {
    testKernelNet(); // Exercise first-use warmup before any test submits a burst.
    testTunables();
    testBayer();
    testHalfPacking();
    testStagingAndLuma();
    testPackedRaw();
    testHotPixels();
    testAlignment();
    testNoiseCalibration();
    testWarp();
    std::cout << "Bayer, KernelNet, defect correction, motion and warp checks passed\n";
} catch (const std::exception& e) {
    std::cerr << e.what() << '\n';
    return 1;
}
