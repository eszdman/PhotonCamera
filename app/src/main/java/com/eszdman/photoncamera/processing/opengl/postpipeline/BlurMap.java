package com.eszdman.photoncamera.processing.opengl.postpipeline;

import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class BlurMap extends Node {
    GLTexture ResizedBlurredMap;

    public BlurMap(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        basePipeline.glint.glProgram.setTexture("InputBuffer", previousNode.WorkingTexture);
        WorkingTexture = previousNode.previousNode.previousNode.WorkingTexture;
        ResizedBlurredMap = new GLTexture(previousNode.WorkingTexture.mSize, previousNode.WorkingTexture.mFormat, null, GL_LINEAR, GL_CLAMP_TO_EDGE);
    }

    @Override
    public GLTexture GetProgTex() {
        ((PostPipeline) basePipeline).noiseMap = ResizedBlurredMap;
        return ResizedBlurredMap;
    }
}
