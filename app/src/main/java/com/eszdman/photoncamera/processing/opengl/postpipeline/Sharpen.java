package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.ui.camera.CameraFragment;

public class Sharpen extends Node {
    public Sharpen(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Configure() {
        glProg.setDefine("MSIZE",getTuning("MSIZE",3));
        glProg.setDefine("MINDEPTH",getTuning("MINDEPTH",0.0004));
        glProg.setDefine("BIGSHARP",getTuning("BIGSHARP",1.0));
        glProg.setDefine("DYNAMICSTRKOEFF",getTuning("DYNAMICSTRKOEFF",7.7));
        glProg.setDefine("DYNAMICSTRCONST",getTuning("DYNAMICSTRCONST",3.1));
        glProg.setDefine("SHARPEDGE",getTuning("SHARPEDGE",0.2));
        glProg.setDefine("SHARPKERNEL",getTuning("SHARPKERNEL",0.9));
    }

    @Override
    public void Run() {
        float sharpnessLevel = (float) Math.sqrt((CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * IsoExpoSelector.getMPY() - 50.) / 14.2f;
        sharpnessLevel = Math.max(0.5f, sharpnessLevel);
        sharpnessLevel = Math.min(1.5f, sharpnessLevel);
        Log.d("PostNode:" + Name, "sharpnessLevel:" + sharpnessLevel + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        glProg.setVar("size", 1.f);
        glProg.setVar("strength", PreferenceKeys.getSharpnessValue());
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
    }
}
