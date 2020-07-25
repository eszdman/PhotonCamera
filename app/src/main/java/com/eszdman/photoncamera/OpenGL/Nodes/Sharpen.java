package com.eszdman.photoncamera.OpenGL.Nodes;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;

public class Sharpen extends Node {
    public Sharpen(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        GLInterface glint = basePipeline.glint;
        Node Previous = super.previousNode;
        GLProg glProg = glint.glprogram;
        float sharpnessLevel = (float)Math.sqrt((CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY))* IsoExpoSelector.getMPY() - 50.)/9.2f;
        sharpnessLevel = Math.max(0.8f,sharpnessLevel);
        sharpnessLevel = Math.min(2.0f, sharpnessLevel);
        Log.d("PostNode:"+Name, "sharpnessLevel:" + sharpnessLevel + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        glProg.servar("size", (float)sharpnessLevel);
        glProg.servar("strength", (float)Interface.i.settings.sharpness);
        glProg.setTexture("InputBuffer",Previous.WorkingTexture);
        super.WorkingTexture = new GLTexture(Previous.WorkingTexture.mSize,Previous.WorkingTexture.mFormat,null);
    }
}
