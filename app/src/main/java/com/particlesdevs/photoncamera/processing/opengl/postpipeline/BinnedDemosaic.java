package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

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
