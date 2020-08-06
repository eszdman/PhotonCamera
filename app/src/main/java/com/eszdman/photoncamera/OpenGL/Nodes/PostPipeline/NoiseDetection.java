package com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;

public class NoiseDetection extends Node {
    public NoiseDetection(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline)super.basePipeline;
        GLProg glProg = basePipeline.glint.glprogram;
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        WorkingTexture = new GLTexture(previousNode.WorkingTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_32),null);
    }
}
