package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

public class DemosaicPart2 extends Node {
    public DemosaicPart2(int rid, String name) {
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
        Log.d(Name, "Texture format:" + super.previousNode.WorkingTexture);
        glProg.setTexture("GreenBuffer", super.previousNode.WorkingTexture);
        glProg.setVar("WhiteLevel", params.whiteLevel);
        glProg.setVar("CfaPattern", params.cfaPattern);
        //glProg.servar("RawSizeX",params.rawSize.x);
        //glProg.servar("RawSizeY",params.rawSize.y);
        WorkingTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
    }
}
