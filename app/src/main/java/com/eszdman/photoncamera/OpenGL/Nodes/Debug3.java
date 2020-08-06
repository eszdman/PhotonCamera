package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline.PostPipeline;
import com.eszdman.photoncamera.Render.Parameters;

public class Debug3 extends Node {

    public Debug3(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run() {
        PostPipeline rawPipeline = (PostPipeline)basePipeline;
        GLInterface glint = rawPipeline.glint;
        GLProg glProg = glint.glprogram;
        Parameters params = glint.parameters;
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
    }
}
