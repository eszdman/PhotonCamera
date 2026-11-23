//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include <array>
#include <cstdint>
#include <filesystem>
#include <string>
#include <string_view>
#include <vector>
#include <memory>

namespace kp {
class Manager;
}
namespace photon::esd4d {
struct Geometry;
struct ImageWorkgroup {
    unsigned x = 8, y = 8;
    std::array<std::uint32_t, 3> groups(unsigned width, unsigned height, unsigned depth = 1) const;
    std::vector<std::uint32_t> shader(const std::string& name) const;
};
ImageWorkgroup imageWorkgroup(kp::Manager& gpu, std::string_view shaderName, unsigned height);
bool usesRowShaders(kp::Manager& gpu);

// Share one Vulkan device and queue across the stages of a burst.
class ComputeContext {
  public:
    explicit ComputeContext(const std::filesystem::path& pipelineCacheDirectory = {});
    ~ComputeContext();
    void checkMergeLimits(const Geometry&) const;
    kp::Manager& device() const;

  private:
    std::unique_ptr<kp::Manager> manager_;
};
} // namespace photon::esd4d
