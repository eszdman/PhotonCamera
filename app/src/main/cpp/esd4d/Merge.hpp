//
// Created by eszdman on 19.11.2026.
//

#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <vector>

namespace photon::esd4d {

// Two uint32 words per quad. Each word holds two IEEE binary16 values,
// low lane first, in ESD4D's packed CFA order.
using PackedQuads = std::vector<std::uint32_t>;

struct Geometry {
    std::int32_t rawHalfWidth, rawHalfHeight;
    std::int32_t shiftX, shiftY;
    std::int32_t kernelWidth, kernelHeight;
};

enum class Sampling { Floor, Nearest, Bilinear, Cubic, Lanczos2, Gyro, FlowNet };

// Per-frame field, in the layout exported by ESD4D's aligners. Atlas vectors
// store integer displacement / rawHalf in xy and fractional displacement in zw.
// FlowNet supplies dense xy displacement directly in packed-pixel units.
struct Warp {
    Sampling sampling = Sampling::Floor;
    int fieldWidth = 1, fieldHeight = 1, tile = 16;
    std::vector<float> field{0, 0, 0, 0};
    bool interpolateMotion = false;
    float residualStrength = 0, sensorNoiseS = 0, sensorNoiseO = 0;
    std::array<float, 9> homography{1, 0, 0, 0, 1, 0, 0, 0, 1}; // column major
};

// One burst. Both the reference and aligned inputs are in packed coordinates,
// with dimensions rawHalf + shift. The KernelNet map stays fixed for the burst.
class ComputeContext;
struct Sensor;
enum class SampleFormat;
class RobustMerge {
  public:
    RobustMerge(Geometry geometry, const PackedQuads& reference, const PackedQuads& kernelMap,
                bool residualRobustness = true);
    RobustMerge(std::shared_ptr<ComputeContext>, Geometry, const PackedQuads& reference,
                const PackedQuads& kernelMap, bool residualRobustness);
    ~RobustMerge();
    void addAligned(const PackedQuads& frame, float noiseS, float noiseO, float exposure);
    void addFrame(const PackedQuads& source, const Warp& warp, float noiseS, float noiseO,
                  float exposure);
    // Queues inputs in order. A full two-frame queue waits for its oldest slot.
    // The caller can release the input on return. result() drains both slots.
    void submitFrame(const PackedQuads& source, const Warp& warp, float noiseS, float noiseO,
                     float exposure);
    void submitBayer(const Sensor&, const std::vector<std::uint16_t>&, SampleFormat, float referenceExposure,
                     const Warp&, float noiseS, float noiseO, float exposure);
    PackedQuads result() const;

  private:
    struct State;
    std::unique_ptr<State> state_;
};

} // namespace photon::esd4d
