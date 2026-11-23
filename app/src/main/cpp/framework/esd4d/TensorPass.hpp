//
// Created by eszdman on 22.11.2026.
//

#pragma once
#include "ComputeContext.hpp"
#include "framework/kompute/ShaderProgram.hpp"
#include <kompute/Kompute.hpp>
#include <algorithm>
#include <array>
#include <cstddef>
#include <string_view>

namespace photon::esd4d {

template <class Shader> struct ShaderAsset { const char* name; };

template <class Shader, unsigned Slot, class Word, bool Writable>
struct TensorPort {
    using Owner = Shader;
    static constexpr unsigned slot = Slot;
    static constexpr bool writable = Writable;
    struct Bound {
        using Port = TensorPort;
        std::shared_ptr<kp::Memory> memory;
    };
    Bound operator()(const std::shared_ptr<kp::TensorT<Word>>& tensor) const { return {tensor}; }
};

// A cache belongs to one device and, for writable uniforms, one in-flight frame slot.
// The slot owner waits for its fence before updating its host-visible staging data.
class TensorPass {
    using Buffers = std::vector<std::shared_ptr<kp::Memory>>;
    std::shared_ptr<kp::Algorithm> algorithm_;
    std::string_view asset_;
    std::array<unsigned, 3> extent_{};
    bool exactGroups_ = false;

    struct Recorder {
        TensorPass& pass;
        kp::Manager& gpu;
        kp::Sequence& sequence;
        const char* asset;
        std::array<unsigned, 3> extent;
        bool exactGroups;

        template <class Shader, class Output, class... Inputs>
        void record(const typename Shader::Parameters& vars, const Output& output, const Inputs&... inputs) const {
            static_assert(std::is_same_v<typename Output::Port::Owner, Shader> && Output::Port::writable,
                          "writeTo requires a writable port from this shader");
            constexpr unsigned slots = (1u << Output::Port::slot) | (0u | ... | (1u << Inputs::Port::slot));
            static_assert(sizeof...(Inputs) + 1 == Shader::count && slots == (1u << Shader::count) - 1,
                          "Bind every tensor port exactly once");
            std::array<std::shared_ptr<kp::Memory>, Shader::count> bindings;
            ((bindings[Inputs::Port::slot] = inputs.memory), ...);
            bindings[Output::Port::slot] = output.memory;

            auto& algorithm = pass.algorithm_;
            if (!algorithm || pass.asset_ != asset || pass.extent_ != extent || pass.exactGroups_ != exactGroups ||
                !std::equal(bindings.begin(), bindings.end(), algorithm->getMemObjects().begin())) {
                const auto shape = exactGroups ? ImageWorkgroup{} : imageWorkgroup(gpu, asset, extent[1]);
                const auto groups = exactGroups ? extent : shape.groups(extent[0], extent[1], extent[2]);
                Buffers buffers(bindings.begin(), bindings.end());
                if constexpr (Shader::uniform) {
                    auto params = vars;
                    buffers.push_back(gpu.tensor(&params, 1, sizeof(params), kp::Memory::DataTypes::eCustom,
                                                 kp::Memory::MemoryTypes::eDevice, true));
                    algorithm = gpu.algorithm(buffers, shape.shader(asset), groups);
                } else {
                    algorithm = gpu.algorithm<float, typename Shader::Parameters>(
                        buffers, shape.shader(asset), groups, {}, {vars});
                }
                pass.asset_ = asset;
                pass.extent_ = extent;
                pass.exactGroups_ = exactGroups;
            } else if constexpr (Shader::uniform) {
                algorithm->getMemObjects().back()->setData(&vars, sizeof(vars));
            }
            if constexpr (Shader::uniform) {
                const auto& uniform = algorithm->getMemObjects().back();
                sequence.record<kp::OpSyncDevice>(Buffers{uniform});
                sequence.record<kp::OpAlgoDispatch>(algorithm);
            } else {
                sequence.record<kp::OpAlgoDispatch>(algorithm, std::vector<typename Shader::Parameters>{vars});
            }
        }
    };

  public:
    TensorPass() = default;
    TensorPass(const TensorPass&) = delete;
    TensorPass& operator=(const TensorPass&) = delete;

    template <class Shader>
    auto useProgram(kp::Manager& gpu, kp::Sequence& sequence, ShaderAsset<Shader> asset,
                    unsigned width, unsigned height) {
        return photon::gpu::ShaderProgram<Shader, Recorder>(
            Recorder{*this, gpu, sequence, asset.name, {width, height, 1}, false});
    }

    // The pyramid matcher assigns one whole workgroup to each search tile.
    template <class Shader>
    auto useWorkgroups(kp::Manager& gpu, kp::Sequence& sequence, ShaderAsset<Shader> asset,
                       std::array<unsigned, 3> groups) {
        return photon::gpu::ShaderProgram<Shader, Recorder>(Recorder{*this, gpu, sequence, asset.name, groups, true});
    }
};
} // namespace photon::esd4d
