//
// Created by eszdman on 22.11.2026.
//

#include "PostState.hpp"

namespace photon::post {
void PostState::upload(const Resources& buffers) const {
    sequence.record<kp::OpSyncDevice>(buffers);
    constexpr auto readWrite =
        static_cast<vk::AccessFlagBits>(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
    sequence.record<kp::OpMemoryBarrier>(buffers, vk::AccessFlagBits::eTransferWrite, readWrite,
                                         vk::PipelineStageFlagBits::eTransfer,
                                         vk::PipelineStageFlagBits::eComputeShader);
}

void PostState::download(const Resources& buffers) {
    sequence.record<kp::OpMemoryBarrier>(
        buffers, vk::AccessFlagBits::eShaderWrite, vk::AccessFlagBits::eTransferRead,
        vk::PipelineStageFlagBits::eComputeShader, vk::PipelineStageFlagBits::eTransfer);
    sequence.record<kp::OpSyncLocal>(buffers)->eval();
    sequence.clear();
    passes.clear();
}

} // namespace photon::post
