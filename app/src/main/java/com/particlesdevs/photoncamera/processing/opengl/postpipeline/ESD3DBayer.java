package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class ESD3DBayer extends Node {

    public ESD3DBayer() {
        super(0, "ES3D");
    }

    @Override
    public void Compile() {
    }

    @Override
    public void Run() {
        startT();
        GLTexture map = glUtils.medianDown(previousNode.WorkingTexture,4);
        endT("MedianDown");
        startT();
        GLTexture grad;
        /*
        if(previousNode.WorkingTexture != basePipeline.main3){
            grad = basePipeline.main3;
            WorkingTexture = basePipeline.getMain();
        }
        else {
            grad = basePipeline.getMain();
            WorkingTexture = basePipeline.main3;
        }*/
        WorkingTexture = basePipeline.getMain();
        grad = basePipeline.main3;
        glProg.setDefine("INSIZE",basePipeline.workSize);
        glProg.useProgram(R.raw.diffbayer);
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        glProg.drawBlocks(grad);
        endT("Differentiate");
        //glUtils.ConvDiff(previousNode.WorkingTexture, grad, 0.f);



        startT();
        {
            Log.d(Name, "NoiseS:" + basePipeline.noiseS + ", NoiseO:" + basePipeline.noiseO);
            glProg.setDefine("NOISES", basePipeline.noiseS);
            glProg.setDefine("NOISEO", basePipeline.noiseO);
            glProg.setDefine("INSIZE", previousNode.WorkingTexture.mSize);
            glProg.useProgram(R.raw.esd3dbayer);
            glProg.setTexture("NoiseMap", map);
            glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
            glProg.setTexture("GradBuffer", grad);
            glProg.drawBlocks(WorkingTexture);
        }
        endT("ES3D");
        glProg.closed = true;
        map.close();
    }
}
