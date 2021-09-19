package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class CaptureSharpening extends Node {
    public CaptureSharpening() {
        super(0, "CaptureSharpening");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        if(basePipeline.mParameters.sensorSpecifics == null){
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        float size = basePipeline.mParameters.sensorSpecifics.captureSharpeningS;
        float strength = basePipeline.mParameters.sensorSpecifics.captureSharpeningIntense;

        glProg.setDefine("SHARPDIR",1);
        glProg.useProgram(R.raw.capturesharpening);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setVar("size",size);
        glProg.setVar("strength",strength);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);

        glProg.closed = true;
    }
}
