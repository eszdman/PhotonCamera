//
// Created by eszdman on 21.11.2026.
//

#pragma once

#include "BurstNode.hpp"
#include "framework/tuning/Annotations.hpp"

namespace photon::esd4d {

class EstimateNoise final : public BurstNode {
  public:
    struct Settings {
        TUNABLE("Merge", "Enable Adaptive Noise Model",
                "Creates noise multiplier based on stdev")
        bool enableAdaptiveNoise = true;

        TUNABLE("Merge", "Enable Adaptive Noise Storage",
                "Persist fitted noise model into the dynamic multisample store")
        bool enableNoiseStore = true;

        TUNABLE("Merge", "Low ISO noise variance limit",
                "Maximum fitted variance relative to the sensor model at low ISO until a higher-ISO "
                "calibration is available; 2 limits sigma to sqrt(2) of the sensor model", 1.0, 8.0, 0.25)
        float lowIsoNoiseVarianceLimit = 2.0f;

        TUNABLE("Merge", "Noise blend max frames",
                "Frames combined into the deliberately misaligned progressive Gaussian blend used for noise "
                "estimation (blurs scene detail while noise only drops by a known factor)", 1, 9, 1)
        int noiseBlendMaxFrames = 9;

        TUNABLE("Merge", "Noise blend calibration",
                "Trim multiplier on the physical spatial and temporal residual response (1.0 = analytical "
                "response)", 0.5, 2.0, 0.05)
        float noiseBlendCalMpy = 1.0f;

        TUNABLE("Merge", "Noise histogram range",
                "Residual histogram range in expected noise sigmas; lower values resolve low ISO more finely", 2.0, 8.0, 0.5)
        float noiseHistogramRange = 4.0f;

        TUNABLE("Merge", "Noise brightness texture gate",
                "Variance allowance above the noise curve supported by neighboring brightness rows", 1.25, 4.0, 0.05)
        float noiseBrightnessTextureGate = 1.8f;

        TUNABLE("Merge", "Noise scan subsample",
                "Maximum stride between texels evaluated by the noise histogram; small inputs use denser "
                "scanning to retain enough samples", 1, 8, 1)
        int noiseScanSubsample = 3;

        TUNABLE("Merge", "Read noise floor multiplier",
                "Multiplier on the analytic OPlace read-noise floor applied to fitted O; the legacy 3.0 "
                "compensated texture leakage that the noise blend now removes", 0.5, 4.0, 0.25)
        float noiseOFloorMpy = 1.0f;

        TUNABLE("Merge", "Fit O correction",
                "Legacy fitO += 3/8*fitS^2 correction that compensated the under-rescaled fit; keep off with "
                "the calibrated blend")
        bool enableFitOCorrection = false;

        TUNABLE("Merge", "Adaptive fallback min",
                "Lower clamp of the fallback adaptive multiplier (was 1.0, up-only)", 0.25, 2.0, 0.25)
        float adaptiveFallbackMin = 0.5f;

        TUNABLE("Merge", "Adaptive fallback max",
                "Upper clamp of the fallback adaptive multiplier (was 4.0)", 1.0, 4.0, 0.25)
        float adaptiveFallbackMax = 2.0f;
    };

    void run(BurstState& state) override;
};

} // namespace photon::esd4d
