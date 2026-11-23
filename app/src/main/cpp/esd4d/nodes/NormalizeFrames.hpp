//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include "BurstNode.hpp"

namespace photon::esd4d {

class NormalizeFrames final : public BurstNode {
  public:
    void run(BurstState& state) override;
};

} // namespace photon::esd4d
