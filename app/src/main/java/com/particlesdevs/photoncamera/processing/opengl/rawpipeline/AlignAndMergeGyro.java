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
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.processor.ProcessorBase;
import com.particlesdevs.photoncamera.util.Utilities;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class AlignAndMergeGyro extends Node {
    Point rawSize;
    GLProg glProg;

    public AlignAndMergeGyro() {
        super(0, "AlignAndMerge");
    }

    @Override
    public void Compile() {}

    private void CorrectedRaw(GLTexture out, int number) {
        float bl = Math.min(Math.min(Math.min(PhotonCamera.getParameters().blackLevel[0],PhotonCamera.getParameters().blackLevel[1]),
                PhotonCamera.getParameters().blackLevel[2]),PhotonCamera.getParameters().blackLevel[3]);
        float mpy = minMpy / images.get(number).pair.layerMpy;
        glProg.setDefine("BL",PhotonCamera.getParameters().blackLevel);
        glProg.setDefine("WP",PhotonCamera.getParameters().whitePoint);
        glProg.setDefine("MPY",mpy);
        glProg.setDefine("BAYER",PhotonCamera.getParameters().cfaPattern);
        Log.d("Align","mpy:"+mpy);
        glProg.useProgram(R.raw.precorrection);
        GLTexture inraw = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), images.get(number).buffer);
        glProg.setTexture("InputBuffer",inraw);
        glProg.setVar("WhiteLevel",(float)PhotonCamera.getParameters().whiteLevel);
        glProg.drawBlocks(out);
        inraw.close();
    }
    private static final boolean corners = true;
    private void BoxDown22(GLTexture input,GLTexture out) {
        glProg.useProgram(R.raw.boxdown22);
        glProg.setTexture("InputBuffer", input);
        glProg.setTexture("GainMap", GainMap);
        glProg.setVar("CfaPattern", PhotonCamera.getParameters().cfaPattern);
        glProg.drawBlocks(basePipeline.main3,out.mSize);
        //glUtils.SaveProgResult(output.mSize,"boxdown");
        //glProg.close();
        //GLTexture median = glUtils.blur(output,5.0);
        //GLTexture laplaced = glUtils.ops(median,output,"in2.rgb,3.0*(in1.a-in2.a)");
        //median.close();

        glUtils.median(basePipeline.main3,out,new Point(1,1));

        //GLTexture median = glUtils.blur(output,1.5);
    }
    private int logged = 0;
    private void GaussDown44(GLTexture input,GLTexture out,boolean median) {
        if(median) {
            if(input.mSize.x+input.mSize.y > 9) {
                glUtils.interpolate(input, basePipeline.main3,out.mSize, 1.0 / 4.0);
                glUtils.median(basePipeline.main3, out, new Point(1, 1));
            } else {
                glUtils.median(input, out, new Point(1, 1));
            }
        } else{
            glUtils.interpolate(input, out, 1.0 / 4.0);
        }
        /*
        glUtils.ConvDiff(out,basePipeline.main3,tileSize/3,true,false);
        glUtils.Maximaze(basePipeline.main3,prevV,outV);
        //glUtils.bluxVH(basePipeline.main3,outV,(float)tileSize*2,true);
        if(logged>0){
            glUtils.convertVec4(outV,"(in1.r+in1.g)/0.5");
            glUtils.SaveProgResult(outH.mSize,"convV");
            logged--;
        }

        glUtils.ConvDiff(out,basePipeline.main3,tileSize/3,false,false);
        glUtils.Maximaze(basePipeline.main3,prevH,outH);
        //glUtils.bluxVH(basePipeline.main3,outH,(float)tileSize*2,false);
        if(logged>0){
            glUtils.convertVec4(outH,"(in1.r+in1.g)/0.5");
            glUtils.SaveProgResult(outH.mSize,"convH");
            logged--;
        }*/

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
    private void PrepareDiffs(GLTexture in,GLTexture ref,int i){
        float rotation = (float) images.get(i).rotation;
        glUtils.ConvDiff(in,DiffVIn,tileSize/3,true,false,rotation);
        glUtils.ConvDiff(in,DiffHIn,tileSize/3,false,false,rotation);
        glUtils.ConvDiff(ref,DiffVRef,tileSize/3,true,false);
        glUtils.ConvDiff(ref,DiffHRef,tileSize/3,false,false);
        glUtils.Corners(DiffVRef,DiffVRef,CornersRef);
        if(logged>0){
            glUtils.convertVec4(CornersIn,"(in1.r+in1.g)/0.1");
            glUtils.SaveProgResult(in.mSize,"corners");
            logged--;
        }
    }
    GLTexture medium;
    GLTexture small;
    GLTexture vsmall;
    private void Align(int i) {
        //startT();
        Point movement = new Point((int)((images.get(0).posx-images.get(i).posx)/2.0),(int)((images.get(0).posy-images.get(i).posy)/2.0));
        /*glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",0);
        glProg.setDefine("INPUTSIZE",brTex128H.mSize);
        glProg.setDefine("INITIALMOVE", Utilities.div(movement,64));
        glProg.setDefine("LOWPASSCOMBINE",false);

        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("InputBufferH",brTex128H);
        glProg.setTexture("MainBufferH",BaseFrame128H);

        glProg.setTexture("InputBufferV",brTex128V);
        glProg.setTexture("MainBufferV",BaseFrame128V);

        //glProg.setTexture("InputBuffer",brTex128);
        //glProg.setTexture("MainBuffer",BaseFrame128);

        glProg.drawBlocks(vsmall);


        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex32H.mSize);
        glProg.setDefine("INITIALMOVE", Utilities.div(movement,16));
        glProg.setDefine("LUCKYINPUT",true);
        glProg.setDefine("LOWPASSCOMBINE",false);
        glProg.setDefine("LOWPASSK",4);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("AlignVectors",vsmall);
        glProg.setTexture("InputBufferH", brTex32H);
        glProg.setTexture("MainBufferH", BaseFrame32H);

        glProg.setTexture("InputBufferV", brTex32V);
        glProg.setTexture("MainBufferV", BaseFrame32V);

        glProg.setTexture("LowPassV",brTex128V);
        glProg.setTexture("LowPassH",BaseFrame128H);
        //glProg.setTexture("InputBuffer",brTex32);
        //glProg.setTexture("MainBuffer",BaseFrame32);
        glProg.drawBlocks(small);*/

        PrepareDiffs(brTex8,BaseFrame8,i);
        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",0);
        glProg.setDefine("INPUTSIZE",brTex8.mSize);
        glProg.setDefine("INITIALMOVE", Utilities.div(movement,4));
        glProg.setDefine("LUCKYINPUT",false);
        glProg.setDefine("LOWPASSCOMBINE",false);
        glProg.setDefine("LOWPASSK",16);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("AlignVectors",small);

        glProg.setTexture("InputBufferV", DiffVIn);
        glProg.setTexture("MainBufferV", DiffVRef);

        glProg.setTexture("InputBufferH", DiffHIn);
        glProg.setTexture("MainBufferH", DiffHRef);
        glProg.setTexture("CornersRef",CornersRef);

        glProg.drawBlocks(medium);
        //small.close();

        PrepareDiffs(brTex2,BaseFrame2,i);
        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex2.mSize);
        glProg.setDefine("INITIALMOVE", movement);
        glProg.setDefine("LUCKYINPUT",false);
        glProg.setDefine("LOWPASSCOMBINE",false);
        glProg.setDefine("LOWPASSK",64);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("AlignVectors",medium);

        glProg.setTexture("InputBufferV", DiffVIn);
        glProg.setTexture("MainBufferv", DiffVRef);

        glProg.setTexture("InputBufferH", DiffHIn);
        glProg.setTexture("MainBufferH", DiffHRef);
        glProg.setTexture("CornersRef",CornersRef);

        glProg.drawBlocks(alignVectors[i-1]);
        //alignVectorsTemporal[i-1] = alignVectors[i-1].textureBuffer(alignVectors[i-1].mFormat,true).asShortBuffer();
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
    private void Weight(int num) {
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("FRAMECOUNT",images.size());
        glProg.useProgram(R.raw.spatialweights);
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
        glProg.setDefine("MIN",minMpy);
        glProg.setDefine("MPY",minMpy / images.get(num).pair.layerMpy);
        glProg.setDefine("WP",PhotonCamera.getParameters().whitePoint);
        glProg.setDefine("BAYER",PhotonCamera.getParameters().cfaPattern);
        glProg.setDefine("HDR",true);
        glProg.setDefine("ROTATIOn", (float) images.get(num).rotation);
        glProg.useProgram(R.raw.spatialmerge);

        short[] bufin = new short[alignVectors[0].mSize.x*alignVectors[0].mSize.y*4];

        for(int k =0; k<alignVectors[0].mSize.x*alignVectors[0].mSize.y*4; k+=4){
            bufin[k] = (short) ((images.get(0).posx-images.get(num).posx)/2.0);
            bufin[k+1] = (short) ((images.get(0).posy-images.get(num).posy)/2.0);
            bufin[k+2] = 0;
            bufin[k+3] = 0;
        }
        //alignVectorsTemporal[num-1].get(bufin);
        Log.d("AlignAndMerge","Vectors->"+ Arrays.toString(bufin));
        GLTexture gyroVectors = new GLTexture(alignVectors[num-1].mSize,alignVectors[num-1].mFormat,ShortBuffer.wrap(bufin));

        //GLTexture temporalFilteredVector = new GLTexture(alignVectors[num-1].mSize,alignVectors[num-1].mFormat,alignVectorsTemporal[num-1]);
        glProg.setTexture("AlignVectors", alignVectors[num-1]);
        glProg.setTexture("SumWeights", Weights);
        glProg.setTexture("Weight", Weight[num-1]);

        glProg.setTexture("MainBuffer", BaseFrame);
        glProg.setTexture("InputBuffer", inputRaw);

        glProg.setTexture("InputBuffer22", brTex2);
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
        //temporalFilteredVector.close();
        //glProg.drawBlocks(Output,128,true);
        //Output.close();
        //endT("Merge");
        return output;
    }

    private GLTexture RawOutput(GLTexture input) {
        //startT();
        float[] outBL = new float[4];
        for(int i=0;i<outBL.length;i++) outBL[i] = PhotonCamera.getParameters().blackLevel[i]*(ProcessorBase.FAKE_WL/((float)PhotonCamera.getParameters().whiteLevel));
        glProg.setDefine("BL",outBL);
        glProg.setDefine("BAYER",PhotonCamera.getParameters().cfaPattern);
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
    GLTexture brTex2, brTex8, brTex32,brTex128;
    GLTexture[] alignVectors;
    ShortBuffer[] alignVectorsTemporal;
    GLTexture alignVector;
    GLTexture Weights,WeightsAlt;
    GLTexture[] Weight;
    GLTexture GainMap;
    GLTexture DiffVRef,DiffHRef,CornersRef;
    GLTexture DiffVIn,DiffHIn,CornersIn;
    final int tileSize = 128;
    float minMpy = 1000.f;
    @Override
    public void Run() {
        glProg = basePipeline.glint.glProgram;
        RawPipeline rawPipeline = (RawPipeline) basePipeline;
        rawSize = rawPipeline.glint.parameters.rawSize;
        images = rawPipeline.images;

        for (int i = 0; i < IsoExpoSelector.fullpairs.size(); i++) {
            if (IsoExpoSelector.fullpairs.get(i).layerMpy < minMpy) {
                minMpy = IsoExpoSelector.fullpairs.get(i).layerMpy;
            }
        }
        if (images.get(0).pair.layerMpy != minMpy) {
            for (int i = 1; i < images.size(); i++) {
                if (images.get(i).pair.layerMpy == minMpy) {
                    ImageFrame frame = images.get(0);
                    images.set(0, images.get(i));
                    images.set(i, frame);
                    break;
                }
            }
        }
        long time = System.currentTimeMillis();
        BaseFrame = new GLTexture(rawSize,new GLFormat(GLFormat.DataType.FLOAT_16));
        CorrectedRaw(BaseFrame,0);
        basePipeline.main2 = new GLTexture(BaseFrame);
        basePipeline.main1 = new GLTexture(BaseFrame);
        GainMap = new GLTexture(basePipeline.mParameters.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4),
                FloatBuffer.wrap(basePipeline.mParameters.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        Log.d("AlignAndMerge", "Corrected raw elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        BaseFrame2 = new GLTexture(BaseFrame.mSize.x/2,BaseFrame.mSize.y/2,new GLFormat(GLFormat.DataType.FLOAT_16,2),GL_LINEAR,GL_CLAMP_TO_EDGE);
        DiffVRef = new GLTexture(BaseFrame2);
        DiffHRef = new GLTexture(BaseFrame2);
        DiffVIn = new GLTexture(BaseFrame2);
        DiffHIn = new GLTexture(BaseFrame2);
        CornersRef = new GLTexture(BaseFrame2);
        CornersIn = new GLTexture(BaseFrame2);
        BaseFrame8 = new GLTexture(BaseFrame2.mSize.x/4, BaseFrame2.mSize.y/4, BaseFrame2.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);
        BaseFrame32 = new GLTexture(BaseFrame8.mSize.x/4, BaseFrame8.mSize.y/4, BaseFrame2.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);
        BaseFrame128 = new GLTexture(BaseFrame32.mSize.x/4, BaseFrame32.mSize.y/4, BaseFrame2.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);

        basePipeline.main3 = new GLTexture(BaseFrame2);
        BoxDown22(BaseFrame, BaseFrame2);
        GaussDown44(BaseFrame2, BaseFrame8,false);
        GaussDown44(BaseFrame8, BaseFrame32,false);
        GaussDown44(BaseFrame32,BaseFrame128,false);
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
            BoxDown22(inputraw, brTex2);
            GaussDown44(brTex2, brTex8, true);
            GaussDown44(brTex8, brTex32, false);
            GaussDown44(brTex32, brTex128, false);
            Align(i);
            //Weight(i);
        }
        //Weights();
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
        Log.d("AlignAndMerge", "AlignmentAndMerge elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        WorkingTexture = RawOutput(Output);
    }
}
