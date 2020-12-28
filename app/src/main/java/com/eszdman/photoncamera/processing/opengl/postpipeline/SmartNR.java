package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.ui.camera.CameraFragment;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class SmartNR extends Node {

    public SmartNR(String name) {
        super(0, name);
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        GLTexture inpdetect = glUtils.interpolate(previousNode.WorkingTexture,1.0/2.0);
        glProg.useProgram(R.raw.noisedetection44);
        glProg.setTexture("InputBuffer", inpdetect);
        GLTexture detect = new GLTexture(inpdetect.mSize, new GLFormat(GLFormat.DataType.FLOAT_16,3), null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.drawBlocks(detect);
        GLTexture detectresize = glUtils.gaussdown(detect,3);
        detect.close();
        GLTexture detectblur = new GLTexture(detectresize);
        glProg.useProgram(R.raw.medianfilter);
        glProg.setTexture("InputBuffer",detectresize);
        glProg.setVar("tpose",1,1);
        glProg.drawBlocks(detectblur);
        detectresize.close();
        glProg.useProgram(R.raw.medianfilter);
        glProg.setTexture("InputBuffer",detectblur);
        glProg.setVar("tpose",1,1);
        GLTexture detectblur2 = new GLTexture(detectblur);
        glProg.drawBlocks(detectblur2);
        detectblur.close();

        /*GLTexture detectblur2 = new GLTexture(detectblur);
        glProg.useProgram(R.raw.fastmedian2);
        glProg.setTexture("InputBuffer",detectblur);
        glProg.setVar("tpose",1,1);
        glProg.drawBlocks(detectblur2);
        detectblur.close();*/
        //GLTexture detectblur3 = glUtils.blurfast(detectblur2,1.5);
        //detectblur2.close();

        float denoiseLevel = (float) Math.sqrt((CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * IsoExpoSelector.getMPY() - 50.)*6400.f / (6.2f*IsoExpoSelector.getISOAnalog());
        denoiseLevel += 0.25;
        float str = (float)basePipeline.mSettings.noiseRstr;
        if(str > 1.0)
        denoiseLevel*=str;
        //Chroma NR
        /*glProg.useProgram(R.raw.bilateralcolor);

        Log.d("PostNode:" + Name, "denoiseLevel:" + denoiseLevel + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        denoiseLevel = Math.min(5.0f, denoiseLevel);
        glProg.setVar("sigma", denoiseLevel, denoiseLevel * 2.f);
        glProg.setVar("mapsize", detectblur.mSize);
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        glProg.setTexture("NoiseMap", detectblur);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);*/
        Log.d("PostNode:" + Name, "denoiseLevel:" + denoiseLevel + " iso:" + CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        //glProg.useProgram(R.raw.nlmeans);
        if (denoiseLevel > 2.0) {
        glProg.useProgram(R.raw.bilateralguide);
        glProg.setVar("tpose",1,1);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        //glProg.setTexture("NoiseMap",detectblur2);
        int kernelsize = (int)(denoiseLevel) + 1;
        kernelsize = Math.max(kernelsize,2);
        kernelsize = Math.min(kernelsize,8);
        Log.d("PostNode:" + Name, "denoiseLevel:" + denoiseLevel + " windowSize:" + kernelsize);
        glProg.setVar("kernel",kernelsize);
        if(str > 1.f) str = 1.f;
        if(denoiseLevel > 6.f)
        glProg.setVar("isofactor",denoiseLevel*str/9.f);
        else glProg.setVar("isofactor",1.f);
        glProg.setVar("size",previousNode.WorkingTexture.mSize);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        } else{
            WorkingTexture = previousNode.WorkingTexture;
        }
        detectblur.close();
        if(denoiseLevel>=7.0) {
            GLTexture outp = glUtils.interpolate(WorkingTexture, 1.0 / 3.0);
            for (int i = 0; i < 2; i++) {

                /*GLTexture in = outp;
                glProg.useProgram(R.raw.nlmeans);
                glProg.setVar("tpose",1,1);
                glProg.setTexture("InputBuffer",in);
                glProg.setTexture("NoiseMap",detectblur);
                int kernelsize = (int)(denoiseLevel) + 1;
                kernelsize = Math.max(kernelsize,2);
                kernelsize = Math.min(kernelsize,8);
                Log.d("PostNode:" + Name, "denoiseLevel:" + denoiseLevel + " windowSize:" + kernelsize);
                glProg.setVar("kernel",kernelsize);
                if(denoiseLevel > 6.f)
                    glProg.setVar("isofactor",(float)PhotonCamera.getSettings().noiseRstr*denoiseLevel/6.f);
                else glProg.setVar("isofactor",1.f);
                glProg.setVar("size",previousNode.WorkingTexture.mSize);
                outp = new GLTexture(outp);
                glProg.drawBlocks(outp);
                in.close();*/

                glProg.useProgram(R.raw.hybridmedianfiltercolor);
                glProg.setVar("robust", 10.5f - denoiseLevel + 3.5f);
                glProg.setVar("tpose", 1, 1);
                GLTexture in = outp;
                glProg.setTexture("InputBuffer", in);
                outp = new GLTexture(outp);
                glProg.drawBlocks(outp);
                in.close();
            }
            WorkingTexture = glUtils.ops(outp, WorkingTexture, basePipeline.getMain(), "(in1.rgba/((in1.r+in1.g+in1.b)/3.0))*((in2.r+in2.g+in2.b)/3.0)", "", 1);
        } else {
            for(int i =1;i<2;i++) {
                glProg.useProgram(R.raw.hybridmedianfiltercolor);
                glProg.setVar("robust", 10.5f - denoiseLevel + 3.5f);
                glProg.setVar("tpose", 1, i);
                glProg.setTexture("InputBuffer", WorkingTexture);
                WorkingTexture = basePipeline.getMain();
                glProg.drawBlocks(WorkingTexture);
                glProg.setVar("robust", 10.5f - denoiseLevel + 3.5f);
                glProg.setVar("tpose", i, 1);
                glProg.setTexture("InputBuffer", WorkingTexture);
                WorkingTexture = basePipeline.getMain();
                glProg.drawBlocks(WorkingTexture);
            }
        }
        glProg.closed = true;
    }
}
