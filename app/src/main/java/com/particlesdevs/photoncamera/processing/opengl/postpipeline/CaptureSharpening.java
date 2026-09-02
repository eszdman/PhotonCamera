package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

public class CaptureSharpening extends Node {
    public CaptureSharpening() {
        super("", "CaptureSharpening");
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        Log.d(Name,"CaptureSharpening specific:"+basePipeline.mParameters.sensorSpecifics);
        if(basePipeline.mParameters.sensorSpecifics == null){
            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
            return;
        }
        float str = (0.2f + Math.min(PreferenceKeys.getSharpnessValue(), 0.0f))/0.2f;
        float size = basePipeline.mParameters.sensorSpecifics.captureSharpeningS;
        float strength = basePipeline.mParameters.sensorSpecifics.captureSharpeningIntense*str;
        if (basePipeline.mParameters.isCropped) {
            strength *= ((PostPipeline) basePipeline).upscaleSharpenScale;
        }
        glProg.setDefine("SHARPSTR",strength);
        glProg.setDefine("SHARPSIZEKER",size);
        glProg.setDefine("INSIZE",basePipeline.workSize);
        glProg.useAssetProgram("CaptureSharpening/capturesharpening");
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);

        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);

        glProg.closed = true;
    }
}
