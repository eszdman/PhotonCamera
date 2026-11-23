//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include "ComputeContext.hpp"
#include <kompute/Kompute.hpp>
#include <type_traits>

namespace photon::esd4d {

// Bind buffers in shader binding order. The caller records the dispatch and barriers.
template <typename Parameters>
std::shared_ptr<kp::Algorithm> makeImagePass(kp::Manager& gpu, const std::string& shaderName,
                                             const std::vector<std::shared_ptr<kp::Memory>>& buffers,
                                             unsigned width, unsigned height, const Parameters& parameters) {
    static_assert(std::is_trivially_copyable_v<Parameters>, "Push constants must be trivially copyable");
    const auto shape = imageWorkgroup(gpu, shaderName, height);
    return gpu.algorithm<float, Parameters>(buffers, shape.shader(shaderName), shape.groups(width, height),
                                            {}, {parameters});
}

} // namespace photon::esd4d
