//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include <cstddef>
#include <vector>
#include "BurstNode.hpp"
#include "framework/tuning/Annotations.hpp"

namespace photon::esd4d {

class ComputeContext;
struct Sensor;
struct BurstFrame;
struct NoiseModel;

class RepairHotPixels final : public BurstNode {
  public:
    struct Settings {
        TUNABLE("Merge", "Max hotPixels",
                "Maximum proposed Bayer quads; skip correction if exceeded", 16384, 262144, 1000)
        int MAX_HOT_PIXELS = 65535;

        TUNABLE("Merge", "Max reasonable hotPixels",
                "Maximum confirmed defect sites; skip correction if exceeded", 1000, 10000, 100)
        int MAX_REASONABLE_HOTPIXELS = 2000;

        TUNABLE("Merge", "Enable hotPixel correction",
                "Conservative burst-only correction of persistent isolated bright sensor defects before "
                "alignment. Requires at least three frames")
        bool enableHotPixelCorrection = false;

        TUNABLE("Merge", "HotPixels detect threshold",
                "Higher values require stronger outliers; minimum six-sigma and burst/texture safeguards "
                "always apply", 0.5, 5.0, 0.1)
        double detectThr = 1.5;
    };

    void run(BurstState& state) override;
    static std::vector<std::size_t> run(ComputeContext&, const Sensor&, std::vector<BurstFrame>&,
                                        NoiseModel, const Settings&);
};

} // namespace photon::esd4d
