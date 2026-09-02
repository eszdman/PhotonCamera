package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.hardware.camera2.CaptureResult;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;

public class Sharpen2 extends Node {
    public Sharpen2() {
        super("", "Sharpening");
    }

    @Override
    public void Compile() {
    }
    
    @Tunable(
            title = "Sharp Size", description = "Size parameter for sharpening",
            category = "Sharpening", min = 0.0f, max = 2.0f, defaultValue = 0.8f, step = 0.01f
    )
    float sharpSize;
    
    @Tunable(
            title = "Sharp Min", description = "Minimum sharpening threshold",
            category = "Sharpening", min = 0.0f, max = 2.0f, defaultValue = 0.25f, step = 0.01f
    )
    float sharpMin;
    
    @Tunable(
            title = "Sharp Max", description = "Maximum sharpening threshold",
            category = "Sharpening", min = 0.0f, max = 2.0f, defaultValue = 1.0f, step = 0.01f
    )
    float sharpMax;
    
    @Tunable(
            title = "Denoise Activity", description = "Denoise intensity parameter",
            category = "Sharpening", min = 0.0f, max = 1.0f, defaultValue = 0.0f, step = 0.01f)
    float denoiseActivity;
    
    @Override
    public void Run() {
        glProg.setDefine("INTENSE",denoiseActivity);
        glProg.setDefine("INSIZE",previousNode.WorkingTexture.mSize);
        glProg.setDefine("SHARPSIZE",sharpSize);
        glProg.setDefine("SHARPMIN",sharpMin);
        glProg.setDefine("SHARPMAX",sharpMax);
        glProg.setDefine("NOISES",basePipeline.noiseS);
        glProg.setDefine("NOISEO",basePipeline.noiseO);
        glProg.useAssetProgram("sharpening/lsharpening3");
        glProg.setVar("size", sharpSize);
        float sharpness = Math.max(PreferenceKeys.getSharpnessValue(), 0.0f);
        if (basePipeline.mParameters.isCropped) {
            // Unsharp masks tuned for native detail overshoot on interpolated
            // pixels; the kernelnet reconstruction carries the acutance here.
            sharpness *= ((PostPipeline) basePipeline).upscaleSharpenScale;
        }
        glProg.setVar("strength", sharpness);
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        glProg.setTexture("BlurBuffer",previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
