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
        Parameters params = glInt.parameters;
        for(int i =0; i<4;i++){
            params.blackLevel[i]/=params.whiteLevel*((PostPipeline)basePipeline).regenerationSense;
        }
        GLTexture GainMapTex = new GLTexture(params.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4), FloatBuffer.wrap(params.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setVar("blackLevel",params.blackLevel);
        glProg.setTexture("GainMap",GainMapTex);
        WorkingTexture = basePipeline.main3;
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
        GainMapTex.close();
    }
}
