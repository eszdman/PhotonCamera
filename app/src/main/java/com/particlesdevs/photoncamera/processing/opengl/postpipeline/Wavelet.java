package com.particlesdevs.photoncamera.processing.opengl.postpipeline;


import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

import static android.opengl.GLES31.GL_READ_WRITE;

public class Wavelet extends Node {

    public Wavelet() {
        super(0, "Wavelet");
    }

    @Override
    public void Compile() {}
    GLTexture waveletRT;
    GLTexture input;
    int rescaling = 1;
    int waves = 4;
    int tile = 8;
    int waveTile = 3;
    void Wavelet2(){
        //GLTexture input = previousNode.WorkingTexture;
        for(int i =0; i<waves;i++){
            glProg.setLayout(tile,tile,1);
            glProg.setDefine("TILE",waveTile);
            glProg.setDefine("RESCALING",rescaling);
            glProg.setDefine("OFFSET",0,0);
            glProg.setDefine("OUTSET",basePipeline.mParameters.rawSize);
            glProg.useProgram(R.raw.wavelet2,true);
            glProg.setTextureCompute("inTexture",input,false);
            glProg.setTextureCompute("outTexture", waveletRT,true);
            glProg.computeManual(input.mSize.x/(rescaling*tile*waveTile) + 1,input.mSize.y/(rescaling*tile*waveTile) + 1,1);
            Log.d(Name,"Downscale:"+rescaling);
            rescaling*=waveTile;
            input = waveletRT;
            WorkingTexture = waveletRT;
        }
    }
    void UndoWavelet2() {
        //GLTexture input = WorkingTexture;
        rescaling /= waveTile;
        for (int i = waves - 1; i >= 0; i--) {
            glProg.setLayout(tile, tile, 1);
            glProg.setDefine("TILE",waveTile);
            glProg.setDefine("RESCALING", rescaling);
            glProg.setDefine("OFFSET", 0, 0);
            glProg.setDefine("OUTSET", basePipeline.mParameters.rawSize);
            glProg.setDefine("NOISEO",basePipeline.noiseO);
            glProg.setDefine("NOISES",basePipeline.noiseS);
            glProg.useProgram(R.raw.waveletinv2, true);
            glProg.setTextureCompute("inTexture", input, false);
            glProg.setTextureCompute("outTexture", waveletRT, true);
            glProg.computeManual(input.mSize.x / (rescaling*tile*waveTile) + 1, input.mSize.y / (rescaling*tile*waveTile) + 1, 1);
            Log.d(Name, "Downscale2:" + rescaling);
            rescaling /= waveTile;
            input = waveletRT;
            WorkingTexture = waveletRT;
        }
    }
    void TransFormColors(){
        glProg.setDefine("OUTSET", basePipeline.mParameters.rawSize);
        glProg.setLayout(tile, tile, 1);
        glProg.useProgram(R.raw.tocol, true);
        glProg.setTextureCompute("inTexture", previousNode.WorkingTexture, false);
        input = waveletRT;
        glProg.setTextureCompute("outTexture", input, true);
        glProg.computeAuto(basePipeline.mParameters.rawSize, 1);
    }
    void TransFormImage(){
        glProg.setDefine("OUTSET", basePipeline.mParameters.rawSize);
        glProg.setLayout(tile, tile, 1);
        glProg.useProgram(R.raw.toimg, true);
        glProg.setTextureCompute("inTexture", previousNode.WorkingTexture, false);
        glProg.setTextureCompute("colTexture", waveletRT, false);
        glProg.setTextureCompute("outTexture", waveletRT, true);
        glProg.computeAuto(basePipeline.mParameters.rawSize, 1);
    }
    @Override
    public void Run() {
        waves = (int)(Math.log10(basePipeline.mParameters.rawSize.x)/Math.log10(waveTile))-2;
        if(waves <= 0) waves = 1;
        input = previousNode.WorkingTexture;
        waveletRT = basePipeline.getMain();

        TransFormColors();

        Wavelet2();
        UndoWavelet2();

        TransFormImage();
        glProg.closed = true;
    }
}
