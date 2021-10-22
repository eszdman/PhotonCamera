package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class ESD3D2 extends Node {

    public ESD3D2() {
        super(0, "ES3D");
    }

    @Override
    public void Compile() {
    }

    @Override
    public void Run() {
        GLTexture map = glUtils.medianDown(previousNode.WorkingTexture,4);
        GLTexture grad;

        //if(previousNode.WorkingTexture != basePipeline.main3){
            grad = basePipeline.main3;
        //}
        //else grad = basePipeline.getMain();
        glUtils.ConvDiff(previousNode.WorkingTexture, grad, 0.f);



        WorkingTexture = basePipeline.getMain();
        GLTexture temp = new GLTexture(WorkingTexture);
        glUtils.ops(WorkingTexture,map,temp,"in1-in2","",0);
        {
            Log.d(Name, "NoiseS:" + basePipeline.noiseS + ", NoiseO:" + basePipeline.noiseO);
            glProg.setDefine("NOISES", basePipeline.noiseS);
            glProg.setDefine("NOISEO", basePipeline.noiseO);
            glProg.setDefine("INSIZE", previousNode.WorkingTexture.mSize);
            glProg.useProgram(R.raw.esd3d);
            glProg.setTexture("NoiseMap", map);
            glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
            glProg.setTexture("GradBuffer", grad);
            glProg.drawBlocks(temp);
        }
        GLTexture ntex = basePipeline.getMain();
        glUtils.ops(temp,map,ntex,"in1+in2","",0);
        WorkingTexture = ntex;
        glProg.closed = true;
        map.close();
        temp.close();
    }
}
