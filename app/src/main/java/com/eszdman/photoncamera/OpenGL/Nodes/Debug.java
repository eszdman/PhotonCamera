package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.RawPipeline.RawPipeline;
import com.eszdman.photoncamera.Render.Parameters;

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
