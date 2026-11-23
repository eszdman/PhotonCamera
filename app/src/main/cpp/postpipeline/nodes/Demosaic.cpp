//
// Created by eszdman on 21.11.2026.
//

#include "PostNodes.hpp"
#include "demosaicp1.hpp"
#include "demosaicp2.hpp"
#include "demosaicp0quad.hpp"
#include "demosaicp12quad.hpp"
#include "demosaicp2quad.hpp"
#include "monochrome.hpp"

namespace photon::post {
using namespace shaders;

void MonoDemosaic::run(PostState& s) {
    monochrome::Parameters p;
    p.blackLevel = s.settings.monoBlackLevel;
    s.passes.dispatch(p, monochrome::RawBuffer(s.cfa), monochrome::Output(s.rgb));
}

void Demosaic::run(PostState& s) {
    auto green = s.scalar(s.image);
    s.passes.dispatch({}, demosaicp1::RawBuffer(s.cfa), demosaicp1::Output(green));
    const auto& wp = s.settings.whitePoint;
    demosaicp2::Parameters p;
    p.neutral = {wp[0], wp[1], wp[1], wp[2]};
    s.passes.dispatch(p, demosaicp2::RawBuffer(s.cfa), demosaicp2::GreenBuffer(green), demosaicp2::Output(s.rgb));
}

void DemosaicQuad::run(PostState& s) {
    auto grad = s.rgba(s.image);
    auto green = s.scalar(s.image);
    s.passes.dispatch({}, demosaicp0quad::RawBuffer(s.cfa), demosaicp0quad::Output(grad));
    demosaicp12quad::Parameters p;
    p.GRADSIZE = s.settings.quadGradSize;
    p.NOISEO = s.settings.noiseO;
    p.NOISES = s.settings.noiseS;
    s.passes.dispatch(p, demosaicp12quad::RawBuffer(s.cfa), demosaicp12quad::GradBuffer(grad),
                      demosaicp12quad::Output(green));
    s.passes.dispatch({}, demosaicp2quad::RawBuffer(s.cfa), demosaicp2quad::GreenBuffer(green),
                      demosaicp2quad::Output(s.rgb));
}
} // namespace photon::post
