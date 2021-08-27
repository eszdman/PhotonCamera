package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class DirectedMedian extends Node {
    public DirectedMedian() {
        super(0, "DirectedMedian");
    }

    @Override
    public void Compile() {
    }

    @Override
    public void Run() {
        GLTexture grad;
        grad = basePipeline.main3;
        glUtils.ConvDiff(previousNode.WorkingTexture, grad, 0.f);
        {
            glProg.setDefine("INTENSE", (float) basePipeline.mSettings.noiseRstr);
            glProg.setDefine("INSIZE", previousNode.WorkingTexture.mSize);
            glProg.useProgram(R.raw.directedmedian);
            glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
            glProg.setTexture("GradBuffer", grad);
            WorkingTexture = basePipeline.getMain();
            glProg.drawBlocks(WorkingTexture);
        }
        glProg.closed = true;
    }
}
