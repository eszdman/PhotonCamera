package com.eszdman.photoncamera.OpenGL.Nodes;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;

public class Bilateral extends Node {
    public Bilateral(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        startT();
        GLInterface glint = basePipeline.glint;
        Node Previous = super.previousNode;
        GLProg glProg = glint.glprogram;
        //glProg.servar("size", 5);
        float denoiseLevel = (float)Math.sqrt(Math.log(CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)))/1.2f;
        denoiseLevel+=0.35f;
        Log.d("PostNode:"+Name, "denoiseLevel:" + denoiseLevel + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        glProg.servar("sigma", denoiseLevel,0.1f);
        glProg.setTexture("InputBuffer",Previous.WorkingTexture);
        super.WorkingTexture = new GLTexture(Previous.WorkingTexture.mSize,Previous.WorkingTexture.mFormat,null);
        endT();
    }
}
