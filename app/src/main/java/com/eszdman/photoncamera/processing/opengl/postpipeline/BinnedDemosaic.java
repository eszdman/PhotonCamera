package com.eszdman.photoncamera.processing.opengl.postpipeline;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class BinnedDemosaic extends Node {

    public BinnedDemosaic() {
        super(R.raw.demosaicpbinned, "BinnedDemosaicing");
    }

    @Override
    public void Run() {
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        for(int i =0; i<4;i++){
            basePipeline.mParameters.blackLevel[i]/=basePipeline.mParameters.whiteLevel*((PostPipeline)basePipeline).regenerationSense;
        }
        GLTexture GainMapTex = new GLTexture(basePipeline.mParameters.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4),
                FloatBuffer.wrap(basePipeline.mParameters.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setVar("blackLevel",basePipeline.mParameters.blackLevel);
        glProg.setTexture("GainMap",GainMapTex);
        WorkingTexture = basePipeline.main3;
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
        GainMapTex.close();
    }
}
