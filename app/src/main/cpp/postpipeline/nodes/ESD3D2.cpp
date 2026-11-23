//
// Created by eszdman on 21.11.2026.
//

#ifndef PHOTON_POST_ESD3D2
#define PHOTON_POST_ESD3D2
#include "framework/tuning/Annotations.hpp"

namespace photon::post {
struct PostState;
struct LinearSettings;

struct ESD3D2 {
    enum class Version { steered TUNABLE_LABEL("Steered"), steered_prod TUNABLE_LABEL("Steered Prod") };

    TUNABLE("Denoise", "Enable", "Enable ESD3D Denoising")
    bool enable = true;

    TUNABLE("Denoise", "Noise To Kernel Size", 0, 50, 0.01)
    float noiseToKernelSize = 24.f;

    TUNABLE("Denoise", "Noise Target", "Target noise level to map to minimum kernel size (1/256 = 0.00390625)",
            0, 0.1, 0.0001)
    float noiseTarget = 1.f / 256.f;

    TUNABLE("Denoise", "Luma", "Luma strength multiplier for denoising", 0, 2, 0.01)
    float luma = 0.8f;

    TUNABLE("Denoise", "Max Kernel", "Maximum kernel size for denoising", 1, 51, 1)
    int maxSize = 21;

    TUNABLE("Denoise", "Min Kernel", "Minimum kernel size for denoising", 1, 21, 1)
    int minSize = 7;

    TUNABLE("Denoise", "Moire Reduction", "Moire reduction strength", 0, 5, 0.1)
    float moire = 1.5f;

    TUNABLE("Denoise", "Tone-Aware Boost",
            "Scale denoise sigma by the tonemap's shadow gain (sqrt brightness ratio as variance), so shadows are filtered to their post-tonemap visibility")
    bool toneAwareBoost = true;

    TUNABLE("Denoise", "Tone-Aware Strength", "Strength of the tone-aware shadow sigma boost", 0, 3, 0.05)
    float toneAwareStrength = 1.f;

    TUNABLE("Denoise", "Use Color Denoising", "Whether to apply subsampling denoising to color channels (in addition to luma)")
    bool useColorDenoising = true;

    TUNABLE("Denoise", "ESD3D Version",
            "Steered Prod adds consensus weights (stricter edge preservation) on top of tensor steering")
    Version version = Version::steered;

    bool enabledFor(const LinearSettings&) const;
    void validate() const;
    void run(PostState&) const;

  private:
    template <class Shader> void process(PostState&) const;
    template <class Shader> auto denoiseProgram(PostState&, float noiseScale) const;
};
} // namespace photon::post

#endif // PHOTON_POST_ESD3D2

#ifndef PHOTON_DISCOVER_TUNABLES
#include "framework/post/PostState.hpp"
#include "convdiff.hpp"
#include "esd_gaussdown.hpp"
#include "esd3d2_steered.hpp"
#include "esd3d2_steered_prod.hpp"
#include "guided1.hpp"
#include "guided2.hpp"
#include "guided3.hpp"
#include "guided4.hpp"
#include <algorithm>
#include <cmath>

namespace photon::post {
using namespace shaders;

bool ESD3D2::enabledFor(const LinearSettings& settings) const {
    return settings.denoise && enable &&
           (settings.demosaicing == Demosaicing::amaze || settings.demosaicing == Demosaicing::legacy ||
            settings.demosaicing == Demosaicing::mergedRgb);
}

void ESD3D2::validate() const {
    if (!(noiseTarget > 0) || !std::isfinite(noiseToKernelSize) || noiseToKernelSize < 0 ||
        minSize < 1 || maxSize < minSize || maxSize > 51)
        throw std::invalid_argument("Invalid ESD3D2 kernel settings");
}

namespace {
Plane<Format::rg16f> gradient(PostState& state, Rgba input) {
    auto result = state.rg(input.size);
    auto program = state.passes.useProgram<convdiff>();
    program.bindInputs(convdiff::InputBuffer(input)).writeTo(result);
    return result;
}

Rgba downsample(PostState& state, Rgba input, int scale) {
    if (scale == 1) return input;
    Size size{input.size.x / scale, input.size.y / scale};
    if (size.x < 2 || size.y < 2) throw std::invalid_argument("ESD3D2 pyramid is too small");
    auto low = state.rgba(size);
    auto program = state.passes.useProgram<esd_gaussdown>();
    program.setVar(esd_gaussdown::SCALE, scale);
    program.bindInputs(esd_gaussdown::InputBuffer(input)).writeTo(low);
    return low;
}

template <class Shader>
void guidedUpsample(PostState& state, Rgba low, Rgba guide, Rgba output) {
    auto program = state.passes.useProgram<Shader>();
    program.bindInputs(Shader::LowresInput(low), Shader::GuideHigh(guide)).writeTo(output);
}

Rgba upsampleColor(PostState& state, Rgba low, Rgba guide, int scale) {
    auto output = state.rgba(guide.size);
    // SCALE is compiled into each guided-upsample SPIR-V variant.
    switch (scale) {
    case 1: guidedUpsample<guided1>(state, low, guide, output); break;
    case 2: guidedUpsample<guided2>(state, low, guide, output); break;
    case 3: guidedUpsample<guided3>(state, low, guide, output); break;
    case 4: guidedUpsample<guided4>(state, low, guide, output); break;
    }
    return output;
}
} // namespace

template <class Shader>
auto ESD3D2::denoiseProgram(PostState& state, float noiseScale) const {
    const float noiseS = state.settings.noiseS / noiseScale;
    const float noiseO = state.settings.noiseO / noiseScale;
    const double ratio = std::max(double((noiseS + noiseO) / noiseTarget), 1e-7);
    const double kernel = 1.0 + std::sqrt(ratio) * noiseToKernelSize;
    const int size = kernel >= maxSize ? maxSize : std::min(minSize + int(kernel) - int(kernel) % 2, maxSize);

    auto program = state.passes.useProgram<Shader>();
    program.setVar(Shader::NOISES, noiseS);
    program.setVar(Shader::NOISEO, noiseO);
    program.setVar(Shader::KERNELSIZE, float(kernel));
    program.setVar(Shader::kernelRadius, (size - 1) / 2);
    program.setVar(Shader::LUMA, luma);
    program.setVar(Shader::TONESTRENGTH, toneAwareBoost ? toneAwareStrength : 0.f);
    return program;
}

template <class Shader>
void ESD3D2::process(PostState& state) const {
    auto input = state.rgb;
    if (useColorDenoising) {
        const float noise = float(std::sqrt(.5 * state.settings.noiseS + state.settings.noiseO));
        const float scaling = std::clamp(noise / noiseTarget, 1.f, 4.f);
        const int scale = int(scaling + .5f);
        auto low = downsample(state, input, scale);
        auto filtered = state.rgba(low.size);
        auto grad = gradient(state, low);

        auto program = denoiseProgram<Shader>(state, scale == 1 ? 1.f : scaling * .75f);
        program.setVar(Shader::MOIRE, 0.f);
        program.bindInputs(Shader::InputBuffer(low), Shader::GradBuffer(grad)).writeTo(filtered);
        input = upsampleColor(state, filtered, input, scale);
    }

    auto output = state.nextRgb();
    auto grad = gradient(state, input);
    auto program = denoiseProgram<Shader>(state, 1.f);
    program.setVar(Shader::MOIRE, moire);
    program.bindInputs(Shader::InputBuffer(input), Shader::GradBuffer(grad)).writeTo(output);
    state.rgb = output;
}

void ESD3D2::run(PostState& state) const {
    if (version == Version::steered_prod)
        process<esd3d2_steered_prod>(state);
    else
        process<esd3d2_steered>(state);
}
} // namespace photon::post
#endif // PHOTON_DISCOVER_TUNABLES
