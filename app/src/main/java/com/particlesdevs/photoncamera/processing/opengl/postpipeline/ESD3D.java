package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;

public class ESD3D extends Node {

    public ESD3D() {
        super(0, "ES3D");
    }

    @Override
    public void Compile() {
    }

    @Override
    public void Run() {
        GLTexture map = glUtils.medianDown(previousNode.WorkingTexture,4);
        GLTexture grad;

        if(previousNode.WorkingTexture != basePipeline.main3){
            grad = basePipeline.main3;
            WorkingTexture = basePipeline.getMain();
        }
        else {
            grad = basePipeline.getMain();
            WorkingTexture = basePipeline.main3;
        }
        glUtils.ConvDiff(previousNode.WorkingTexture, grad, 0.f);




        {
            Log.d(Name, "NoiseS:" + basePipeline.noiseS + ", NoiseO:" + basePipeline.noiseO);
            glProg.setDefine("NOISES", basePipeline.noiseS);
            glProg.setDefine("NOISEO", basePipeline.noiseO);
            glProg.setDefine("INSIZE", previousNode.WorkingTexture.mSize);
            glProg.useProgram(R.raw.esd3d);
            glProg.setTexture("NoiseMap", map);
            glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
            glProg.setTexture("GradBuffer", grad);
            glProg.drawBlocks(WorkingTexture);
        }
        glProg.closed = true;
        map.close();
    }
}
