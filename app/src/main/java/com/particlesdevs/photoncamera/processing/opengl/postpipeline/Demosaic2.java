package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class Demosaic2 extends Node {
    public Demosaic2() {
        super(0, "Demosaic");
    }

    @Override
    public void Compile() {}
    float gradSize = 1.75f;
    float fuseMin = 0.f;
    float fuseMax = 1.f;
    float fuseShift = -0.15f;
    float fuseMpy = 1.15f;
    @Override
    public void Run() {
        gradSize = getTuning("GradSize",gradSize);
        fuseMin = getTuning("FuseMin",fuseMin);
        fuseMax = getTuning("FuseMax",fuseMax);
        fuseShift = getTuning("FuseShift",fuseShift);
        fuseMpy = getTuning("FuseMpy",fuseMpy);
        GLTexture glTexture;
        glTexture = previousNode.WorkingTexture;
        //Gradients
        glProg.useProgram(R.raw.demosaicp0);
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        glProg.drawBlocks(basePipeline.main3);
        GLTexture outp;
        //glUtils.convertVec4(basePipeline.main3,"in1");
        //glUtils.SaveProgResult(basePipeline.main3.mSize,"deriv");


        //Green channel
        glProg.setDefine("GRADSIZE",gradSize);
        glProg.setDefine("FUSEMIN",fuseMin);
        glProg.setDefine("FUSEMAX",fuseMax);
        glProg.setDefine("FUSESHIFT",fuseShift);
        glProg.setDefine("FUSEMPY",fuseMpy);
        glProg.useProgram(R.raw.demosaicp12);
        glProg.setTexture("RawBuffer",previousNode.WorkingTexture);
        glProg.setTexture("GradBuffer",basePipeline.main3);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        if(basePipeline.mSettings.cfaPattern == -2) glProg.setDefine("QUAD","1");
        GLTexture prev = previousNode.WorkingTexture;
        outp = basePipeline.main1;
        if(basePipeline.main1 == previousNode.WorkingTexture){
            outp = basePipeline.main2;
        }
        glProg.drawBlocks(outp);

        //Colour channels
        glProg.useProgram(R.raw.demosaicp2);
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setTexture("GreenBuffer", outp);
        glProg.setVar("whitePoint",basePipeline.mParameters.whitePoint);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        WorkingTexture = basePipeline.main3;
        glProg.drawBlocks(WorkingTexture);
        glProg.close();
    }
}
