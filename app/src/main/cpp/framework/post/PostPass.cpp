//
// Created by eszdman on 21.11.2026.
//

#include "PostPass.hpp"
#include <cmrc/cmrc.hpp>
#include <cstring>
CMRC_DECLARE(post);

namespace photon::post {
std::vector<uint32_t> shaderWords(const char* shader) {
    const auto file = cmrc::post::get_filesystem().open(std::string(shader) + ".spv");
    std::vector<uint32_t> words(file.size() / sizeof(uint32_t));
    std::memcpy(words.data(), file.begin(), file.size());
    return words;
}

void TilePasses::prepare(const char* shader, Size extent, Resources buffers) {
    const auto groups = esd4d::ImageWorkgroup{}.groups(extent.x, extent.y);
    auto algorithm = gpu_.algorithm<float, Tile>(buffers, shaderWords(shader), groups, {}, {Tile{}});
    passes_.push_back({std::move(buffers), std::move(algorithm)});
}

void computeBarrier(kp::Sequence& sequence, const Resources& resources) {
    constexpr auto compute = vk::PipelineStageFlagBits::eComputeShader;
    constexpr auto readWrite =
        static_cast<vk::AccessFlagBits>(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
    sequence.record<kp::OpMemoryBarrier>(resources, readWrite, readWrite, compute, compute);
}

void TilePasses::record(kp::Sequence& sequence, const Tile& tile) const {
    for (const Pass& pass : passes_) {
        // Also orders the previous tile's readers before scratch is overwritten.
        computeBarrier(sequence, pass.buffers);
        sequence.record<kp::OpAlgoDispatch>(pass.algorithm, std::vector<Tile>{tile});
    }
}
} // namespace photon::post
