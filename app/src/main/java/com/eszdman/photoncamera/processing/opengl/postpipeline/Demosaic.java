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
        glTexture = previousNode.WorkingTexture;
        glProg.useProgram(R.raw.demosaicp1);
        glProg.setTexture("RawBuffer", glTexture);
        glProg.setVar("CfaPattern", params.cfaPattern);
        //GLTexture green = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16));
        glProg.drawBlocks(basePipeline.main1);

        //Green Channel guided denoising
        glProg.useProgram(R.raw.denoisebygreen);
        glProg.setTexture("RawBuffer",previousNode.WorkingTexture);
        glProg.setTexture("GreenBuffer",basePipeline.main1);
        glProg.drawBlocks(basePipeline.main2);

        glProg.useProgram(R.raw.demosaicp2);
        GLTexture GainMapTex = new GLTexture(params.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4), FloatBuffer.wrap(params.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setTexture("RawBuffer", basePipeline.main2);
        glProg.setTexture("GreenBuffer", basePipeline.main1);
        glProg.setVar("whitePoint",params.whitePoint);
        glProg.setTexture("GainMap",GainMapTex);
        glProg.setVar("CfaPattern", params.cfaPattern);
        glProg.setVar("RawSize",params.rawSize);
        for(int i =0; i<4;i++){
            params.blackLevel[i]/=params.whiteLevel;
        }
        glProg.setVar("blackLevel",params.blackLevel);
        WorkingTexture = basePipeline.main3;
        glProg.drawBlocks(WorkingTexture);
        GainMapTex.close();
        glProg.close();
    }
}
