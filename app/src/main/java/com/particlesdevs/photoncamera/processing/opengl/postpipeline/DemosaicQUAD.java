package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class DemosaicQUAD extends Node {
    public DemosaicQUAD() {
        super("", "Demosaic");
    }

    @Override
    public void Compile() {}
    float gradSize = 1.5f;
    float fuseMin = 0.f;
    float fuseMax = 1.f;
    float fuseShift = -0.3f;
    float fuseMpy = 0.0f;
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
        glProg.useAssetProgram("demosaic/demosaicp0quad");
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        glProg.drawBlocks(basePipeline.getMain3());
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
        glProg.useAssetProgram("demosaic/demosaicp12quad");
        glProg.setTexture("RawBuffer",previousNode.WorkingTexture);
        glProg.setTexture("GradBuffer",basePipeline.getMain3());
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        GLTexture prev = previousNode.WorkingTexture;
        outp = basePipeline.main1;
        if(basePipeline.main1 == previousNode.WorkingTexture){
            outp = basePipeline.main2;
        }
        glProg.drawBlocks(outp);

        //Colour channels

        glProg.useAssetProgram("demosaic/demosaicp2quad");
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setTexture("GreenBuffer", outp);
        glProg.setVar("whitePoint",basePipeline.mParameters.whitePoint);
        glProg.setVar("CfaPattern", basePipeline.mParameters.cfaPattern);
        WorkingTexture = basePipeline.getMain3();
        glProg.drawBlocks(WorkingTexture);
        glProg.close();
    }
}
