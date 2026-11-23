//
// Created by eszdman on 21.11.2026.
//

#pragma once
#include "framework/post/PostState.hpp"

namespace photon::post {
// R, G, B bins stored consecutively; sampled at the GLES scale-3 lattice.
std::vector<uint32_t> rgbHistogram(PostState&, int bins, std::array<float, 3> exposure, float offset = 0);
} // namespace photon::post
