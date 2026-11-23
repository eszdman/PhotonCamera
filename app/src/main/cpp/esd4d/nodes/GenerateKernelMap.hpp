//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include "BurstNode.hpp"
#include "framework/tuning/Annotations.hpp"

namespace photon::esd4d {

class GenerateKernelMap final : public BurstNode {
  public:
    struct Settings {
        TUNABLE("Merge", "Network merge noise multiplier",
                "Scales the noise model fed to the kernel network", 0.1, 20.0, 0.05)
        float noiseMpy = 1.0f;
    };

    void run(BurstState& state) override;
};

} // namespace photon::esd4d
