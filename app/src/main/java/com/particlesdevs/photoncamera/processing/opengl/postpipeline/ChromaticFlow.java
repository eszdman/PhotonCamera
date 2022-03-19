package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class ChromaticFlow extends Node {

    public ChromaticFlow() {
        super("", "ChromaticFlow");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {

        glProg.useAssetProgram("chromaticgrad");
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.drawBlocks(basePipeline.main3);

        glProg.setDefine("SIZE",previousNode.WorkingTexture.mSize);
        glProg.useAssetProgram("chromaticcomp");
        glProg.setTexture("DiffBuffer",basePipeline.main3);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
