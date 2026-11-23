//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include "BurstNode.hpp"
#include "framework/tuning/Annotations.hpp"

namespace photon::esd4d {

enum class AlignerSelect {
    halide TUNABLE_LABEL("Halide CPU neon"),
    gl TUNABLE_LABEL("PyramidAlign GPU"),
    flownet TUNABLE_LABEL("FlowNet optical flow"),
    off TUNABLE_LABEL("Off"),
};

enum class HalideMergeSampling {
    cubic TUNABLE_LABEL("CFA cubic (sharp subpixel)"),
    bilinear TUNABLE_LABEL("CFA bilinear (subpixel)"),
    lanczos2 TUNABLE_LABEL("CFA Lanczos-2"),
    nearest TUNABLE_LABEL("Texel nearest (rounded)"),
    texel TUNABLE_LABEL("Texel legacy (floor)"),
};

class StartAlignment final : public BurstNode {
  public:
    struct Settings {
        TUNABLE("Alignment", "Alignment start level",
                "Finest resolution fed to the block pyramid matcher: 0 = raw/2 (default), 1 = raw/4, 2 = "
                "raw/8. Each skipped level removes the most expensive matching pass (~4x faster alignment "
                "core per level) at the cost of a coarser alignment vector field", 0, 2, 1)
        int alignmentStartLevel = 0;

        TUNABLE("Alignment", "Aligner",
                "GL - GL block pyramid, FlowNet - network optical flow, Halide - Halide CPU")
        AlignerSelect alignerSelect = AlignerSelect::halide;

        TUNABLE("Alignment", "Gyroscope prealignment",
                "Prewarp Halide input using timestamped gyro motion and available OIS telemetry; off uses "
                "local alignment only")
        bool gyroPrealignment = false;

        TUNABLE("Alignment", "Gyroscope only",
                "Test mergeAlign with gyro/OIS warping and no local matcher. Overrides Aligner and enables "
                "gyro prealignment; missing gyro data uses identity")
        bool gyroOnly = false;

        TUNABLE("Alignment", "Aligner debug compare",
                "1 = when the Halide aligner runs, also run the GL block pyramid and log per-frame vector "
                "differences (diagnoses constant biases/shift conventions)", 0, 1, 1)
        int alignDebugCompare = 0;

        TUNABLE("Alignment", "Halide flow refinement",
                "Refine Halide vectors with Lucas-Kanade on the raw/2 grid: denser subpixel vectors, at "
                "increased alignment cost. Off uses the hierarchical field with one vector per 32 raw pixels. "
                "Only applies to Halide")
        bool halideRefineFlow = false;

        TUNABLE("Alignment", "Halide accurate subpixel",
                "Fit fractional motion on the existing Halide grid, with noise-aware shift scoring. Off "
                "restores the original matcher for comparison. Applies when Halide flow refinement is off")
        bool halideAccurateSubpixel = true;

        TUNABLE("Alignment", "Halide global coarse seed",
                "Experimental: ambiguous coarse tiles prefer a shift supported by the whole image; strong "
                "local matches can override it. Keeps the existing grid. Requires accurate subpixel and flow "
                "refinement off")
        bool halideCoarseGlobal = false;

        TUNABLE("Alignment", "Halide merge sampling",
                "Without gyro: cubic and Lanczos preserve fine detail; bilinear smooths more. Interpolated "
                "greens share their physical quincunx lattice while retaining distinct CFA positions. "
                "Residual fusion uses the matching sampling model")
        HalideMergeSampling halideMergeSampling = HalideMergeSampling::lanczos2;

        TUNABLE("Alignment", "Halide interpolate motion",
                "Experimental: interpolate vectors at their patch centers and sample the CFA once, instead of "
                "blending four warped samples")
        bool halideInterpolateMotion = false;

        TUNABLE("Merge", "CFA residual fusion",
                "Experimental Halide merge without gyro: 0 keeps the selected sampler; 0.5 tests regularized "
                "CFA residual reconstruction at 1x. Uses one interpolated motion vector and retains robust "
                "merge rejection. Higher values can amplify noise and ringing", 0, 1, 0.05)
        float cfaResidualFusion = 0.0f;
    };

    void run(BurstState& state) override;
};

} // namespace photon::esd4d
