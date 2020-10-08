package com.eszdman.photoncamera.processing.opengl.postpipeline;

import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

public class DemosaicPart1 extends Node {
    public DemosaicPart1(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline) (basePipeline);
        GLInterface glint = basePipeline.glint;
        GLProg glProg = glint.glProgram;
        GLTexture glTexture;
        Parameters params = glint.parameters;
        glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), postPipeline.stackFrame);
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setVar("WhiteLevel", params.whiteLevel);
        glProg.setVar("CfaPattern", params.cfaPattern);
        //glProg.servar("RawSizeX",params.rawSize.x);
        //glProg.servar("RawSizeY",params.rawSize.y);
        super.WorkingTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16), null);
    }
}
