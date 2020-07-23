package com.eszdman.photoncamera.OpenGL.Nodes;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.ui.CameraFragment;

public class BilateralColor extends Node {
    public BilateralColor(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        GLInterface glint = basePipeline.glint;
        Node Previous = super.previousNode;
        GLProg glProg = glint.glprogram;
        //glProg.servar("size", 5);
        float denoiseLevel = (float)Math.sqrt((CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY))* IsoExpoSelector.getMPY() - 50.)/6.2f;
        denoiseLevel+=0.25;
        Log.d("PostNode:"+Name, "denoiseLevel:" + denoiseLevel + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        denoiseLevel = Math.min(5.0f,denoiseLevel);
        glProg.servar("sigma", denoiseLevel,denoiseLevel*2.f);
        glProg.setTexture("InputBuffer",Previous.WorkingTexture);
        super.WorkingTexture = new GLTexture(Previous.WorkingTexture.mSize,Previous.WorkingTexture.mFormat,null);
    }
}
