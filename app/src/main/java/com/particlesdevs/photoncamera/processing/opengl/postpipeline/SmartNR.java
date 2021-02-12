package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.hardware.camera2.CaptureResult;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.capture.CaptureController;
import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class SmartNR extends Node {

    public SmartNR() {
        super(0, "SmartNR");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        float LumaDenoiseLevel = (float)Math.sqrt(basePipeline.mParameters.noiseModeler.computeModel[1].first);
        float ChromaDenoiseLevel = (float)Math.sqrt(basePipeline.mParameters.noiseModeler.computeModel[0].first);
        ChromaDenoiseLevel+=(float)Math.sqrt(basePipeline.mParameters.noiseModeler.computeModel[2].first);
        ChromaDenoiseLevel/=2.f;
        LumaDenoiseLevel*=basePipeline.mSettings.noiseRstr;
        ChromaDenoiseLevel*=basePipeline.mSettings.noiseRstr;
        float str = ((float)basePipeline.mSettings.noiseRstr)/16.f;
        Log.d("PostNode:" + Name, "LumaDenoiseLevel:" + LumaDenoiseLevel + " iso:" + CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));
        Log.d("PostNode:" + Name, "ChromaDenoiseLevel:" + ChromaDenoiseLevel + " iso:" + CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY));

        //glProg.useProgram(R.raw.nlmeans);

        if (LumaDenoiseLevel > 0.001) {
        GLTexture inpdetect = glUtils.interpolate(previousNode.WorkingTexture,1.0/2.0);
        glProg.useProgram(R.raw.noisedetection44);
        glProg.setTexture("InputBuffer", inpdetect);
        GLTexture detect = new GLTexture(inpdetect.mSize, new GLFormat(GLFormat.DataType.FLOAT_16,3), null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.drawBlocks(detect);
        GLTexture detectresize = glUtils.gaussdown(detect,3);
        detect.close();
        GLTexture detectblur = new GLTexture(detectresize);
        glProg.setDefine("TRANSPOSE",1,1);
        glProg.useProgram(R.raw.medianfilter);
        glProg.setTexture("InputBuffer",detectresize);
        glProg.drawBlocks(detectblur);
        detectresize.close();
        glProg.setDefine("TRANSPOSE",1,1);
        glProg.useProgram(R.raw.medianfilter);
        glProg.setTexture("InputBuffer",detectblur);
        GLFormat format = new GLFormat(detectblur.mFormat);
        format.wrap = GL_CLAMP_TO_EDGE;
        format.filter = GL_LINEAR;
        GLTexture detectblur2 = new GLTexture(detectblur,format);
        glProg.drawBlocks(detectblur2);
        detectblur.close();
        GLTexture tonemapUpscale = null;
        boolean tonemaped = false;
        int kernelsize = (int)(LumaDenoiseLevel*233.0);
        kernelsize = Math.max(kernelsize,1);
        kernelsize = Math.min(kernelsize,7);
        /*if(isofactor > 0.4){
            tonemaped = false;
            if(tonemaped) {
                tonemapUpscale = new GLTexture(previousNode.WorkingTexture.mSize, new GLFormat(GLFormat.DataType.FLOAT_16));
                glUtils.interpolate(((PostPipeline) (basePipeline)).FusionMap, tonemapUpscale);
            }
        }*/
        if(kernelsize >3) glProg.setDefine("MEDIAN",true);
        glProg.setDefine("KERNEL","("+kernelsize+")");
        //if(isofactor > 0.4){
        //    glProg.setDefine("TONEMAPED",false);
        //}
        glProg.setDefine("TONEMAPED",false);
        glProg.setDefine("ISOFACTOR",LumaDenoiseLevel*10.f);
        glProg.setDefine("NOISEDISTR",LumaDenoiseLevel);
        glProg.setDefine("STR",str);
        glProg.setDefine("SIZE","("+((double)(previousNode.WorkingTexture.mSize.x))+","+
                ((double)(previousNode.WorkingTexture.mSize.y))+")");
        glProg.useProgram(R.raw.bilateralguide);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setTexture("NoiseMap",detectblur2);
        //if(tonemaped) {
        //    glProg.setTexture("ToneMap", tonemapUpscale);
        //}
        Log.d("PostNode:" + Name, "windowSize:" + kernelsize);
        glProg.setVar("kernel",kernelsize);
        if(str > 1.f) str = 1.f;
        //if(denoiseLevel > 6.f)
        //else glProg.setVar("isofactor",str/2.5f);
        glProg.setVar("size",previousNode.WorkingTexture.mSize);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);

        /*glProg.setDefine("BSIGMA",ChromaDenoiseLevel);
        glProg.useProgram(R.raw.bilateralcolor);
        glProg.setTexture("InputBuffer",WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);*/

        //if(tonemapUpscale != null) tonemapUpscale.close();
        } else{
            Log.d(Name,"Skip");
            WorkingTexture = previousNode.WorkingTexture;
        }

        if(ChromaDenoiseLevel>=0.004) {
            GLTexture inp = WorkingTexture;
            glProg.setDefine("TRANSPOSE",2,2);
            int size = 3;
            if(ChromaDenoiseLevel > 0.04) size = 4;
            glProg.setDefine("MEDSIZE",size);
            glProg.useProgram(R.raw.hybridmedianfiltercolor);
            glProg.setTexture("InputBuffer", inp);
            WorkingTexture = basePipeline.getMain();
            glProg.drawBlocks(WorkingTexture);
            inp = WorkingTexture;
            //WorkingTexture = glUtils.ops(outp, WorkingTexture, basePipeline.getMain(), "(in1.rgba/((in1.r+in1.g+in1.b)/3.0))*((in2.r+in2.g+in2.b)/3.0)", "", 1);

        }
        /*else if(ChromaDenoiseLevel>=0.004){
            for(int i =1;i<2;i++) {
                glProg.setDefine("TRANSPOSE",1,i);
                glProg.useProgram(R.raw.hybridmedianfiltercolor);
                glProg.setTexture("InputBuffer", WorkingTexture);
                WorkingTexture = basePipeline.getMain();
                glProg.drawBlocks(WorkingTexture);
                glProg.setDefine("TRANSPOSE",i,1);
                glProg.useProgram(R.raw.hybridmedianfiltercolor);
                glProg.setTexture("InputBuffer", WorkingTexture);
                WorkingTexture = basePipeline.getMain();
                glProg.drawBlocks(WorkingTexture);
            }
        }*/
        glProg.useProgram(R.raw.reinterpolatecolors);
        glProg.setTexture("InputBuffer", WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);

        glProg.closed = true;
    }
}
