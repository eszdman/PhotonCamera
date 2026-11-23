//
// Created by eszdman on 22.11.2026.
//

#pragma once
#include "PostState.hpp"
#include "framework/kompute/KomputePipeline.hpp"

namespace photon::post {
struct LinearStages {
    const LinearSettings& settings;
    const BayerSettings* bayer;
    Size offset;
};
struct CaptureStages {
    const PostPipelineSettings& settings;
    esd4d::ComputeContext& context;
    const std::vector<uint16_t>& merged;
    Size work;
    bool cropped;
    SdrImage* hdrGainMap;
    float gainMapScale;
};
struct DisplayStages {
    const DisplaySettings& settings;
    Sdr sink;
};

// A full render uses all three sections. Checkpoint renders select just linear or display.
using PostGraph = esd4d::KomputePipeline<PostState>;
PostGraph buildDefaultPipeline(const LinearStages* linear, const CaptureStages* capture,
                               const DisplayStages* display);
} // namespace photon::post
