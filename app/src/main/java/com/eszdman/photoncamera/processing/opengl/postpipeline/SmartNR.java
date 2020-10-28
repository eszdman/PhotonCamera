package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.ui.camera.CameraFragment;

public class SmartNR extends Node {

    public SmartNR(String name) {
        super(0, name);
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        glProg.useProgram(R.raw.noisedetection44);
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        GLTexture detect = new GLTexture(previousNode.WorkingTexture.mSize, new GLFormat(GLFormat.DataType.FLOAT_16), null);
        glProg.drawBlocks(detect);
        glProg.close();
        GLTexture detectresize = glUtils.gaussdown(detect,4);
        detect.close();
        GLTexture detectblur = glUtils.blur(detectresize,1.2);
        detectresize.close();

        //Chroma NR
        glProg.useProgram(R.raw.bilateralcolor);
        float denoiseLevel = (float) Math.sqrt((CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * IsoExpoSelector.getMPY() - 50.) / 6.2f;
        denoiseLevel += 0.25;
        Log.d("PostNode:" + Name, "denoiseLevel:" + denoiseLevel + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        denoiseLevel = Math.min(5.0f, denoiseLevel);
        glProg.setVar("sigma", denoiseLevel, denoiseLevel * 2.f);
        glProg.setVar("mapsize", detectblur.mSize);
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        glProg.setTexture("NoiseMap", detectblur);
        WorkingTexture = new GLTexture(previousNode.WorkingTexture);
        detectblur.close();
    }
}
