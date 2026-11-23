//
// Created by eszdman on 19.11.2026.
//

#pragma once
#include "Burst.hpp"
namespace photon::esd4d {
class BlockAlignment {
  public:
    BlockAlignment(ComputeContext&, const BurstOptions&, const BurstFrame& reference, NoiseModel);
    ~BlockAlignment();
    Warp estimate(const BurstFrame&);

  private:
    struct State;
    std::unique_ptr<State> state_;
};
} // namespace photon::esd4d
