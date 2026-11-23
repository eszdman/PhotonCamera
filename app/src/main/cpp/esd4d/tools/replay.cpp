//
// Created by eszdman on 19.11.2026.
//

#include "Merge.hpp"

#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {
photon::esd4d::PackedQuads readQuads(const char* path) {
    std::ifstream input(path, std::ios::binary | std::ios::ate);
    auto bytes = input.tellg();
    if (bytes <= 0 || bytes % 8 != 0)
        throw std::runtime_error(std::string("Missing or invalid packed quad file: ") + path);
    photon::esd4d::PackedQuads words(static_cast<std::size_t>(bytes) / 4);
    input.seekg(0);
    if (!input.read(reinterpret_cast<char*>(words.data()), bytes))
        throw std::runtime_error(std::string("Incomplete input: ") + path);
    return words;
}
} // namespace

int main(int argc, char** argv) try {
    if (argc < 14 || (argc - 10) % 4) {
        std::cerr << "Usage: esd4d_replay halfWidth halfHeight shiftX shiftY mapWidth mapHeight"
                     " reference.f16 kernels.f16 output.f16"
                     " aligned.f16 noiseS noiseO exposure [aligned.f16 noiseS noiseO exposure ...]\n";
        return 2;
    }
    photon::esd4d::Geometry geometry{std::stoi(argv[1]), std::stoi(argv[2]), std::stoi(argv[3]),
                                     std::stoi(argv[4]), std::stoi(argv[5]), std::stoi(argv[6])};
    photon::esd4d::RobustMerge burst(geometry, readQuads(argv[7]), readQuads(argv[8]));
    for (int i = 10; i < argc; i += 4)
        burst.addAligned(readQuads(argv[i]), std::stof(argv[i + 1]), std::stof(argv[i + 2]),
                         std::stof(argv[i + 3]));
    auto result = burst.result();
    std::ofstream output;
    output.exceptions(std::ios::failbit | std::ios::badbit);
    output.open(argv[9], std::ios::binary);
    output.write(reinterpret_cast<const char*>(result.data()), result.size() * sizeof(result[0]));
    output.close();
} catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
}
