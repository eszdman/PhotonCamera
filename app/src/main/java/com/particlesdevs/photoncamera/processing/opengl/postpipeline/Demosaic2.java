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

    @Override
    public void Run() {
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
        glProg.useProgram(R.raw.demosaicp12);
        glProg.setTexture("RawBuffer",previousNode.WorkingTexture);
        glProg.setTexture("GradBuffer",basePipeline.main3);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        if(basePipeline.mSettings.cfaPattern == -2) glProg.setDefine("QUAD","1");
        GLTexture prev = previousNode.WorkingTexture;
        outp = basePipeline.main1;
        glProg.drawBlocks(outp);

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
