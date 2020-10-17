package com.eszdman.photoncamera.processing.opengl.postpipeline;

import com.eszdman.photoncamera.processing.opengl.nodes.Node;

public class ExposureFusion extends Node {

    public ExposureFusion(String name) {
        super(0, name);
    }
    @Override
    public void AfterRun() {
        previousNode.WorkingTexture.close();
    }
    @Override
    public void Compile() {}

    @Override
    public void Run() {

    }
}
