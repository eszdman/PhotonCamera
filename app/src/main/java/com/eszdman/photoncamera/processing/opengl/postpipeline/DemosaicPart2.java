package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

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
        GLTexture GainMapTex = new GLTexture(params.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4), FloatBuffer.wrap(params.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setTexture("RawBuffer", glTexture);
        Log.d(Name, "Texture format:" + super.previousNode.WorkingTexture);
        glProg.setTexture("GreenBuffer", super.previousNode.WorkingTexture);
        glProg.setTexture("GainMap",GainMapTex);
        glProg.setVar("WhiteLevel", params.whiteLevel);
        glProg.setVar("CfaPattern", params.cfaPattern);
        glProg.setVar("RawSize",params.rawSize);
        for(int i =0; i<4;i++){
            params.blackLevel[i]/=params.whiteLevel;
        }
        glProg.setVar("blackLevel",params.blackLevel);
        //glProg.servar("RawSizeX",params.rawSize.x);
        //glProg.servar("RawSizeY",params.rawSize.y);
        WorkingTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
    }
}
