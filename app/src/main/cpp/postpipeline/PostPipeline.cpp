//
// Created by eszdman on 21.11.2026.
//

#include "framework/post/PostGraph.hpp"
#include "nodes/ToneNodes.hpp"
#include <algorithm>

namespace photon::post {

PostGraph buildDefaultPipeline(const LinearStages* linear, const CaptureStages* capture,
                               const DisplayStages* display) {
    PostGraph nodes;

    if (linear) {
        const auto& settings = linear->settings;
        if (linear->bayer) nodes.add<Bayer2Float>(*linear->bayer, linear->offset);
        if (capture && capture->cropped && settings.demosaicing != Demosaicing::mergedRgb &&
            capture->settings.kernelMap.pixels.empty())
            nodes.add<KernelNetPrep>(capture->context, capture->merged, capture->settings.kernelNet.singleSigmaMpy);

        switch (settings.demosaicing) {
        case Demosaicing::amaze:      nodes.add<Amaze>(); break;
        case Demosaicing::legacy:     nodes.add<Demosaic>(); break;
        case Demosaicing::quad:       nodes.add<DemosaicQuad>(); break;
        case Demosaicing::monochrome: nodes.add<MonoDemosaic>(); break;
        case Demosaicing::mergedRgb:  break;
        default: throw std::invalid_argument("Unknown demosaicing mode");
        }
        if (settings.esd3d.enabledFor(settings)) nodes.add<ESD3D2>(settings.esd3d);
        if (settings.blackLevels.enable) nodes.add<ABLC>();
    }

    if (capture) {
        const auto& p = capture->settings;
        float strength = p.sharpening.amount * std::clamp((.2f + p.display.sharpness) / .2f, 0.f, 1.f);
        if (capture->cropped) strength *= p.display.cropSharpenScale;
        if (p.deconvolution && !p.display.unsharp && strength > 0 && p.sharpening.iterations > 0 &&
            p.sharpening.contrastRatio < 1)
            nodes.add<DeconvolutionSharpening>(p.sharpening, strength);
        if (capture->hdrGainMap) nodes.add<CaptureSceneLuma>(capture->work, p.display);

        switch (p.tone.mode) {
        case ToneMode::off:
            nodes.add<LinearExposure>(p.tone);
            break;
        case ToneMode::sky:
            nodes.add<LinearExposure>(p.tone);
            nodes.add<HeadroomRender>(p.tone);
            break;
        case ToneMode::curve:
            nodes.add<AutoExposureCurve>(p.tone);
            nodes.add<Initial>(p.tone);
            break;
        case ToneMode::modern:
            nodes.add<AutoExposureCurve>(p.tone);
            nodes.add<ModernInitial>(p.tone);
            break;
        }
        if (capture->cropped) nodes.add<UpscaleCrop>(p, capture->work);
    }

    if (display) {
        const auto& d = display->settings;
        if (d.localContrast.enabled) nodes.add<LocalLaplacian2>(d);
        if (d.unsharp && d.captureSharpening) nodes.add<CaptureSharpening>(d);
        if (d.aberration[0] || d.aberration[1]) nodes.add<CorrectingFlow>(d);
        if (d.unsharp) nodes.add<Sharpen2>(d);
        nodes.add<RotateWatermark>(d, display->sink);
        if (capture && capture->hdrGainMap)
            nodes.add<RenderGainMap>(display->sink, capture->settings.tone, capture->gainMapScale, *capture->hdrGainMap);
    }
    return nodes;
}

} // namespace photon::post
