package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class ChromaticFlow extends Node {

    public ChromaticFlow() {
        super(0, "ChromaticFlow");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {

        glProg.useProgram(R.raw.chromaticgrad);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.drawBlocks(basePipeline.main3);

        glProg.setDefine("SIZE",previousNode.WorkingTexture.mSize);
        glProg.useProgram(R.raw.chromaticcomp);
        glProg.setTexture("DiffBuffer",basePipeline.main3);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
