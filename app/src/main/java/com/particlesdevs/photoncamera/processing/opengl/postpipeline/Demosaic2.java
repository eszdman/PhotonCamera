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
    float gradSize = 1.5f;
    float fuseMin = 0.f;
    float fuseMax = 1.f;
    float fuseShift = -0.5f;
    float fuseMpy = 6.0f;
    @Override
    public void Run() {
        startT();
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
        glProg.drawBlocks(basePipeline.main3);
        endT("Demosaic00");
        GLTexture outp;

        //glUtils.convertVec4(basePipeline.main3,"in1");
        //glUtils.SaveProgResult(basePipeline.main3.mSize,"deriv");


        //Green channel
        glProg.setDefine("GRADSIZE",gradSize);
        glProg.setDefine("FUSEMIN",fuseMin);
        glProg.setDefine("FUSEMAX",fuseMax);
        glProg.setDefine("FUSESHIFT",fuseShift);
        glProg.setDefine("FUSEMPY",fuseMpy);
        glProg.setDefine("NOISES",basePipeline.noiseS);
        glProg.setDefine("NOISEO",basePipeline.noiseO);
        startT();
        glProg.useProgram(R.raw.demosaicp12);
        glProg.setTexture("RawBuffer",previousNode.WorkingTexture);
        glProg.setTexture("GradBuffer",basePipeline.main3);
        if(basePipeline.mSettings.cfaPattern == -2) glProg.setDefine("QUAD","1");
        GLTexture prev = previousNode.WorkingTexture;
        outp = basePipeline.getMain();
        /*if(basePipeline.main1 == previousNode.WorkingTexture){
            outp = basePipeline.main2;
        }*/
        glProg.drawBlocks(outp);
        endT("Demosaic12");

        //Colour channels
        startT();
        glProg.useProgram(R.raw.demosaicp2);
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setTexture("GreenBuffer", outp);
        WorkingTexture = basePipeline.main3;
        glProg.drawBlocks(WorkingTexture);
        glProg.close();
        endT("Demosaic2");
    }
}


