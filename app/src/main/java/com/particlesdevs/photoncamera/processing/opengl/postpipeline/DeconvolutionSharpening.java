package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GpuCaptureDeconvolution;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;

/** Luminance capture deconvolution, applied before the tone curve. */
public final class DeconvolutionSharpening extends Node {
    @Tunable(title = "Radius", description = "Gaussian blur sigma in pixels",
            category = "Capture Deconvolution", min = 0.2f, max = 2f, defaultValue = 0.75f, step = 0.05f)
    public float radius = 0.75f;
    @Tunable(title = "Iterations", description = "Richardson-Lucy iterations; zero bypasses sharpening",
            category = "Capture Deconvolution", min = 0, max = 10, defaultValue = 10, step = 1)
    public int iterations = 10;
    @Tunable(title = "Contrast ratio", description = "Contrast-to-noise confidence: 0 is least protective, 1 disables sharpening",
            category = "Capture Deconvolution", min = 0, max = 1, defaultValue = 0.5f, step = 0.01f)
    public float contrastRatio = 0.5f;
    @Tunable(title = "Amount", description = "Final luminance blend strength",
            category = "Capture Deconvolution", min = 0, max = 1, defaultValue = 1, step = 0.05f)
    public float amount = 1f;

    public DeconvolutionSharpening() { super("", "DeconvolutionSharpening"); }
    @Override public void Compile() { }
    @Override public int halo() {
        return Math.max(8, 2 * GpuCaptureDeconvolution.kernelRadius(radius) * Math.max(0, Math.min(10, iterations)));
    }
    @Override public void Run() {
        GLTexture input = previousNode.WorkingTexture;
        PostPipeline pipeline = (PostPipeline) basePipeline;
        // Preserve the existing sharpness slider's negative-side bypass and crop attenuation.
        float strength = amount * Math.max(0f, Math.min(1f,
                (0.2f + PreferenceKeys.getSharpnessValue()) / 0.2f));
        if (pipeline.mParameters.isCropped) strength *= pipeline.upscaleSharpenScale;
        if (iterations <= 0 || strength <= 0f || contrastRatio >= 1f) {
            WorkingTexture = input;
            glProg.closed = true;
            return;
        }
        WorkingTexture = basePipeline.getMain();
        if (WorkingTexture == input) WorkingTexture = basePipeline.getMain();
        try (GpuCaptureDeconvolution filter = new GpuCaptureDeconvolution(
                glProg, input.mSize, radius, iterations, 512)) {
            // Reuse the same stack-adjusted model as spatial denoising. Keeping
            // its pre-denoise variance is conservative after spatial smoothing.
            filter.render(input, WorkingTexture, strength, contrastRatio,
                    Math.max(1f, pipeline.rawClipLevel), pipeline.noiseS, pipeline.noiseO);
        }
        glProg.closed = true;
    }
}
