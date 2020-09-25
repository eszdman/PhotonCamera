package com.eszdman.photoncamera.processing.opengl.nodes;

import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.postpipeline.PostPipeline;
import com.eszdman.photoncamera.processing.render.Parameters;

public class Debug2 extends Node {

    public Debug2(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run() {
        PostPipeline rawPipeline = (PostPipeline)basePipeline;
        GLInterface glint = rawPipeline.glint;
        GLProg glProg = glint.glprogram;
        Parameters params = glint.parameters;
        GLTexture glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),rawPipeline.stackFrame);
        glProg.setTexture("InputBuffer",glTexture);
    }
}
