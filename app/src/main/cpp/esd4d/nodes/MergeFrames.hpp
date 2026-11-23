//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include "BurstNode.hpp"
#include "framework/tuning/Annotations.hpp"

namespace photon::esd4d {

class MergeFrames final : public BurstNode {
  public:
    struct Settings {
        TUNABLE("Merge", "CFA residual robustness",
                "Track locally accepted frame weights to reduce unmerged blotches, independently of CFA "
                "residual fusion strength. Off restores the original robustness for comparison")
        bool cfaResidualRobustness = true;

        TUNABLE("Merge", "GPU sync profiling",
                "Drain the GPU queue after every merge-loop stage so Stage[merge:*] logs measure true "
                "per-stage time (submit + GPU execution) instead of submit-only. Measurement only: serializes "
                "the pipeline and inflates the shot time")
        bool profileGpuSync = false;
    };

    void run(BurstState& state) override;
};

} // namespace photon::esd4d
