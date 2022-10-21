package com.particlesdevs.photoncamera.processing.opengl.rawpipeline;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.processor.ProcessorBase;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class AlignAndMergeCompute extends Node {
    Point rawSize;
    GLProg glProg;

    public AlignAndMergeCompute() {
        super("", "AlignAndMerge");
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

    private void CorrectedRaw(GLTexture out, int number) {
        float bl = Math.min(Math.min(Math.min(PhotonCamera.getParameters().blackLevel[0],PhotonCamera.getParameters().blackLevel[1]),
                PhotonCamera.getParameters().blackLevel[2]),PhotonCamera.getParameters().blackLevel[3]);    
        //glProg.setDefine("BL",bl);
        glProg.useAssetProgram("precorrection");
        GLTexture inraw = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), images.get(number).buffer);
        glProg.setTexture("InputBuffer",inraw);
        glProg.setVar("WhiteLevel",(float)PhotonCamera.getParameters().whiteLevel);
        glProg.drawBlocks(out);
        inraw.close();
    }

    private void BoxDown22(GLTexture input,GLTexture out) {
        glProg.useAssetProgram("boxdown22");
        glProg.setTexture("InputBuffer", input);
        glProg.setTexture("GainMap", GainMap);
        glProg.setVar("CfaPattern", PhotonCamera.getParameters().cfaPattern);
        glProg.drawBlocks(basePipeline.main3,out.mSize);

        glUtils.median(basePipeline.main3,out,new Point(1,1));
    }
    private int logged = 0;
    private void GaussDown44(GLTexture input,GLTexture out,boolean median) {
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
    }

    private GLTexture LaplacDown44(GLTexture input) {
        glProg.useAssetProgram("laplaciandown44");
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
        glProg.setDefine("SCANSIZE",tileSize* overScan);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",0);
        glProg.setDefine("INPUTSIZE",brTex128.mSize);
        glProg.setDefine("LOWPASSCOMBINE",false);
        glProg.setDefine("FLOWACT",opticalFlowActivity);

        glProg.useAssetProgram("pyramidalign2");
        glProg.setTexture("InputBuffer",brTex128);
        glProg.setTexture("MainBuffer",BaseFrame128);

        glProg.setTexture("DiffHVRef",DiffHVRef128);


        glProg.drawBlocks(vsmall);

        glProg.setDefine("SCANSIZE",tileSize* overScan);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex32.mSize);
        glProg.setDefine("LUCKYINPUT",useLuckyLayers);
        glProg.setDefine("LOWPASSCOMBINE",false);
        glProg.setDefine("FLOWACT",opticalFlowActivity);
        glProg.setDefine("LOWPASSK",4);

        glProg.useAssetProgram("pyramidalign2");
        glProg.setTexture("AlignVectors",vsmall);
        glProg.setTexture("InputBuffer", brTex32);
        glProg.setTexture("MainBuffer", BaseFrame32);

        glProg.setTexture("DiffHVRef",DiffHVRef32);


        glProg.drawBlocks(small);
        glProg.setDefine("SCANSIZE",tileSize* overScan);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex8.mSize);
        glProg.setDefine("LUCKYINPUT",useLuckyLayers);
        glProg.setDefine("LOWPASSCOMBINE",false);
        glProg.setDefine("FLOWACT",opticalFlowActivity);
        glProg.setDefine("LOWPASSK",16);

        glProg.useAssetProgram("pyramidalign2");

        glProg.setTexture("AlignVectors",small);

        glProg.setTexture("InputBuffer", brTex8);
        glProg.setTexture("MainBuffer", BaseFrame8);

        glProg.setTexture("DiffHVRef",DiffHVRef8);

        glProg.drawBlocks(medium);
        //small.close();
        glProg.setDefine("SCANSIZE",tileSize* overScan);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex2.mSize);
        glProg.setDefine("LUCKYINPUT",useLuckyLayers);
        glProg.setDefine("LOWPASSCOMBINE",false);
        glProg.setDefine("FLOWACT",opticalFlowActivity);
        glProg.setDefine("LOWPASSK",64);

        glProg.useAssetProgram("pyramidalign2");

        glProg.setTexture("AlignVectors",medium);
        glProg.setTexture("InputBuffer", brTex2);
        glProg.setTexture("MainBuffer", BaseFrame2);
        glProg.setTexture("DiffHVRef",DiffHVRef2);


        glProg.drawBlocks(alignVectors[i-1],alignVectors[i-1].mSize);

        //alignVectorsTemporal[i-1] = alignVectors[i-1].textureBuffer(alignVectors[i-1].mFormat,true).asShortBuffer();
    }

    private void Weights() {
        GLTexture out = Weights;
        GLTexture alt = WeightsAlt;
        GLTexture t = Weights;
        glProg.useAssetProgram("sumweights");
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
    private void Weight(int num) {
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("FRAMECOUNT",images.size());
        glProg.useAssetProgram("spatialweights");
        glProg.setTexture("MainBuffer", BaseFrame2);
        glProg.setTexture("InputBuffer", brTex2);
        glProg.setTexture("AlignVectors", alignVectors[num-1]);
        glProg.drawBlocks(Weight[num-1]);

            //glUtils.convertVec4(Weight[num-1],"(in1*10.0)");
            //glUtils.SaveProgResult(Weight[num-1].mSize,"WGht");
    }

    private GLTexture Merge(GLTexture Output, GLTexture inputRaw,int num) {
        //startT();
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.useAssetProgram("spatialmerge");
        //short[] bufin = new short[alignVectors[num-1].mSize.x*alignVectors[num-1].mSize.y*4];
        //ByteBuffer buffers = alignVectors[num-1].textureBuffer(alignVectors[num-1].mFormat);
        //buffers.asShortBuffer().get(bufin);
        /*for(int k =0; k<alignVectors[0].mSize.x*alignVectors[0].mSize.y*4; k+=4){
            bufin[k] = (short) ((images.get(0).posx-images.get(num).posx)/2.0);
            bufin[k+1] = (short) ((images.get(0).posy-images.get(num).posy)/2.0);
            bufin[k+2] = 0;
            bufin[k+3] = 0;
        }*/
        //alignVectorsTemporal[num-1].get(bufin);
        //Log.d("AlignAndMerge","Vectors->"+ Arrays.toString(bufin));
        /*
        short[] bufin = new short[alignVectors[0].mSize.x*alignVectors[0].mSize.y*4];
        for(int k =0; k<alignVectors[0].mSize.x*alignVectors[0].mSize.y*4; k+=4){
            bufin[k] = -400;
            bufin[k+1] = -400;
            bufin[k+2] = 0;
            bufin[k+3] = 0;
        }
        //alignVectorsTemporal[num-1].get(bufin);
        Log.d("AlignAndMerge","Vectors->"+Arrays.toString(bufin));
        GLTexture temporalFilteredVector = new GLTexture(alignVectors[num-1].mSize,alignVectors[num-1].mFormat,ShortBuffer.wrap(bufin));
        */
        //GLTexture temporalFilteredVector = new GLTexture(alignVectors[num-1].mSize,alignVectors[num-1].mFormat,alignVectorsTemporal[num-1]);
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
        glProg.setVar("alignk", 1.f / (float) (((RawPipeline) (basePipeline)).images.size()));
        glProg.setVar("number",num+1);
        glProg.setVarU("rawsize", rawSize);
        glProg.setVarU("alignsize", alignVectors[0].mSize);
        GLTexture output = basePipeline.getMain();
        glProg.drawBlocks(output);
        //temporalFilteredVector.close();
        //glProg.drawBlocks(Output,128,true);
        //Output.close();
        //endT("Merge");
        return output;
    }

    private GLTexture RawOutput(GLTexture input) {
        //startT();
        glProg.useAssetProgram("toraw");
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
    GLTexture brTex2, brTex8, brTex32,brTex128;
    GLTexture DiffHVRef;
    GLTexture DiffHVRef2,DiffHVRef8,DiffHVRef32,DiffHVRef128;
    GLTexture[] alignVectors;
    GLTexture debug;
    ShortBuffer[] alignVectorsTemporal;
    GLTexture alignVector;
    GLTexture Weights,WeightsAlt;
    GLTexture[] Weight;
    GLTexture GainMap;
    int tileSize = 32;
    int overScan = 2;
    boolean useLuckyLayers = false;
    boolean medianFilterPyramid = true;
    float opticalFlowActivity = -2.0f;
    float gradientMapShift =  0.2f;
    @Override
    public void Run() {
        tileSize = getTuning("TileSize",tileSize);
        overScan = getTuning("OverScan", overScan);
        useLuckyLayers = getTuning("UseLuckyLayers",useLuckyLayers);
        medianFilterPyramid = getTuning("MedianFilterPyramid",medianFilterPyramid);
        opticalFlowActivity = getTuning("OpticalFlowActivity",opticalFlowActivity);
        gradientMapShift = getTuning("GradientMapShift",gradientMapShift);
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

        DiffHVRef = new GLTexture(BaseFrame2);

        basePipeline.main3 = new GLTexture(BaseFrame2);
        BoxDown22(BaseFrame, BaseFrame2);
        GaussDown44(BaseFrame2, BaseFrame8,medianFilterPyramid);
        GaussDown44(BaseFrame8, BaseFrame32,medianFilterPyramid);
        GaussDown44(BaseFrame32,BaseFrame128,medianFilterPyramid);

        GLTexture Output = basePipeline.getMain();
        CorrectedRaw(Output,0);
        Log.d("AlignAndMerge", "Resize elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        time = System.currentTimeMillis();
        Log.d("AlignAndMerge","ImagesCount:"+images.size());
        brTex2 = new GLTexture(BaseFrame2);
        brTex8 = new GLTexture(BaseFrame8);
        brTex32 = new GLTexture(BaseFrame32);
        brTex128 = new GLTexture(BaseFrame128);

        DiffHVRef2 = new GLTexture(BaseFrame2);
        DiffHVRef8 = new GLTexture(BaseFrame8);
        DiffHVRef32 = new GLTexture(BaseFrame32);
        DiffHVRef128 = new GLTexture(BaseFrame128);
        glUtils.ConvDiff(BaseFrame2, DiffHVRef2,gradientMapShift);
        glUtils.ConvDiff(BaseFrame8, DiffHVRef8,gradientMapShift);
        glUtils.ConvDiff(BaseFrame32, DiffHVRef32,gradientMapShift);
        glUtils.ConvDiff(BaseFrame128, DiffHVRef128,gradientMapShift);
        GLTexture corn = new GLTexture(DiffHVRef2);
        glUtils.Corners(DiffHVRef2,corn);
        //glUtils.convertVec4(corn,"(in1.r)*100.0");
        //glUtils.SaveProgResult(corn.mSize,"corners");


        int added = 1;
        alignVectors = new GLTexture[images.size()-1];
        Weight = new GLTexture[images.size()-1];
        alignVectorsTemporal = new ShortBuffer[images.size()-1];
        for(int i = 1; i<images.size();i++){
            alignVectors[i-1] = new GLTexture(new Point((brTex2.mSize.x / (tileSize))+added, (brTex2.mSize.y / (tileSize))+added), new GLFormat(GLFormat.DataType.SIGNED_32, 4));
            Weight[i-1] = new GLTexture(alignVectors[i-1].mSize,new GLFormat(GLFormat.DataType.FLOAT_16),GL_LINEAR,GL_CLAMP_TO_EDGE);
        }
        debug = new GLTexture(alignVectors[0].mSize,new GLFormat(GLFormat.DataType.FLOAT_16,4));
        alignVector = new GLTexture(alignVectors[0]);
        medium = new GLTexture(new Point((brTex8.mSize.x / (tileSize))+added, (brTex8.mSize.y / (tileSize))+added), alignVectors[0].mFormat);
        small = new GLTexture(new Point((brTex32.mSize.x / (tileSize))+added, (brTex32.mSize.y / (tileSize))+added),alignVectors[0].mFormat);
        added = 1;
        vsmall = new GLTexture(new Point((brTex128.mSize.x / (tileSize))+added, (brTex128.mSize.y / (tileSize))+added), alignVectors[0].mFormat);
        Weights = new GLTexture(alignVectors[0].mSize,new GLFormat(GLFormat.DataType.FLOAT_16),GL_LINEAR,GL_CLAMP_TO_EDGE);
        WeightsAlt = new GLTexture(Weights);
        GLTexture inputraw = new GLTexture(BaseFrame);
        for (int i = 1; i < images.size(); i++) {
            CorrectedRaw(inputraw,i);
            BoxDown22(inputraw, brTex2);
            GaussDown44(brTex2, brTex8, medianFilterPyramid);
            GaussDown44(brTex8, brTex32, medianFilterPyramid);
            GaussDown44(brTex32, brTex128, medianFilterPyramid);
            Align(i);
            Weight(i);

        }
        Weights();

        //FilterTemporal();
        for (int i = 1; i < images.size(); i++) {
            CorrectedRaw(inputraw,i);
            images.get(i).image.close();
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
        brTex128.close();
        BaseFrame2.close();
        BaseFrame8.close();
        BaseFrame32.close();
        BaseFrame128.close();
        Log.d("AlignAndMerge", "AlignmentAndMerge elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        WorkingTexture = RawOutput(Output);
    }
}
