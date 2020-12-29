package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.capture.CaptureController;

public class Median extends Node {
    Point transposing;
    public Median(Point transpose, String name, int original) {
        super(original, name);
        transposing = transpose;
    }

    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline) basePipeline;
        GLInterface glint = basePipeline.glint;
        Node Previous = previousNode;
        GLProg glProg = glint.glProgram;
        //glProg.servar("size", 5);
        float denoiseLevel = (float) Math.sqrt((CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * IsoExpoSelector.getMPY() - 50.) / 9.2f;
        denoiseLevel -= 0.2;
        Log.d("PostNode:" + Name, "denoiseLevel:" + denoiseLevel + " iso:" + CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        denoiseLevel = Math.min(10.5f, denoiseLevel);
        Log.d(Name,"denoiseLevel:"+denoiseLevel);
        glProg.setVar("robust",10.5f-denoiseLevel + 3.5f);
        //glProg.setVar("robust",2.5f);
        glProg.setVar("tpose",transposing);
        glProg.setTexture("InputBuffer", Previous.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.closed = false;
    }
}
