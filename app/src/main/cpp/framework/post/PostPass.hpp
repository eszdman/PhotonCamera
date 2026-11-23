//
// Created by eszdman on 21.11.2026.
//

#pragma once
#include "PostPipeline.hpp"
#include "PostFormat.hpp"
#include "framework/kompute/ShaderProgram.hpp"
#include <kompute/Kompute.hpp>
#include <algorithm>
#include <array>
#include <type_traits>
#include <tuple>
#include <utility>
#include <stdexcept>

namespace photon::post {
template <Format F> struct Plane {
    std::shared_ptr<kp::Tensor> tensor;
    Size size{};
};

template <class Shader, unsigned Slot, Format F> struct ImageBinding {
    using Owner = Shader;
    static constexpr unsigned slot = Slot;
    struct Bound {
        using Port = ImageBinding;
        std::shared_ptr<kp::Tensor> tensor;
        Size size;
    };
    Bound operator()(const Plane<F>& image) const { return {image.tensor, image.size}; }
};

struct Tile {
    Size windowSize, imageSize, tileOrigin, tileSize, interior;
};
static_assert(sizeof(Tile) == 40);

using Resources = std::vector<std::shared_ptr<kp::Memory>>;
std::vector<uint32_t> shaderWords(const char* name);
void computeBarrier(kp::Sequence& sequence, const Resources& resources);

template <class First, class... Rest> Resources bindImages(First first, Rest... rest) {
    using Shader = typename First::Port::Owner;
    static_assert((std::is_same_v<Shader, typename Rest::Port::Owner> && ...),
                  "Bindings belong to different shaders");
    constexpr unsigned slots = (1U << First::Port::slot) | (0U | ... | (1U << Rest::Port::slot));
    static_assert(1 + sizeof...(Rest) == Shader::count && slots == (1U << Shader::count) - 1,
                  "Bind every shader input and output exactly once");
    Resources buffers(Shader::count);
    buffers[First::Port::slot] = first.tensor;
    ((buffers[Rest::Port::slot] = rest.tensor), ...);
    return buffers;
}

class FramePasses {
  public:
    FramePasses(kp::Manager& gpu, kp::Sequence& sequence) : gpu_(gpu), sequence_(sequence) {}

    struct Recorder {
        FramePasses& passes;
        template <class Shader, class Output, class... Inputs>
        void record(const typename Shader::Parameters& vars, const Output& output, const Inputs&... inputs) const {
            passes.dispatch(vars, inputs..., Shader::Output(output));
        }
    };
    template <class Shader> auto useProgram() { return gpu::ShaderProgram<Shader, Recorder>(Recorder{*this}); }

    template <class First, class... Rest>
    void dispatch(typename First::Port::Owner::Parameters params, First first, Rest... rest) {
        Size extent{};
        auto setExtent = [&](const auto& binding) {
            using Port = typename std::decay_t<decltype(binding)>::Port;
            if constexpr (Port::slot + 1 == Port::Owner::count) extent = binding.size;
        };
        setExtent(first);
        (setExtent(rest), ...);
        compute(extent, params, first, rest...);
    }

    template <class First, class... Rest>
    void compute(Size extent, typename First::Port::Owner::Parameters params, First first, Rest... rest) {
        using Shader = typename First::Port::Owner;
        auto buffers = bindImages(first, rest...);
        params.sizes[First::Port::slot] = first.size;
        ((params.sizes[Rest::Port::slot] = rest.size), ...);
        auto groups = esd4d::ImageWorkgroup{Shader::localX, Shader::localY}.groups(extent.x, extent.y);
        std::shared_ptr<kp::Algorithm> pass;
        if constexpr (Shader::parameterBuffer) {
            auto uniform = gpu_.tensor(&params, 1, sizeof(params), kp::Memory::DataTypes::eCustom,
                                       kp::Memory::MemoryTypes::eHost);
            buffers.push_back(uniform);
            pass = gpu_.algorithm(buffers, shaderWords(Shader::asset), groups);
            computeBarrier(sequence_, buffers);
            sequence_.record<kp::OpAlgoDispatch>(pass);
        } else {
            // Iterations and equal-sized bands reuse the same pipeline and descriptors.
            auto match = std::find_if(cache_.begin(), cache_.end(), [&](const Prepared& e) {
                return e.shader == Shader::asset && e.groups == groups && e.buffers == buffers;
            });
            if (match != cache_.end()) {
                pass = match->algorithm;
            } else {
                pass = gpu_.algorithm<float, decltype(params)>(buffers, shaderWords(Shader::asset), groups, {},
                                                               {params});
                cache_.push_back({Shader::asset, groups, buffers, pass});
            }
            computeBarrier(sequence_, buffers);
            sequence_.record<kp::OpAlgoDispatch>(pass, std::vector<typename Shader::Parameters>{params});
        }
    }
    void clear() { cache_.clear(); }

  private:
    struct Prepared {
        const char* shader;
        std::array<uint32_t, 3> groups;
        Resources buffers;
        std::shared_ptr<kp::Algorithm> algorithm;
    };
    std::vector<Prepared> cache_;
    kp::Manager& gpu_;
    kp::Sequence& sequence_;
};

class TilePasses {
  public:
    explicit TilePasses(kp::Manager& gpu) : gpu_(gpu) {}

    template <class First, class... Rest> void dispatch(Size extent, First first, Rest... rest) {
        using Shader = typename First::Port::Owner;
        auto buffers = bindImages(first, rest...);
        prepare(Shader::asset, extent, std::move(buffers));
    }
    void record(kp::Sequence& sequence, const Tile& tile) const;

  private:
    struct Pass {
        Resources buffers;
        std::shared_ptr<kp::Algorithm> algorithm;
    };
    void prepare(const char* shader, Size extent, Resources buffers);
    kp::Manager& gpu_;
    std::vector<Pass> passes_;
};
} // namespace photon::post
