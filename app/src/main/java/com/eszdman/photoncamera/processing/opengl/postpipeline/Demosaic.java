package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLConst;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class Demosaic extends Node {
    public Demosaic(String name) {
        super(0, name);
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline) (basePipeline);
        GLTexture glTexture;
        Parameters params = glInt.parameters;
        glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), postPipeline.stackFrame);
        glProg.useProgram(R.raw.demosaicp1);
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setVar("WhiteLevel", params.whiteLevel);
        glProg.setVar("CfaPattern", params.cfaPattern);
        GLTexture green = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16));
        glProg.drawBlocks(green);
        glProg.useProgram(R.raw.demosaicp2);
        GLTexture GainMapTex = new GLTexture(params.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4), FloatBuffer.wrap(params.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setTexture("GreenBuffer", green);
        glProg.setTexture("GainMap",GainMapTex);
        glProg.setVar("WhiteLevel", params.whiteLevel);
        glProg.setVar("CfaPattern", params.cfaPattern);
        glProg.setVar("RawSize",params.rawSize);
        for(int i =0; i<4;i++){
            params.blackLevel[i]/=params.whiteLevel;
        }
        glProg.setVar("blackLevel",params.blackLevel);
        WorkingTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim));
        basePipeline.main1 = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim));
        basePipeline.main2 = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim));
        //WorkingTexture = basePipeline.main2;
        glProg.drawBlocks(WorkingTexture);
        green.close();
        GainMapTex.close();
        glTexture.close();
        glProg.close();
    }
}
