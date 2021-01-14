package com.particlesdevs.photoncamera.processing.opengl.nodes;

import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.render.Parameters;

public class Debug3 extends Node {

    public Debug3(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        PostPipeline rawPipeline = (PostPipeline) basePipeline;
        GLInterface glint = rawPipeline.glint;
        GLProg glProg = glint.glProgram;
        Parameters params = glint.parameters;
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
    }
}
