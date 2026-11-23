//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include "Bayer.hpp"

namespace photon::esd4d {
// The precise Halide backend caches one Q3 pyramid for a burst. Available
// when ESD4D_HALIDE_ALIGNMENT is enabled.
class PreciseAlignment {
  public:
    PreciseAlignment(int width, int height, const std::vector<std::uint16_t>& normalizedReference,
                     bool accurate, bool refine, bool noiseAware);
    ~PreciseAlignment();
    Warp estimate(const std::vector<std::uint16_t>& normalizedFrame, float exposure, float noiseS,
                  float noiseO, bool coarseGlobal) const;

  private:
    struct State;
    std::unique_ptr<State> state_;
};
} // namespace photon::esd4d
