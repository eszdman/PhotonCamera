package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class MedianDown extends Node {
    public MedianDown() {
        super("", "MedianDownUpscale");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        WorkingTexture = basePipeline.getMain();
        glUtils.medianDown(previousNode.WorkingTexture,WorkingTexture,2);
        basePipeline.workSize = new Point(WorkingTexture.mSize.x/2,WorkingTexture.mSize.y/2);
        glProg.closed = true;
    }
}
