//
// Created by eszdman on 19.11.2026.
//

#include "Burst.hpp"
#include "Tunables.hpp"
#include "framework/esd4d/PipelineCache.hpp"
#ifdef ESD4D_HAS_FLOWNET
#include "FlowNet.hpp"
#endif
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <stdexcept>

// Plain text manifest + raw binary planes; independent of camera SDKs.
int main(int argc, char** argv) try {
    if (argc == 3 && std::string(argv[1]) == "--prepare-pipelines") {
        auto count = photon::esd4d::preparePipelines(argv[2]);
        std::cout << "Prepared " << count << " pipelines\n";
        return 0;
    }
    if (argc < 3) {
        std::cerr << "Usage: esd4d_burst manifest.txt merged.f16 [--tunables file] [--fixed-noise] [--merge-strength stops] [--pipeline-cache dir]\n"
                     "       esd4d_burst --prepare-pipelines dir\n";
        return 2;
    }
    std::ifstream manifest(argv[1]);
    photon::esd4d::BurstOptions options{};
    auto& s = options.sensor;
    std::string motion, flowParams, flowWeights;
    auto directory = std::filesystem::path(argv[1]).parent_path();
    if (!(manifest >> s.width >> s.height >> s.cfa >> s.white >> s.black[0] >> s.black[1] >> s.black[2] >>
          s.black[3] >> options.sensorNoise.s >> options.sensorNoise.o >> motion))
        throw std::runtime_error("Invalid burst manifest header");
    photon::esd4d::geometryFor(s);
    using photon::esd4d::AlignerSelect;
    if (motion == "0" || motion == "off")
        options.tunables.alignerSelect = AlignerSelect::off;
    else if (motion == "1" || motion == "halide")
        options.tunables.alignerSelect = AlignerSelect::halide;
    else if (motion == "lk")
        options.tunables.halideRefineFlow = true;
    else if (motion == "pyramid")
        options.tunables.alignerSelect = AlignerSelect::gl;
    else if (motion == "flownet") {
        options.tunables.alignerSelect = AlignerSelect::flownet;
        if (!(manifest >> std::quoted(flowParams) >> std::quoted(flowWeights)))
            throw std::runtime_error("FlowNet needs parameter and weight file paths");
    } else
        throw std::runtime_error("Unknown or unavailable matcher: " + motion);
    bool fixedNoise = false;
    for (int i = 3; i < argc; ++i) {
        std::string flag = argv[i];
        if (flag == "--fixed-noise") fixedNoise = true;
        else if (flag == "--tunables" && i + 1 < argc) {
            std::ifstream settings(argv[++i]);
            if (!settings) throw std::runtime_error("Cannot open tunables file: " + std::string(argv[i]));
            photon::tuning::inject(options.tunables, settings);
        } else if (flag == "--pipeline-cache" && i + 1 < argc) options.pipelineCacheDirectory = argv[++i];
        else if (flag == "--merge-strength" && i + 1 < argc) options.mergeStrength = std::stof(argv[++i]);
        else throw std::runtime_error("Invalid option: " + flag);
    }
    if (fixedNoise) options.tunables.enableAdaptiveNoise = false;
    if (options.tunables.alignerSelect == AlignerSelect::flownet && !options.tunables.gyroOnly) {
#ifdef ESD4D_HAS_FLOWNET
        if (flowParams.empty() || flowWeights.empty())
            throw std::runtime_error("Selecting FlowNet requires model paths in the manifest");
        options.motion = photon::esd4d::makeFlowNetMotion(s, (directory / flowParams).string(),
                                                          (directory / flowWeights).string());
#else
        throw std::runtime_error("FlowNet is unavailable in this build");
#endif
    }
    std::vector<photon::esd4d::BurstFrame> frames;
    std::string path;
    while (manifest >> std::quoted(path)) {
        photon::esd4d::BurstFrame frame;
        std::string format;
        if (!(manifest >> format >> frame.layerMultiplier >> frame.iso) ||
            (format != "f16" && format != "u16"))
            throw std::runtime_error("Invalid frame entry: " + path);
        frame.format = format == "u16" ? photon::esd4d::SampleFormat::SensorU16
                                       : photon::esd4d::SampleFormat::NormalizedF16;
        std::ifstream in(directory / path, std::ios::binary | std::ios::ate);
        auto samples = std::size_t(s.width) * s.height;
        if (in.tellg() != std::streamoff(samples * 2))
            throw std::runtime_error("Invalid plane size: " + path);
        frame.pixels.resize(samples);
        in.seekg(0);
        if (!in.read(reinterpret_cast<char*>(frame.pixels.data()), samples * 2))
            throw std::runtime_error("Read failed: " + path);
        frames.push_back(std::move(frame));
    }
    auto result = photon::esd4d::processBurst(options, std::move(frames));
    std::ofstream out;
    out.exceptions(std::ios::badbit | std::ios::failbit);
    out.open(argv[2], std::ios::binary);
    out.write(reinterpret_cast<const char*>(result.normalizedBayer.data()),
              result.normalizedBayer.size() * 2);
    out.close();
    std::cout << "Noise S=" << result.appliedNoise.s << " O=" << result.appliedNoise.o << "\n";
} catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
}
