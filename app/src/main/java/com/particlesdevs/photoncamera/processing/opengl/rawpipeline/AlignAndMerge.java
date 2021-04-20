package com.particlesdevs.photoncamera.processing.opengl.rawpipeline;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.processor.ProcessorBase;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.util.Utilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;

public class AlignAndMerge extends Node {
    Point rawSize;
    GLProg glProg;

    public AlignAndMerge() {
        super(0, "AlignAndMerge");
    }

    @Override
    public void Compile() {}

    float[] DH = new float[]
        {
            -0.3f,0.f,0.3f,
            -0.1f,  0.f,0.1f,
            -0.3f,0.f,0.3f
        };
    float[] DV = new float[]
        {
                0.3f, 0.1f, 0.3f,
              0.f,   0.f, 0.f,
             -0.3f,-0.1f,-0.3f
        };
    /*float[] DV = new float[]
            {
                    -0.1f,0.f,0.1f,
                   -0.7f,  0.0f,0.7f,
                    -0.1f,0.f,0.1f
            };
    float[] DH = new float[]
            {
                    0.1f, 0.7f, 0.1f,
                    0.f,  0.0f, 0.f,
                    -0.1f,-0.7f,-0.1f
            };*/

    private void CorrectedRaw(GLTexture out, int number) {
        float bl = Math.min(Math.min(Math.min(PhotonCamera.getParameters().blackLevel[0],PhotonCamera.getParameters().blackLevel[1]),
                PhotonCamera.getParameters().blackLevel[2]),PhotonCamera.getParameters().blackLevel[3]);    
        //glProg.setDefine("BL",bl);
        glProg.useProgram(R.raw.precorrection);
        GLTexture inraw = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), images.get(number).buffer);
        glProg.setTexture("InputBuffer",inraw);
        glProg.setVar("WhiteLevel",(float)PhotonCamera.getParameters().whiteLevel);
        glProg.drawBlocks(out);
        inraw.close();
    }

    private void BoxDown22(GLTexture input,GLTexture out,GLTexture outH,GLTexture outV) {
        glProg.useProgram(R.raw.boxdown22);
        glProg.setTexture("InputBuffer", input);
        glProg.setTexture("GainMap", GainMap);
        glProg.setVar("CfaPattern", PhotonCamera.getParameters().cfaPattern);
        glProg.drawBlocks(out);
        //glUtils.SaveProgResult(output.mSize,"boxdown");
        //glProg.close();
        //GLTexture median = glUtils.blur(output,5.0);
        //GLTexture laplaced = glUtils.ops(median,output,"in2.rgb,3.0*(in1.a-in2.a)");
        //median.close();

        //glUtils.median(basePipeline.main3,out,new Point(1,1));

        //GLTexture median = glUtils.blur(output,1.5);


        //glUtils.median(basePipeline.main3, out,new Point(1,1));

        glUtils.ConvDiff(out,outV,tileSize/3,true,false);
        glUtils.ConvDiff(out,outH,tileSize/3,false,false);

        //glUtils.SaveProgResult(output.mSize,"aligninput");
        //GLTexture laplaced = glUtils.ops(blur,output,"-");
        //output.close();
        //return laplaced;
    }
    private void GaussDown44(GLTexture input,GLTexture out,GLTexture outH,GLTexture outV,boolean median) {
        if(median) {
            if(input.mSize.x+input.mSize.y > 9) {
                glUtils.interpolate(input, basePipeline.main3, 1.0 / 4.0);
                glUtils.median(basePipeline.main3, out, new Point(1, 1));
            } else {
                glUtils.median(input, out, new Point(1, 1));
            }
        } else{
            glUtils.interpolate(input, out, 1.0 / 4.0);
        }
        glUtils.ConvDiff(out,outV,tileSize/3,true,false);
        glUtils.ConvDiff(out,outH,tileSize/3,false,false);
        //glUtils.median(basePipeline.main3,out, new Point(1,1));
    }

    private GLTexture LaplacDown44(GLTexture input) {
        glProg.useProgram(R.raw.laplaciandown44);
        glProg.setTexture("InputBuffer", input);
        GLTexture output = new GLTexture(new Point(input.mSize.x / 4, input.mSize.y / 4), input.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    GLTexture medium;
    GLTexture small;
    GLTexture vsmall;
    private void Align(int i) {
        //startT();
        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("MAXMP",1);
        glProg.setDefine("PREVSCALE",0);
        glProg.setDefine("INPUTSIZE",brTex128H.mSize);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("InputBufferH",brTex128H);
        glProg.setTexture("MainBufferH",BaseFrame128H);
        glProg.setTexture("InputBufferV",brTex128V);
        glProg.setTexture("MainBufferV",BaseFrame128V);
        glProg.setTexture("InputBuffer",brTex128);
        glProg.setTexture("MainBuffer",BaseFrame128);

        glProg.drawBlocks(vsmall,8);


        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("MAXMP",1);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex32H.mSize);
        glProg.setDefine("LUCKYINPUT",true);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("AlignVectors",vsmall);
        glProg.setTexture("InputBufferH", brTex32H);
        glProg.setTexture("MainBufferH", BaseFrame32H);
        glProg.setTexture("InputBufferV", brTex32V);
        glProg.setTexture("MainBufferV", BaseFrame32V);
        glProg.setTexture("InputBuffer",brTex32);
        glProg.setTexture("MainBuffer",BaseFrame32);
        glProg.drawBlocks(small,8);

        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("MAXMP",1);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex8H.mSize);
        glProg.setDefine("LUCKYINPUT",true);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("AlignVectors",small);
        glProg.setTexture("InputBufferH", brTex8H);
        glProg.setTexture("MainBufferH", BaseFrame8H);
        glProg.setTexture("InputBufferV", brTex8V);
        glProg.setTexture("MainBufferV", BaseFrame8V);
        glProg.setTexture("InputBuffer",brTex8);
        glProg.setTexture("MainBuffer",BaseFrame8);
        glProg.drawBlocks(medium,8);
        //small.close();

        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("MAXMP",1);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex2H.mSize);
        glProg.setDefine("LUCKYINPUT",true);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("AlignVectors",medium);
        glProg.setTexture("InputBufferH", brTex2H);
        glProg.setTexture("MainBufferH", BaseFrame2H);
        glProg.setTexture("InputBufferV", brTex2V);
        glProg.setTexture("MainBufferV", BaseFrame2V);
        glProg.setTexture("InputBuffer",brTex2);
        glProg.setTexture("MainBuffer",BaseFrame2);
        glProg.drawBlocks(alignVectors[i-1],8);
        alignVectorsTemporal[i-1] = alignVectors[i-1].textureBuffer(alignVectors[i-1].mFormat,true).asShortBuffer();
    }

    private void Weights() {
        GLTexture out = Weights;
        GLTexture alt = WeightsAlt;
        GLTexture t = Weights;
        glProg.useProgram(R.raw.sumweights);
        for(int i =1; i<images.size();i++){
            glProg.setTexture("WeightsIn", Weight[i-1]);
            glProg.setTexture("WeightsOut", out);
            glProg.drawBlocks(alt);
            t = alt;
            alt = out;
            out = t;
        }
        Weights = t;
    }
    private int MirrorCoords(int in){
        if(in < 0) {
            in = -in;
        } else {
            if(in > alignVectorsTemporal.length-1)
                in = -in+alignVectorsTemporal.length-1;
        }
        in = Math.min(Math.max(in,0),alignVectorsTemporal.length-1);
        return in;
    }
    private void FilterTemporal() {
        for(int i = 0; i<alignVectorsTemporal.length;i++){
            for(int j =0; j<alignVectorsTemporal[i].remaining();j++){
                float k0 = alignVectorsTemporal[MirrorCoords(i-2)].asReadOnlyBuffer().get(j);
                float k1 = alignVectorsTemporal[MirrorCoords(i-1)].asReadOnlyBuffer().get(j);
                float k2 = alignVectorsTemporal[MirrorCoords(i)].get(j);
                float k3 = alignVectorsTemporal[MirrorCoords(i+1)].asReadOnlyBuffer().get(j);
                float k4 = alignVectorsTemporal[MirrorCoords(i+2)].asReadOnlyBuffer().get(j);
                float pred = (k0+(k1) + k3+(k4))/4.f;
                if(Math.max(pred,k2)/Math.min(pred,k2) > 1.5f) {
                    Log.d("AlignAndMerge","Pred:"+pred+" initial:"+k2);
                    alignVectorsTemporal[MirrorCoords(i)].put(j,(short) pred);
                }
            }
        }
    }
    private void Weight(int num,GLTexture InputFrame) {
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("FRAMECOUNT",images.size());
        glProg.useProgram(R.raw.spatialweights);
        glProg.setTexture("MainBuffer", BaseFrame2);
        glProg.setTexture("InputBuffer", brTex2);
        glProg.setTexture("AlignVectors", alignVectors[num-1]);
        glProg.drawBlocks(Weight[num-1]);
    }

    private GLTexture Merge(GLTexture Output, GLTexture inputRaw,int num) {
        //startT();
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.useProgram(R.raw.spatialmerge);
        /*short[] bufin = new short[alignVectorsTemporal[num-1].remaining()];
        alignVectorsTemporal[num-1].get(bufin);
        Log.d("AlignAndMerge","Vectors->"+Arrays.toString(bufin));
        GLTexture temporalFilteredVector = new GLTexture(alignVectors[num-1].mSize,alignVectors[num-1].mFormat,ShortBuffer.wrap(bufin));
         */
        GLTexture temporalFilteredVector = new GLTexture(alignVectors[num-1].mSize,alignVectors[num-1].mFormat,alignVectorsTemporal[num-1]);
        glProg.setTexture("AlignVectors", alignVectors[num-1]);
        glProg.setTexture("SumWeights", Weights);
        glProg.setTexture("Weight", Weight[num-1]);

        glProg.setTexture("MainBuffer", BaseFrame);
        glProg.setTexture("InputBuffer", inputRaw);

        //glProg.setTexture("InputBuffer22", brTex2);
        //glProg.setTexture("MainBuffer22", BaseFrame2);

        if(num == 1){
            glProg.setTexture("OutputBuffer", BaseFrame);
        } else glProg.setTexture("OutputBuffer", Output);
        glProg.setVar("alignk", 1.f / (float) (((RawPipeline) (basePipeline)).imageObj.size()));
        glProg.setVar("number",num+1);
        glProg.setVarU("rawsize", rawSize);
        glProg.setVarU("alignsize", alignVectors[0].mSize);
        GLTexture output = basePipeline.getMain();
        glProg.drawBlocks(output);
        temporalFilteredVector.close();
        //glProg.drawBlocks(Output,128,true);
        //Output.close();
        //endT("Merge");
        return output;
    }

    private GLTexture RawOutput(GLTexture input) {
        //startT();
        glProg.useProgram(R.raw.toraw);
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("whitelevel", ProcessorBase.FAKE_WL);
        GLTexture output = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), null);
        glProg.drawBlocks(output);
        glProg.closed = true;
        //endT("RawOutput");
        return output;
    }
    ArrayList<ImageFrame> images;
    GLTexture BaseFrame, BaseFrame2, BaseFrame8, BaseFrame32,BaseFrame128;
    GLTexture BaseFrame2H, BaseFrame8H, BaseFrame32H,BaseFrame128H;
    GLTexture BaseFrame2V, BaseFrame8V, BaseFrame32V,BaseFrame128V;
    GLTexture brTex2, brTex8, brTex32,brTex128;
    GLTexture brTex2H, brTex8H, brTex32H,brTex128H;
    GLTexture brTex2V, brTex8V, brTex32V,brTex128V;
    GLTexture[] alignVectors;
    ShortBuffer[] alignVectorsTemporal;
    GLTexture alignVector;
    GLTexture Weights,WeightsAlt;
    GLTexture[] Weight;
    GLTexture GainMap;
    final int tileSize = 64;
    @Override
    public void Run() {
        glProg = basePipeline.glint.glProgram;
        RawPipeline rawPipeline = (RawPipeline) basePipeline;
        rawSize = rawPipeline.glint.parameters.rawSize;
        images = rawPipeline.images;
        long time = System.currentTimeMillis();
        BaseFrame = new GLTexture(rawSize,new GLFormat(GLFormat.DataType.FLOAT_16));
        CorrectedRaw(BaseFrame,0);
        basePipeline.main2 = new GLTexture(BaseFrame);
        basePipeline.main1 = new GLTexture(BaseFrame);
        GainMap = new GLTexture(basePipeline.mParameters.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4),
                FloatBuffer.wrap(basePipeline.mParameters.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        Log.d("AlignAndMerge", "Corrected raw elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        BaseFrame2 = new GLTexture(BaseFrame.mSize.x/2,BaseFrame.mSize.y/2,new GLFormat(GLFormat.DataType.FLOAT_16,2),GL_LINEAR,GL_CLAMP_TO_EDGE);
        BaseFrame8 = new GLTexture(BaseFrame2.mSize.x/4, BaseFrame2.mSize.y/4, BaseFrame2.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);
        BaseFrame32 = new GLTexture(BaseFrame8.mSize.x/4, BaseFrame8.mSize.y/4, BaseFrame2.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);
        BaseFrame128 = new GLTexture(BaseFrame32.mSize.x/4, BaseFrame32.mSize.y/4, BaseFrame2.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);

        BaseFrame2H = new GLTexture(BaseFrame2);
        BaseFrame2V = new GLTexture(BaseFrame2);
        BaseFrame8H = new GLTexture(BaseFrame8);
        BaseFrame8V = new GLTexture(BaseFrame8);
        BaseFrame32H = new GLTexture(BaseFrame32);
        BaseFrame32V = new GLTexture(BaseFrame32);
        BaseFrame128H = new GLTexture(BaseFrame128);
        BaseFrame128V = new GLTexture(BaseFrame128);

        basePipeline.main3 = new GLTexture(BaseFrame2);
        BoxDown22(BaseFrame, BaseFrame2,BaseFrame2H,BaseFrame2V);
        GaussDown44(BaseFrame2, BaseFrame8,BaseFrame8H,BaseFrame8V,false);
        GaussDown44(BaseFrame8, BaseFrame32,BaseFrame32H,BaseFrame32V,false);
        GaussDown44(BaseFrame32,BaseFrame128,BaseFrame128H,BaseFrame128V,false);
        BaseFrame2.close();
        BaseFrame8.close();
        BaseFrame32.close();
        BaseFrame128.close();

        GLTexture Output = basePipeline.getMain();
        CorrectedRaw(Output,0);
        Log.d("AlignAndMerge", "Resize elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        time = System.currentTimeMillis();
        Log.d("AlignAndMerge","ImagesCount:"+images.size());
        brTex2 = new GLTexture(BaseFrame2);
        brTex8 = new GLTexture(BaseFrame8);
        brTex32 = new GLTexture(BaseFrame32);
        brTex128 = new GLTexture(BaseFrame128);

        brTex2V = new GLTexture(BaseFrame2);
        brTex8V = new GLTexture(BaseFrame8);
        brTex32V = new GLTexture(BaseFrame32);
        brTex128V = new GLTexture(BaseFrame128);

        brTex2H = new GLTexture(BaseFrame2);
        brTex8H = new GLTexture(BaseFrame8);
        brTex32H = new GLTexture(BaseFrame32);
        brTex128H = new GLTexture(BaseFrame128);
        int added = 1;
        alignVectors = new GLTexture[images.size()-1];
        Weight = new GLTexture[images.size()-1];
        alignVectorsTemporal = new ShortBuffer[images.size()-1];
        for(int i = 1; i<images.size();i++){
            alignVectors[i-1] = new GLTexture(new Point((brTex2.mSize.x / (tileSize))+added, (brTex2.mSize.y / (tileSize))+added), new GLFormat(GLFormat.DataType.SIGNED_16, 4));
            Weight[i-1] = new GLTexture(alignVectors[i-1].mSize,new GLFormat(GLFormat.DataType.FLOAT_16),GL_LINEAR,GL_CLAMP_TO_EDGE);
        }
        alignVector = new GLTexture(alignVectors[0]);
        medium = new GLTexture(new Point((brTex8.mSize.x / (tileSize))+added, (brTex8.mSize.y / (tileSize))+added), alignVectors[0].mFormat);
        small = new GLTexture(new Point((brTex32.mSize.x / (tileSize))+added, (brTex32.mSize.y / (tileSize))+added),alignVectors[0].mFormat);
        vsmall = new GLTexture(new Point((brTex128.mSize.x / (tileSize))+added, (brTex128.mSize.y / (tileSize))+added), alignVectors[0].mFormat);
        Weights = new GLTexture(alignVectors[0].mSize,new GLFormat(GLFormat.DataType.FLOAT_16),GL_LINEAR,GL_CLAMP_TO_EDGE);
        WeightsAlt = new GLTexture(Weights);

        GLTexture inputraw = new GLTexture(BaseFrame);
        for (int i = 1; i < images.size(); i++) {
            CorrectedRaw(inputraw,i);
            BoxDown22(inputraw, brTex2, brTex2H, brTex2V);
            GaussDown44(brTex2, brTex8, brTex8H, brTex8V, false);
            GaussDown44(brTex8, brTex32, brTex32H, brTex32V, false);
            GaussDown44(brTex32, brTex128, brTex128H, brTex128V, false);
            Align(i);
            Weight(i,inputraw);
        }
        Weights();
        //FilterTemporal();
        for (int i = 1; i < images.size(); i++) {
            CorrectedRaw(inputraw,i);
            if(i!=0) images.get(i).image.close();
            Output = Merge(Output, inputraw,i);
        }
        for(int i = 0; i<images.size()-1;i++){
            alignVectors[i].close();;
            Weight[i].close();
        }
        inputraw.close();
        brTex2.close();
        brTex8.close();
        brTex32.close();
        Log.d("AlignAndMerge", "AlignmentAndMerge elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        WorkingTexture = RawOutput(Output);
    }
}
