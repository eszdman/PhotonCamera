package com.eszdman.photoncamera.processing.opengl.nodes;

import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.rawpipeline.RawPipeline;
import com.eszdman.photoncamera.processing.render.Parameters;

public class Debug extends Node {

    public Debug(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run() {
        RawPipeline rawPipeline = (RawPipeline)basePipeline;
        GLInterface glint = rawPipeline.glint;
        GLProg glProg = glint.glprogram;
        Parameters params = glint.parameters;
        GLTexture glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),rawPipeline.images.get(0));
        glProg.setTexture("InputBuffer",glTexture);
        WorkingTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),rawPipeline.images.get(0));
    }
}
