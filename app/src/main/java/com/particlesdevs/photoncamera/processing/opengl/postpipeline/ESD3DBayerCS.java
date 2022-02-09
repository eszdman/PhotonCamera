package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class ESD3DBayerCS extends Node {

    public ESD3DBayerCS() {
        super(0, "ES3D");
    }

    @Override
    public void Compile() {
    }

    @Override
    public void Run() {
        startT();
        GLTexture map = basePipeline.main4;
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
        glProg.setLayout(16,16,1);
        glProg.setDefine("INSIZE",basePipeline.workSize);
        glProg.useProgram(R.raw.diffbayercs,true);
        glProg.setTextureCompute("inTexture", previousNode.WorkingTexture,false);
        glProg.setTextureCompute("outTexture", grad,true);
        glProg.computeAuto(grad.mSize,1);
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
