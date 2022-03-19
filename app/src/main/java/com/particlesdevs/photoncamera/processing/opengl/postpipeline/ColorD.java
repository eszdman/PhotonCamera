package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class ColorD extends Node {
    Point transposing;
    int size;
    public ColorD(Point transpose, int size, String name, String original) {
        super(original, name);
        transposing = transpose;
        this.size = size;
    }

    @Override
    public void BeforeCompile() {
        glProg.setDefine("NOISES", basePipeline.noiseS);
        glProg.setDefine("NOISEO", basePipeline.noiseO);
        glProg.setDefine("TRANSPOSE",transposing);
        glProg.setDefine("SIZE",size);
    }

    @Override
    public void Run() {
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
