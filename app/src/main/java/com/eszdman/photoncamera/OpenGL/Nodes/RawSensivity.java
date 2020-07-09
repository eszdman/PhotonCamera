package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.Render.Parameters;

public class RawSensivity extends Node {

    public RawSensivity(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run() {
        RawPipeline rawPipeline = (RawPipeline)basePipeline;
        startT();
        GLInterface glint = rawPipeline.glint;
        GLProg glProg = glint.glprogram;
        Parameters params = glint.parameters;
        GLTexture glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),rawPipeline.rawInput);
        glProg.setTexture("RawBuffer",glTexture);
        glProg.servar("PostRawSensivity",rawPipeline.sensivity);
        endT();
    }
}
