//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include "Merge.hpp"

namespace photon::esd4d {
enum class SampleFormat { SensorU16, NormalizedF16 };
struct Sensor {
    int width, height, cfa;
    std::array<float, 4> black;
    float white;
};
float halfValue(std::uint16_t bits);
void normalizeBayer(const Sensor&, std::vector<std::uint16_t>&, SampleFormat);
// PhotonCamera's contiguous LSB-first burst bitstream (1 through 16 bits per site).
std::vector<std::uint16_t> unpackSensorBits(const Sensor&, const std::uint8_t* data, std::size_t bytes,
                                            unsigned bits);
Geometry geometryFor(const Sensor& sensor);
PackedQuads packBayer(const Sensor&, const std::vector<std::uint16_t>& pixels, SampleFormat,
                      float referenceExposure);
void packBayerInto(const Sensor&, const std::vector<std::uint16_t>& pixels, SampleFormat,
                   float referenceExposure, std::uint32_t* destination, std::size_t words);
std::vector<std::uint16_t> unpackBayer(const Geometry&, const PackedQuads&);
std::vector<std::uint16_t> prewarpBayer(const Sensor&, const std::vector<std::uint16_t>& normalized,
                                        const std::array<float, 9>& homography);
inline int kernelLumaWidth(const Geometry& g) { return (g.rawHalfWidth + g.shiftX + 3) / 4 * 4; }
std::vector<float> kernelLuma(const Geometry&, const PackedQuads&);
} // namespace photon::esd4d
