package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.capture.CaptureController;

public class Median extends Node {
    Point transposing;
    int size;
    public Median(Point transpose,int size, String name, String original) {
        super(original, name);
        transposing = transpose;
        this.size = size;
    }

    @Override
    public void BeforeCompile() {
        glProg.setDefine("TRANSPOSE",transposing);
        glProg.setDefine("SIZE",size);
    }

    @Override
    public void Run() {
        //glProg.servar("size", 5);
        //float denoiseLevel = (float) Math.sqrt((CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * IsoExpoSelector.getMPY() - 50.) / 9.2f;
        //denoiseLevel -= 0.2;
        //Log.d("PostNode:" + Name, "denoiseLevel:" + denoiseLevel + " iso:" + CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        //denoiseLevel = Math.min(10.5f, denoiseLevel);
        //Log.d(Name,"denoiseLevel:"+denoiseLevel);
        //glProg.setVar("robust",10.5f-denoiseLevel + 3.5f);
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
