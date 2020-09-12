package com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.api.CameraController;
import com.eszdman.photoncamera.api.CameraFragment;

public class BilateralColor extends Node {
    public BilateralColor(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        PostPipeline postPipeline = ((PostPipeline)basePipeline);
        GLInterface glint = basePipeline.glint;
        Node Previous = super.previousNode;
        GLProg glProg = glint.glprogram;
        //glProg.servar("size", 5);
        float denoiseLevel = (float)Math.sqrt((CameraController.GET().getCaptureResult().get(CaptureResult.SENSOR_SENSITIVITY))* IsoExpoSelector.getMPY() - 50.)/6.2f;
        denoiseLevel+=0.25;
        Log.d("PostNode:"+Name, "denoiseLevel:" + denoiseLevel + " iso:" + CameraController.GET().getCaptureResult().get(CaptureResult.SENSOR_SENSITIVITY));
        denoiseLevel = Math.min(5.0f,denoiseLevel);
        glProg.setvar("sigma", denoiseLevel,denoiseLevel*2.f);
        glProg.setvar("mapsize",(float)Previous.WorkingTexture.mSize.x,(float)Previous.WorkingTexture.mSize.y);
        glProg.setTexture("InputBuffer",Previous.WorkingTexture);
        glProg.setTexture("NoiseMap",((PostPipeline)basePipeline).noiseMap);
        super.WorkingTexture = new GLTexture(Previous.WorkingTexture.mSize,Previous.WorkingTexture.mFormat,null);
    }
}
