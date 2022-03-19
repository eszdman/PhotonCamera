package com.particlesdevs.photoncamera.processing.opengl.postpipeline;


import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

import static android.opengl.GLES31.GL_READ_WRITE;

public class Wavelet extends Node {

    public Wavelet() {
        super("", "Wavelet");
    }

    @Override
    public void Compile() {}
    GLTexture waveletRT;
    GLTexture input;
    int rescaling = 1;
    int waves = 4;
    int tile = 8;
    int waveTile = 2;
    void Wavelet2(){
        //GLTexture input = previousNode.WorkingTexture;
        for(int i =0; i<waves;i++){
            glProg.setLayout(tile,tile,1);
            glProg.setDefine("TILE",waveTile);
            glProg.setDefine("RESCALING",rescaling);
            glProg.setDefine("OFFSET",0,0);
            glProg.setDefine("OUTSET",basePipeline.mParameters.rawSize);
            glProg.setDefine("SQRT2",(float)Math.sqrt(2));
            glProg.useAssetProgram("wavelet2",true);
            glProg.setTextureCompute("inTexture",input,false);
            glProg.setTextureCompute("outTexture", waveletRT,true);
            glProg.computeManual(input.mSize.x/(rescaling*tile*waveTile) + 1,input.mSize.y/(rescaling*tile*waveTile) + 1,1);
            Log.d(Name,"Downscale:"+rescaling);
            rescaling*=waveTile;
            input = waveletRT;
            WorkingTexture = waveletRT;
        }
        rescaling/=waveTile;
    }
    void Thresholding(){
        glProg.setDefine("NOISEO", basePipeline.noiseO);
        glProg.setDefine("NOISES", basePipeline.noiseS);
        glProg.setDefine("RESCALING",rescaling);
        glProg.setDefine("TILE",waveTile);
        glProg.setDefine("OUTSET",basePipeline.mParameters.rawSize);
        glProg.setLayout(tile, tile, 1);
        glProg.useAssetProgram("waveletthr", true);
        glProg.setTextureCompute("inTexture", input, false);
        glProg.setTextureCompute("outTexture", waveletRT, true);
        glProg.computeAuto(input.mSize,1);
    }
    void UndoWavelet2() {
        //GLTexture input = WorkingTexture;
        for (int i = waves - 1; i >= 0; i--) {
            glProg.setLayout(tile, tile, 1);
            glProg.setDefine("TILE",waveTile);
            glProg.setDefine("RESCALING", rescaling);
            glProg.setDefine("OFFSET", 0, 0);
            glProg.setDefine("SQRT2",(float)Math.sqrt(2));
            glProg.setDefine("OUTSET", basePipeline.mParameters.rawSize);
            glProg.setDefine("NOISEO", basePipeline.noiseO);
            glProg.setDefine("NOISES", basePipeline.noiseS);

            glProg.useAssetProgram("waveletinv2", true);
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
        glProg.useAssetProgram("tocol", true);
        glProg.setTextureCompute("inTexture", previousNode.WorkingTexture, false);
        input = basePipeline.getMain();
        glProg.setTextureCompute("outTexture", input, true);
        glProg.computeAuto(basePipeline.mParameters.rawSize, 1);
    }
    void TransFormImage(){
        glProg.setDefine("OUTSET", basePipeline.mParameters.rawSize);
        glProg.setDefine("BL",basePipeline.mParameters.blackLevel);
        glProg.setDefine("NOISEO", basePipeline.noiseO);
        glProg.setDefine("NOISES", basePipeline.noiseS);
        glProg.setLayout(tile, tile, 1);
        glProg.useAssetProgram("toimg", true);
        glProg.setTextureCompute("inTexture", previousNode.WorkingTexture, false);
        glProg.setTextureCompute("colTexture", waveletRT, false);
        WorkingTexture = basePipeline.getMain();
        glProg.setTextureCompute("outTexture", WorkingTexture, true);
        glProg.computeAuto(basePipeline.mParameters.rawSize, 1);
    }
    @Override
    public void Run() {
        waves = (int)(Math.log10(basePipeline.mParameters.rawSize.y)/Math.log10(waveTile))-4;
        //if(waves <= 0) waves = 1;
        input = previousNode.WorkingTexture;
        waveletRT = basePipeline.getMain();

        TransFormColors();

        Wavelet2();
        //Thresholding();
        UndoWavelet2();

        TransFormImage();
        glProg.closed = true;
    }
}
