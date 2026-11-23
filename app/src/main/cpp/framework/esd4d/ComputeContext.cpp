//
// Created by eszdman on 19.11.2026.
//

#include "ComputeContext.hpp"
#include "Merge.hpp"
#include "Shader.hpp"
#include <kompute/Kompute.hpp>
#include <stdexcept>
#include <algorithm>
#include <initializer_list>

namespace photon::esd4d {
bool usesRowShaders(kp::Manager& gpu) {
    const auto p = gpu.getDeviceProperties();
#ifdef __ANDROID__
    return p.vendorID == 0x5143 && p.deviceID == 0x43050a01; // Adreno 740
#else
    return p.vendorID == 0x1002 && p.deviceID == 0x7550 && p.deviceType == vk::PhysicalDeviceType::eDiscreteGpu;
#endif
}

ImageWorkgroup imageWorkgroup(kp::Manager& gpu, std::string_view shaderName, unsigned height) {
    static constexpr std::initializer_list<std::string_view> rowShaders = {
#include "esd4d_row_shaders.inc"
    };
    if (std::find(rowShaders.begin(), rowShaders.end(), shaderName) != rowShaders.end() &&
        usesRowShaders(gpu) && height <= gpu.getDeviceProperties().limits.maxComputeWorkGroupCount[1])
        return {ESD4D_ROW_WIDTH, 1};
    return {};
}

std::array<std::uint32_t, 3> ImageWorkgroup::groups(unsigned width, unsigned height, unsigned depth) const {
    return {(width + x - 1) / x, (height + y - 1) / y, depth};
}

std::vector<std::uint32_t> ImageWorkgroup::shader(const std::string& name) const {
    return photon::esd4d::shader(y == 1 ? name + "_row" : name);
}

void ComputeContext::checkMergeLimits(const Geometry& g) const {
    if (g.rawHalfWidth <= 0 || g.rawHalfHeight <= 0 || g.kernelWidth <= 0 || g.kernelHeight <= 0 ||
        g.shiftX < 0 || g.shiftX > 1 || g.shiftY < 0 || g.shiftY > 1)
        throw std::invalid_argument("Invalid packed Bayer geometry");
    const auto limits = manager_->getDeviceProperties().limits;
    const auto width = std::uint64_t(g.rawHalfWidth) + g.shiftX;
    const auto height = std::uint64_t(g.rawHalfHeight) + g.shiftY;
    if (width * height > limits.maxStorageBufferRange / 8 ||
        std::uint64_t(g.kernelWidth) * g.kernelHeight > limits.maxStorageBufferRange / 8)
        throw std::length_error("Bayer plane exceeds Vulkan maxStorageBufferRange");
    if ((width + 7) / 8 > limits.maxComputeWorkGroupCount[0] ||
        (height + 7) / 8 > limits.maxComputeWorkGroupCount[1] ||
        limits.maxPerStageDescriptorStorageBuffers < 5 || limits.maxDescriptorSetStorageBuffers < 5)
        throw std::runtime_error("Merge dispatch exceeds Vulkan device limits");
}

ComputeContext::~ComputeContext() = default;
kp::Manager& ComputeContext::device() const { return *manager_; }
} // namespace photon::esd4d
