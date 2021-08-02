package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

public class SharpenDual extends Node {
    public SharpenDual() {
        super(0, "Sharpening");
    }

    @Override
    public void Compile() {
    }
    float blurSize = 0.20f;
    float sharpSize = 1.5f;
    float sharpMin = 0.5f;
    float sharpMax = 1.f;
    @Override
    public void Run() {
        blurSize = getTuning("BlurSize", blurSize);
        sharpSize = getTuning("SharpSize", sharpSize);
        sharpMin = getTuning("SharpMin",sharpMin);
        sharpMax = getTuning("SharpMax",sharpMax);
        float sharpnessLevel = (float) Math.sqrt((CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * IsoExpoSelector.getMPY() - 50.) / 14.2f;
        sharpnessLevel = Math.max(0.5f, sharpnessLevel);
        sharpnessLevel = Math.min(1.5f, sharpnessLevel);
        glProg.setDefine("SAVEGREEN",true);
        glProg.setDefine("size1",blurSize);
        glProg.useProgram(R.raw.blur);
        //glProg.setVar("size", blurSize);
        //glProg.setVar("strength", PreferenceKeys.getSharpnessValue());
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.drawBlocks(basePipeline.main3);
        glProg.setDefine("INSIZE",basePipeline.mParameters.rawSize);
        glProg.setDefine("SHARPSIZE",sharpSize);
        glProg.setDefine("SHARPMIN",sharpMin);
        glProg.setDefine("SHARPMAX",sharpMax);
        glProg.useProgram(R.raw.lsharpening);
        Log.d("PostNode:" + Name, "sharpnessLevel:" + sharpnessLevel + " iso:" + CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        glProg.setVar("size", sharpSize);
        glProg.setVar("strength", PreferenceKeys.getSharpnessValue());
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        glProg.setTexture("BlurBuffer",basePipeline.main3);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
