package com.eszdman.photoncamera.OpenGL.Nodes;

import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.Render.Parameters;

public class DebugRaw extends Node {
    public DebugRaw(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run(BasePipeline basePipeline) {
        startT();
        GLProg glProg = GLInterface.i.glprogram;
        Parameters params = GLInterface.i.parameters;
        //Log.d(super.Name,"Previous Node:"+super.previousNode.Name);
        //Log.d(super.Name,"Previous Texture:"+super.previousNode.WorkingTexture.mSize);
        //glProg.setTexture("InputBuffer",super.previousNode.WorkingTexture);
        endT();
    }
}
