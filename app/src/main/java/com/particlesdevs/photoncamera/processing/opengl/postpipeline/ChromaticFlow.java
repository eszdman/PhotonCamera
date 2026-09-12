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

        glProg.useAssetProgram("ChromaticFlow/chromaticgrad");
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.drawBlocks(basePipeline.getMain3());

        glProg.setDefine("SIZE",previousNode.WorkingTexture.mSize);
        glProg.useAssetProgram("ChromaticFlow/chromaticcomp");
        glProg.setTexture("DiffBuffer",basePipeline.getMain3());
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
