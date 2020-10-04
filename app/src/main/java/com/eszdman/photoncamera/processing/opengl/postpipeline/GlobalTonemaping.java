package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.R;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class GlobalTonemaping extends Node {
    public GlobalTonemaping(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Compile() {}
    private GLTexture GaussDown88(GLTexture input){
        GLInterface glint = basePipeline.glint;
        GLProg glProg = glint.glprogram;
        glProg.useProgram(R.raw.gaussdown884);
        GLTexture output = new GLTexture(new Point(input.mSize.x/8,input.mSize.y/8),input.mFormat,null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        glProg.setTexture("InputBuffer",input);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    @Override
    public void Run() {
        Node Previous = super.previousNode;
        GLInterface glint = basePipeline.glint;
        GLProg glProg = glint.glprogram;
        GLTexture lowres0 = GaussDown88(Previous.WorkingTexture);
        GLTexture lowres = GaussDown88(lowres0);
        glProg.useProgram(R.raw.globaltonemaping);
        glProg.setTexture("InputBuffer",Previous.WorkingTexture);
        glProg.setTexture("LowRes",lowres);
        glProg.setvar("insize",Previous.WorkingTexture.mSize.x,Previous.WorkingTexture.mSize.y);
        glProg.setvar("lowsize",lowres.mSize.x,lowres.mSize.y);
        glProg.setvar("str",0.015f);
        GLTexture out1 = new GLTexture(super.previousNode.WorkingTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
        WorkingTexture = new GLTexture(super.previousNode.WorkingTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
        glProg.drawBlocks(out1);
        glProg.close();
        GLTexture lowres2 = GaussDown88(lowres);
        lowres.close();
        lowres0.close();
        glProg.useProgram(R.raw.globaltonemaping);
        glProg.setTexture("InputBuffer",out1);
        glProg.setTexture("LowRes",lowres2);
        glProg.setvar("insize",WorkingTexture.mSize.x,WorkingTexture.mSize.y);
        glProg.setvar("lowsize",lowres2.mSize.x,lowres2.mSize.y);
        glProg.setvar("str",0.045f);
        glProg.drawBlocks(WorkingTexture);
        glProg.close();
    }
}
