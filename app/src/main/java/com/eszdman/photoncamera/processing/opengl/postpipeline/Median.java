package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.ui.camera.CameraFragment;

public class Median extends Node {
    public Median(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline) basePipeline;
        GLInterface glint = basePipeline.glint;
        Node Previous = previousNode;
        GLProg glProg = glint.glProgram;
        //glProg.servar("size", 5);
        float denoiseLevel = (float) Math.sqrt((CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * IsoExpoSelector.getMPY() - 50.) / 9.2f;
        denoiseLevel -= 0.2;
        Log.d("PostNode:" + Name, "denoiseLevel:" + denoiseLevel + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        denoiseLevel = Math.min(7.f, denoiseLevel);
        //glProg.setVar("sigma", denoiseLevel, 0.12f);
        //glProg.setVar("mapsize", (float) Previous.WorkingTexture.mSize.x, (float) Previous.WorkingTexture.mSize.y);
        glProg.setTexture("InputBuffer", Previous.WorkingTexture);
        //glProg.setTexture("NoiseMap", postPipeline.noiseMap);
        WorkingTexture = new GLTexture(Previous.WorkingTexture.mSize, Previous.WorkingTexture.mFormat, null);
    }
}
