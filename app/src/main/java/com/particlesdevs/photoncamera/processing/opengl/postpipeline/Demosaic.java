package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class Demosaic extends Node {
    public Demosaic() {
        super(0, "Demosaic");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        GLTexture glTexture;
        glTexture = previousNode.WorkingTexture;
        glProg.useProgram(R.raw.demosaicp1);
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        if(basePipeline.mSettings.cfaPattern == -2) glProg.setDefine("QUAD","1");
        glProg.drawBlocks(basePipeline.main1);
        GLTexture outp = previousNode.WorkingTexture;


        //Green Channel guided denoising, hotpixel removing
        glProg.useProgram(R.raw.denoisebygreen);
        glProg.setTexture("RawBuffer",previousNode.WorkingTexture);
        glProg.setTexture("GreenBuffer",basePipeline.main1);
        //glProg.setVar("CfaPattern", params.cfaPattern);
        GLTexture prev = previousNode.WorkingTexture;
        outp = basePipeline.main2;
        glProg.drawBlocks(outp);

        glProg.useProgram(R.raw.demosaicp2);
        glProg.setTexture("RawBuffer", outp);
        glProg.setTexture("GreenBuffer", basePipeline.main1);
        glProg.setVar("whitePoint",basePipeline.mParameters.whitePoint);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        WorkingTexture = basePipeline.main3;
        glProg.drawBlocks(WorkingTexture);
        glProg.close();
    }
}
