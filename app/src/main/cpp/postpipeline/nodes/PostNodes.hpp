//
// Created by eszdman on 22.11.2026.
//

#pragma once
#include "framework/post/PostState.hpp"
#include "framework/kompute/KomputePipeline.hpp"

namespace photon::post {
using PostNode = esd4d::KomputeNode<PostState>;
class Bayer2Float final : public PostNode {
  public:
    explicit Bayer2Float(const BayerSettings& settings) : Bayer2Float(settings, settings.offset) {}
    Bayer2Float(const BayerSettings& settings, Size offset) : settings_(settings), offset_(offset) {}

  private:
    const BayerSettings& settings_;
    Size offset_;
    void run(PostState&) override;
};
class Amaze final : public PostNode {
    void run(PostState&) override;
};
class Demosaic final : public PostNode {
    void run(PostState&) override;
};
class DemosaicQuad final : public PostNode {
    void run(PostState&) override;
};
class MonoDemosaic final : public PostNode {
    void run(PostState&) override;
};
class ABLC final : public PostNode {
    void run(PostState&) override;
};

class DisplayNode : public PostNode {
  public:
    explicit DisplayNode(const DisplaySettings& settings) : display(settings) {}

  protected:
    const DisplaySettings& display;
};
class LocalLaplacian2 final : public DisplayNode {
    using DisplayNode::DisplayNode;
    void run(PostState&) override;
};
class CaptureSharpening final : public DisplayNode {
    using DisplayNode::DisplayNode;
    void run(PostState&) override;
};
class CorrectingFlow final : public DisplayNode {
    using DisplayNode::DisplayNode;
    void run(PostState&) override;
};
class Sharpen2 final : public DisplayNode {
    using DisplayNode::DisplayNode;
    void run(PostState&) override;
};
class RotateWatermark final : public DisplayNode {
  public:
    RotateWatermark(const DisplaySettings& settings, Sdr sink) : DisplayNode(settings), sink_(sink) {}
    void run(PostState&) override;

  private:
    Sdr sink_;
};
class DeconvolutionSharpening final : public PostNode {
  public:
    DeconvolutionSharpening(const settings::DeconvolutionSharpening& tuning, float strength)
        : tuning_(tuning), strength_(strength) {}

  private:
    const settings::DeconvolutionSharpening& tuning_;
    float strength_;
    void run(PostState&) override;
};
class KernelNetPrep final : public PostNode {
  public:
    KernelNetPrep(esd4d::ComputeContext& ctx, const std::vector<uint16_t>& merged, float sigmaMpy)
        : ctx_(ctx), merged_(merged), sigmaMpy_(sigmaMpy) {}

  private:
    esd4d::ComputeContext& ctx_;
    const std::vector<uint16_t>& merged_;
    float sigmaMpy_;
    void run(PostState&) override;
};
class UpscaleCrop final : public PostNode {
  public:
    UpscaleCrop(const PostPipelineSettings& options, Size target) : options_(options), target_(target) {}

  private:
    const PostPipelineSettings& options_;
    Size target_;
    void run(PostState&) override;
};
class CaptureSceneLuma final : public PostNode {
  public:
    CaptureSceneLuma(Size target, const DisplaySettings& display) : target_(target), display_(display) {}
    void run(PostState&) override;

  private:
    Size target_;
    const DisplaySettings& display_;
};
class RenderGainMap final : public PostNode {
  public:
    RenderGainMap(Sdr base, const ToneSettings& tone, float scale, SdrImage& output)
        : base_(base), tone_(tone), scale_(scale), output_(output) {}

  private:
    Sdr base_;
    const ToneSettings& tone_;
    float scale_;
    SdrImage& output_;
    void run(PostState&) override;
};
} // namespace photon::post
