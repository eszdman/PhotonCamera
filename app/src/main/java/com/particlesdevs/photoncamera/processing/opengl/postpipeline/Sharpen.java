package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.capture.CaptureController;

public class Sharpen extends Node {
    public Sharpen(int rid) {
        super(rid, "Sharpening");
    }
    @Override
    public void Run() {
        float sharpnessLevel = (float) Math.sqrt((CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * IsoExpoSelector.getMPY() - 50.) / 14.2f;
        sharpnessLevel = Math.max(0.5f, sharpnessLevel);
        sharpnessLevel = Math.min(1.5f, sharpnessLevel);
        Log.d("PostNode:" + Name, "sharpnessLevel:" + sharpnessLevel + " iso:" + CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        glProg.setVar("size", 1.5f);
        glProg.setVar("strength", PreferenceKeys.getSharpnessValue());
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
    }
}
