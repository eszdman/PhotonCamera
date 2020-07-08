package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.api.Interface;

public class Sharpen extends Node {
    public Sharpen(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run(BasePipeline basePipeline) {
        startT();
        Node Previous = super.previousNode;
        GLProg glProg = GLInterface.i.glprogram;
        glProg.servar("strength", (float)Interface.i.settings.sharpness);
        glProg.setTexture("InputBuffer",Previous.WorkingTexture);
        super.WorkingTexture = new GLTexture(Previous.WorkingTexture.mSize,Previous.WorkingTexture.mFormat,null);
        endT();
    }
}
