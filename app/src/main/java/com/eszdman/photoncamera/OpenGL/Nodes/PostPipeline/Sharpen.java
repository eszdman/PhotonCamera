package com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.CameraFragment;
import com.eszdman.photoncamera.settings.PreferenceKeys;

public class Sharpen extends Node {
    public Sharpen(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        GLInterface glint = basePipeline.glint;
        Node Previous = super.previousNode;
        GLProg glProg = glint.glprogram;
        float sharpnessLevel = (float)Math.sqrt((CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY))* IsoExpoSelector.getMPY() - 50.)/14.2f;
        sharpnessLevel = Math.max(0.5f,sharpnessLevel);
        sharpnessLevel = Math.min(1.5f, sharpnessLevel);
        Log.d("PostNode:"+Name, "sharpnessLevel:" + sharpnessLevel + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        glProg.setvar("size", sharpnessLevel);
        glProg.setvar("strength", (float) PreferenceKeys.getSharpnessValue());
        glProg.setTexture("InputBuffer",Previous.WorkingTexture);
        super.WorkingTexture = new GLTexture(Previous.WorkingTexture.mSize,Previous.WorkingTexture.mFormat,null);
    }
}
