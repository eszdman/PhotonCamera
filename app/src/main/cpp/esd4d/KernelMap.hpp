//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include "Merge.hpp"
namespace photon::esd4d {
// Optional prefetch for the first capture. Returns true if it ran an extraction.
bool warmKernelMap(const Geometry& geometry);
PackedQuads inferKernelMap(ComputeContext&, const std::vector<float>& brightness, int width, int height,
                           float sigma);
}
