package com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;
import com.eszdman.photoncamera.Render.Parameters;

public class DemosaicPart1 extends Node {
    public DemosaicPart1(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline)(basePipeline);
        GLInterface glint = basePipeline.glint;
        GLProg glProg = glint.glprogram;
        GLTexture glTexture;
        Parameters params = glint.parameters;
        glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),postPipeline.stackFrame);
        glProg.setTexture("RawBuffer",glTexture);
        glProg.setvar("WhiteLevel",params.whitelevel);
        glProg.setvar("CfaPattern",params.cfaPattern);
        //glProg.servar("RawSizeX",params.rawSize.x);
        //glProg.servar("RawSizeY",params.rawSize.y);
        super.WorkingTexture = new GLTexture(params.rawSize,new GLFormat(GLFormat.DataType.FLOAT_16),null);
    }
}
