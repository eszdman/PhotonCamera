//
// Created by eszdman on 21.11.2026.
//

#pragma once
#include "PostNodes.hpp"

namespace photon::post {
class ToneNode : public PostNode {
  public:
    explicit ToneNode(const ToneSettings& settings) : tone(settings) {}

  protected:
    const ToneSettings& tone;
};
class AutoExposureCurve final : public ToneNode {
    using ToneNode::ToneNode;
    void run(PostState&) override;
};
class LinearExposure final : public ToneNode {
    using ToneNode::ToneNode;
    void run(PostState&) override;
};
class Initial final : public ToneNode {
    using ToneNode::ToneNode;
    template <class Shader> void render(PostState&);
    void run(PostState&) override;
};
class ModernInitial final : public ToneNode {
    using ToneNode::ToneNode;
    template <class Shader> void render(PostState&);
    void run(PostState&) override;
};
class HeadroomRender final : public ToneNode {
    using ToneNode::ToneNode;
    void run(PostState&) override;
};
} // namespace photon::post
