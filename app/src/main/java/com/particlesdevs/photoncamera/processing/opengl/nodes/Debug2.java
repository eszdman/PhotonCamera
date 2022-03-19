package com.particlesdevs.photoncamera.processing.opengl.nodes;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.render.Parameters;

public class Debug2 extends Node {

    public Debug2(String rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        PostPipeline rawPipeline = (PostPipeline) basePipeline;
        GLInterface glint = rawPipeline.glint;
        GLProg glProg = glint.glProgram;
        Parameters params = glint.parameters;
        GLTexture glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), rawPipeline.stackFrame);
        glProg.setTexture("InputBuffer", glTexture);
    }
}
