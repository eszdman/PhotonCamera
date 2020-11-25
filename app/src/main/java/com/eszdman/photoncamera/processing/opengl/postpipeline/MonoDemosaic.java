package com.eszdman.photoncamera.processing.opengl.postpipeline;

import com.eszdman.photoncamera.processing.opengl.GLConst;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

public class MonoDemosaic extends Node {
    public MonoDemosaic(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline) (basePipeline);
        GLInterface glint = basePipeline.glint;
        GLProg glProg = glint.glProgram;
        GLTexture glTexture;
        Parameters params = glint.parameters;
        //glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), postPipeline.stackFrame);
        glTexture = previousNode.WorkingTexture;
        glProg.setTexture("RawBuffer", glTexture);
        WorkingTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim));
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
