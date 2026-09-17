package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class Demosaic2 extends Node {
    public Demosaic2() {
        super("", "Demosaic");
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
        glProg.useAssetProgram("demosaic/demosaicp0");
        glProg.setTexture("RawBuffer", glTexture);
        glProg.drawBlocks(basePipeline.getMain3());
        endT("Demosaic00");
        GLTexture outp;

        //glUtils.convertVec4(basePipeline.getMain3(),"in1");
        //glUtils.SaveProgResult(basePipeline.getMain3().mSize,"deriv");


        //Green channel
        glProg.setDefine("GRADSIZE",gradSize);
        glProg.setDefine("FUSEMIN",fuseMin);
        glProg.setDefine("FUSEMAX",fuseMax);
        glProg.setDefine("FUSESHIFT",fuseShift);
        glProg.setDefine("FUSEMPY",fuseMpy);
        glProg.setDefine("NOISES",basePipeline.noiseS);
        glProg.setDefine("NOISEO",basePipeline.noiseO);
        startT();
        glProg.useAssetProgram("demosaic/demosaicp12b");
        glProg.setTexture("RawBuffer",previousNode.WorkingTexture);
        glProg.setTexture("GradBuffer",basePipeline.getMain3());
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
        int tile = 8;
        WorkingTexture = basePipeline.getMain3();
        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("demosaic/demosaicp0ig",true);
        glProg.setTextureCompute("inTexture", glTexture,false);
        glProg.setTextureCompute("outTexture", WorkingTexture,true);
        glProg.computeAuto(WorkingTexture.mSize,1);

        WorkingTexture = basePipeline.getMain3();
        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("demosaic/demosaicp2ec",true);
        glProg.setTextureCompute("inTexture", glTexture,false);
        glProg.setTextureCompute("greenTexture", outp,false);
        glProg.setTextureCompute("igTexture", basePipeline.getMain3(),false);
        glProg.setTextureCompute("outTexture", WorkingTexture,true);
        glProg.computeAuto(WorkingTexture.mSize,1);
        //glProg.drawBlocks(WorkingTexture);
        glProg.close();
        endT("Demosaic2");
    }
}


