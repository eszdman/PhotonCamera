package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLConst;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.GLUtils;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

public class ExposureFusionFast extends Node {

    public ExposureFusionFast(String name) {
        super(0, name);
    }
    @Override
    public void Compile() {}

    @Override
    public void AfterRun() {
        previousNode.WorkingTexture.close();
    }

    GLTexture expose(GLTexture in, float str){
        glProg.useProgram(R.raw.expose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        if(basePipeline.main1 == null) basePipeline.main1 = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,GLConst.WorkDim));
        glProg.drawBlocks(basePipeline.main1);
        glProg.close();
        return basePipeline.main1;
    }
    GLTexture expose2(GLTexture in, float str){
        glProg.useProgram(R.raw.expose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        if(basePipeline.main2 == null) basePipeline.main2 = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,GLConst.WorkDim));
        glProg.drawBlocks(basePipeline.main2);
        glProg.close();
        return basePipeline.main2;
    }
    GLTexture unexpose(GLTexture in,float str){
        glProg.useProgram(R.raw.unexpose);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("factor", str);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
        if(basePipeline.main2 == null) basePipeline.main2 = new GLTexture(in.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,GLConst.WorkDim),null);
        glProg.drawBlocks(basePipeline.main2);
        glProg.close();
        return basePipeline.main2;
    }

    @Override
    public void Run() {
        GLTexture in = previousNode.WorkingTexture;
        double compressor = (PhotonCamera.getSettings().compressor);
        if(PhotonCamera.getManualMode().getCurrentExposureValue() != 0 && PhotonCamera.getManualMode().getCurrentISOValue() != 0) compressor = 1.f;
        int perlevel = 4;
        int levelcount = (int)(Math.log10(previousNode.WorkingTexture.mSize.x)/Math.log10(perlevel))+1;
        if(levelcount <= 0) levelcount = 2;
        Log.d(Name,"levelCount:"+levelcount);
        float factorMid = 1.f;
        float factorHigh = (float)(1.f)*3.5f;
        GLUtils.Pyramid normalExpo = glUtils.createPyramid(levelcount,0, in);
        glProg.useProgram(R.raw.fusionfast);
        glProg.setVar("useUpsampled",0);
        int ind = normalExpo.gauss.length - 1;
        GLTexture wip = new GLTexture(normalExpo.gauss[ind]);
        glProg.setTexture("normalExpo",normalExpo.gauss[ind]);
        glProg.setTexture("normalExpoDiff",normalExpo.gauss[ind]);

        glProg.setVar("factorMid", factorMid);
        glProg.setVar("factorHigh", factorHigh);
        glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);

        glProg.setVar("upscaleIn",wip.mSize);

        glProg.drawBlocks(wip);
        for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
                GLTexture upsampleWip = wip;
                Log.d(Name,"upsampleWip:"+upsampleWip.mSize);
                glProg.useProgram(R.raw.fusionfast);

                glProg.setTexture("upsampled", upsampleWip);
                glProg.setVar("useUpsampled", 1);
                glProg.setVar("upscaleIn",normalExpo.sizes[i]);
                glProg.setVar("factorMid", factorMid);
                glProg.setVar("factorHigh", factorHigh);
                glProg.setVar("neutralPoint", PhotonCamera.getParameters().whitePoint);
                // We can discard the previous work in progress merge.
                if(normalExpo.laplace[i].mSize.equals(basePipeline.main1.mSize)){
                wip = basePipeline.main1;
                } else {
                    wip = new GLTexture(normalExpo.laplace[i]);
                }
                //wip = new GLTexture(normalExpo.laplace[i]);


                glProg.setTexture("normalExpo", normalExpo.gauss[i]);
                glProg.setTexture("normalExpoDiff", normalExpo.laplace[i]);

                glProg.drawBlocks(wip);
                //glUtils.SaveProgResult(wip.mSize,"ExposureFusion"+i);
                upsampleWip.close();
                if(!normalExpo.gauss[i].mSize.equals(basePipeline.main1.mSize)) {
                    normalExpo.gauss[i].close();
                    //highExpo.gauss[i].close();
                }
                normalExpo.laplace[i].close();
                //highExpo.laplace[i].close();

        }
        //previousNode.WorkingTexture.close();
        WorkingTexture = unexpose(wip,(float) PhotonCamera.getSettings().gain);
        Log.d(Name,"Output Size:"+wip.mSize);
        //wip.close();
        glProg.closed = true;
        //highExpo.releasePyramid();
        //normalExpo.releasePyramid();
    }
}
