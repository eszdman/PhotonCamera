package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class ImpulsePixelFilter extends Node {


    public ImpulsePixelFilter() {
        super("", "HotPixelFilter");
    }

    @Override
    public void Compile() {}
    void fixImpulse(String color){
        glProg.setLayout(tile,tile,1);
        int tileSize = 7;
        glProg.setDefine("OUTSET",previousNode.WorkingTexture.mSize);
        glProg.setDefine("TILE",tileSize);
        glProg.setDefine("NOISEO",basePipeline.noiseO);
        glProg.setDefine("NOISES",basePipeline.noiseS);
        glProg.setDefine("IMPULSE",8.0f);
        glProg.setDefine("COLOR",color);
        glProg.useAssetProgram("impixels",true);
        glProg.setTextureCompute("inTexture",previousNode.WorkingTexture,false);
        WorkingTexture = previousNode.WorkingTexture;
        glProg.setTextureCompute("outTexture",WorkingTexture,true);
        for(int i =0; i<3;i++)
            glProg.computeManual(WorkingTexture.mSize.x/(8*tileSize),WorkingTexture.mSize.y/(8*tileSize),3);
    }
    int tile = 16;
    @Override
    public void Run() {
        fixImpulse("r");
        fixImpulse("g");
        fixImpulse("b");
        glProg.closed = true;
    }
}
