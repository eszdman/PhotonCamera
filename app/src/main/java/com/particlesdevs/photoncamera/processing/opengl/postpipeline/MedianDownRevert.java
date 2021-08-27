package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class MedianDownRevert extends Node {
    public MedianDownRevert() {
        super(0, "MedianDownUpscale");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        WorkingTexture = basePipeline.getMain();
        glUtils.interpolate(previousNode.WorkingTexture,WorkingTexture,2.0,WorkingTexture.mSize);
        basePipeline.workSize = new Point(WorkingTexture.mSize.x,WorkingTexture.mSize.y);
        glProg.closed = true;
    }
}
