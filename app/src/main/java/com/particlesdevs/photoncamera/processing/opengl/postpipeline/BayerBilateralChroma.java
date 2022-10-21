package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;

public class BayerBilateralChroma extends Node {

    public BayerBilateralChroma() {
        super("", "Denoise");
    }

    @Override
    public void Compile() {
    }

    @Override
    public void Run() {
        NoiseModeler modeler = basePipeline.mParameters.noiseModeler;
        float noiseS = modeler.computeModel[0].first.floatValue()+
                modeler.computeModel[1].first.floatValue()+
                modeler.computeModel[2].first.floatValue();
        float noiseO = modeler.computeModel[0].second.floatValue()+
                modeler.computeModel[1].second.floatValue()+
                modeler.computeModel[2].second.floatValue();
        noiseS/=3.f;
        noiseO/=3.f;
        Log.d(Name,"NoiseS:"+noiseS+", NoiseO:"+noiseO);
        glProg.setDefine("NOISES",noiseS);
        glProg.setDefine("NOISEO",noiseO);
        glProg.setDefine("INTENSE", (float) basePipeline.mSettings.noiseRstr);
        glProg.setDefine("INSIZE",previousNode.WorkingTexture.mSize);
        glProg.useAssetProgram("bayerbilateralchroma");
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        WorkingTexture = basePipeline.getMain();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }
}
