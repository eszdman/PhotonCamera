package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class HotPixelFilter extends Node {


    public HotPixelFilter() {
        super(0, "HotPixelFilter");
    }

    @Override
    public void Compile() {}

    int tile = 8;
    @Override
    public void Run() {
        glProg.setLayout(tile,tile,1);
        int tileSize = 7;
        glProg.setDefine("OUTSET",previousNode.WorkingTexture.mSize);
        glProg.setDefine("TILE",tileSize);
        glProg.setDefine("NOISEO",basePipeline.noiseO);
        glProg.setDefine("NOISES",basePipeline.noiseS);
        glProg.setDefine("IMPULSE",10.0f);
        glProg.useProgram(R.raw.hotpixels,true);
        glProg.setTextureCompute("inTexture",previousNode.WorkingTexture,false);
        WorkingTexture = previousNode.WorkingTexture;
        glProg.setTextureCompute("outTexture",WorkingTexture,true);
        for(int i =0; i<5;i++)
        glProg.computeManual(WorkingTexture.mSize.x/(8*tileSize),WorkingTexture.mSize.y/(8*tileSize),3);
        glProg.closed = true;
    }
}
