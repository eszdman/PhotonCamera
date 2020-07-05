package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.Render.Parameters;

public class DemosaicPart2 extends Node {
    public DemosaicPart2(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run(BasePipeline basePipeline) {
        startT();
        GLProg glProg = GLInterface.i.glprogram;
        GLTexture glTexture;
        Parameters params = GLInterface.i.parameters;
        glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),GLInterface.i.inputRaw);
        glProg.setTexture("RawBuffer",glTexture);
        glProg.setTexture("GreenBuffer",super.previousNode.WorkingTexture);
        //glProg.servar("RawSizeX",params.rawSize.x);
        //glProg.servar("RawSizeY",params.rawSize.y);
        super.WorkingTexture = new GLTexture(params.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_16,4),null);
        glProg.drawBlocks(super.WorkingTexture);
        endT();
    }
}
