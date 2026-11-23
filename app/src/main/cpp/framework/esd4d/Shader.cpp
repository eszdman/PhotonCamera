//
// Created by eszdman on 19.11.2026.
//

#include "Shader.hpp"
#include <cmrc/cmrc.hpp>
#include <cstring>
CMRC_DECLARE(esd4d);
namespace photon::esd4d {
std::vector<std::uint32_t> shader(const std::string& name) {
    const auto file = cmrc::esd4d::get_filesystem().open(name + ".spv");
    std::vector<std::uint32_t> words(file.size() / sizeof(std::uint32_t));
    std::memcpy(words.data(), file.begin(), file.size());
    return words;
}

} // namespace photon::esd4d
