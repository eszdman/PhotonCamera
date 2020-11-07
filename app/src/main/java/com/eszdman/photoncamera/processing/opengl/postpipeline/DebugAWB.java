package com.eszdman.photoncamera.processing.opengl.postpipeline;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

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
