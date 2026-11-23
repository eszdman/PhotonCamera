//
// Created by eszdman on 21.11.2026.
//

#include "PostPipeline.hpp"
#include <chrono>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <sstream>

using namespace photon::post;
namespace {
template <class T> std::vector<T> readImage(const char* path, size_t count) {
    std::ifstream in(path, std::ios::binary | std::ios::ate);
    if (!in || in.tellg() != std::streamoff(count * sizeof(T)))
        throw std::invalid_argument("Input byte count does not match image");
    std::vector<T> pixels(count);
    in.seekg(0);
    if (!in.read(reinterpret_cast<char*>(pixels.data()), count * sizeof(T)))
        throw std::runtime_error("Failed to read image");
    return pixels;
}
} // namespace
int main(int argc, char** argv) try {
    if (argc < 7)
        throw std::invalid_argument(
            "post_replay sdr|gainmap WIDTH HEIGHT TILE merged.f16 out.rgba8 [noiseS noiseO] [--tone "
            "curve|sky|new|off] [--rotation degrees] [--matrix csv] [--pipeline-cache dir]\n"
            "post_replay amaze|mono|quad|legacy WIDTH HEIGHT TILE calibrated.f32 out.rgba16f [noiseS noiseO] [--pipeline-cache dir]");
    const std::string mode = argv[1];
    Size size{std::stoi(argv[2]), std::stoi(argv[3])};
    if (size.x <= 0 || size.y <= 0)
        throw std::invalid_argument("Invalid image dimensions");
    const auto count = std::size_t(size.x) * size.y;
    PostPipelineSettings settings;
    auto& linear = settings.linear;
    linear.tileSize = std::stoi(argv[4]);
    linear.denoise = mode == "sdr" || mode == "gainmap";
    linear.noiseS = .0001f;
    linear.noiseO = .000001f;
    int arg = 7;
    if (argc >= 9 && std::string(argv[arg]).rfind("--", 0) != 0) {
        linear.denoise = true;
        linear.noiseS = std::stof(argv[arg++]);
        linear.noiseO = std::stof(argv[arg++]);
    }
    std::filesystem::path pipelineCache;
    PostPipelineSettings::Preferences preferences;
    while (arg < argc) {
        std::string option = argv[arg++];
        if (arg == argc)
            throw std::invalid_argument("Missing value for " + option);
        std::string value = argv[arg++];
        if (option == "--tone")
            preferences["pref_tunable_postpipeline_tonepipeline"] = value;
        else if (option == "--pipeline-cache")
            pipelineCache = value;
        else if (option == "--rotation")
            settings.display.rotationDegrees = std::stoi(value);
        else if (option == "--matrix") {
            std::istringstream input(value);
            for (auto& element : settings.tone.intermediateToSRGB) {
                std::string field;
                if (!std::getline(input, field, ','))
                    throw std::invalid_argument("Expected nine matrix coefficients");
                element = std::stof(field);
            }
            if (input.peek() != EOF)
                throw std::invalid_argument("Extra matrix coefficients");
        } else
            throw std::invalid_argument("Unknown option: " + option);
    }
    settings.loadTunables(preferences, 3, 1);
    std::vector<uint32_t> pixels;
    if (mode == "sdr" || mode == "gainmap") {
        // Deterministic camera parameters shared with PostPipelineReplayTest.
        settings.bayer.cfaPattern = 3;
        linear.whitePoint = {.51f, 1.f, .72f};
        SdrImage gain;
        auto input = readImage<uint16_t>(argv[5], count);
        using Clock = std::chrono::steady_clock;
        const auto start = Clock::now();
        photon::esd4d::ComputeContext context(pipelineCache);
        const auto ready = Clock::now();
        auto base = renderPostPipeline(context, size, input, settings,
                                       mode == "gainmap" ? &gain : nullptr);
        const auto done = Clock::now();
        using Milliseconds = std::chrono::duration<double, std::milli>;
        std::cout << "post_context_ms=" << Milliseconds(ready - start).count()
                  << " post_render_ms=" << Milliseconds(done - ready).count()
                  << " post_total_ms=" << Milliseconds(done - start).count() << '\n';
        pixels = mode == "gainmap" ? std::move(gain.pixels) : std::move(base.pixels);
    } else {
        linear.blackLevels.enable = false;
        if (mode == "mono")
            linear.demosaicing = Demosaicing::monochrome;
        else if (mode == "quad")
            linear.demosaicing = Demosaicing::quad;
        else if (mode == "legacy")
            linear.demosaicing = Demosaicing::legacy;
        else if (mode != "amaze")
            throw std::invalid_argument("Unknown mode: " + mode);
        photon::esd4d::ComputeContext context(pipelineCache);
        pixels = renderLinear(context, size, readImage<float>(argv[5], count), linear);
    }
    std::ofstream out(argv[6], std::ios::binary);
    out.write(reinterpret_cast<const char*>(pixels.data()), pixels.size() * sizeof(uint32_t));
    out.close();
    if (!out)
        throw std::runtime_error("Failed to write image");
    return 0;
} catch (const std::exception& e) {
    std::cerr << e.what() << '\n';
    return 1;
}
