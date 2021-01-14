package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class DebugAWB extends Node {
    public DebugAWB(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        GLProg glProg = basePipeline.glint.glProgram;
        glProg.useProgram(R.raw.applyvector);
        glProg.setVar("colorvec", 0.5f,1.f,0.3f);
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        WorkingTexture = new GLTexture(previousNode.WorkingTexture);
        glProg.drawBlocks(WorkingTexture);
        glProg.close();
    }
}
