package com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline;

import android.graphics.Point;

import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;

public class NoiseMap extends Node {
    GLTexture ResizedMap;
    public NoiseMap(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        basePipeline.glint.glprogram.setTexture("InputBuffer",previousNode.WorkingTexture);
        Point size = new Point(previousNode.WorkingTexture.mSize.x/4,previousNode.WorkingTexture.mSize.y/4);
        WorkingTexture = new GLTexture(size,previousNode.WorkingTexture.mFormat,null);
    }
}
