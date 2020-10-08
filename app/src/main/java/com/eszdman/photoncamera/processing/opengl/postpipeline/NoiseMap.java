package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

public class NoiseMap extends Node {
    GLTexture ResizedMap;

    public NoiseMap(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        basePipeline.glint.glProgram.setTexture("InputBuffer", previousNode.WorkingTexture);
        Point size = new Point(previousNode.WorkingTexture.mSize.x / 4, previousNode.WorkingTexture.mSize.y / 4);
        WorkingTexture = new GLTexture(size, previousNode.WorkingTexture.mFormat, null);
    }
}
