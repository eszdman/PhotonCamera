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
import com.particlesdevs.photoncamera.processing.rs.AlignWithGL;
import com.particlesdevs.photoncamera.processing.rs.GlAllocation;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class AlignAndMergeHybrid extends Node {
    Point rawSize;
    GLProg glProg;

    public AlignAndMergeHybrid() {
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

    private void BoxDown22(GLTexture input,GlAllocation out) {
        glProg.useProgram(R.raw.boxdown22);
        glProg.setTexture("InputBuffer", input);
        glProg.setTexture("GainMap", GainMap);
        glProg.setVar("CfaPattern", PhotonCamera.getParameters().cfaPattern);
        glProg.drawBlocks(basePipeline.main3,out.glTexture.mSize);
        glUtils.median(basePipeline.main3,out.glTexture,new Point(1,1));
        out.pushToAllocation();
    }
    private int logged = 0;
    private void GaussDown44(GlAllocation input,GlAllocation out,boolean median) {
        if(median) {
            if(input.glTexture.mSize.x+input.glTexture.mSize.y > 9) {
                glUtils.interpolate(input.glTexture, basePipeline.main3, 1.0 / 4.0);
                glUtils.median(basePipeline.main3, out.glTexture, new Point(1, 1));
            } else {
                glUtils.median(input.glTexture, out.glTexture, new Point(1, 1));
            }
        } else{
            glUtils.interpolate(input.glTexture, out.glTexture, 1.0 / 4.0);
        }
        out.pushToAllocation();
    }

    private GLTexture LaplacDown44(GLTexture input) {
        glProg.useProgram(R.raw.laplaciandown44);
        glProg.setTexture("InputBuffer", input);
        GLTexture output = new GLTexture(new Point(input.mSize.x / 4, input.mSize.y / 4), input.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    private void Align(int i) {
        //startT();
        /*
        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",0);
        glProg.setDefine("INPUTSIZE",brTex128.glTexture.mSize);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("InputBuffer",brTex128.glTexture);
        glProg.setTexture("MainBuffer",BaseFrame128.glTexture);

        glProg.drawBlocks(vsmall);


        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex32.glTexture.mSize);
        glProg.setDefine("LUCKYINPUT",true);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("AlignVectors",vsmall);
        glProg.setTexture("InputBuffer",brTex32.glTexture);
        glProg.setTexture("MainBuffer",BaseFrame32.glTexture);
        glProg.drawBlocks(small);

        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex8.glTexture.mSize);
        glProg.setDefine("LUCKYINPUT",true);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("AlignVectors",small);
        glProg.setTexture("InputBuffer",brTex8.glTexture);
        glProg.setTexture("MainBuffer",BaseFrame8.glTexture);
        glProg.drawBlocks(medium);
        //small.close();

        glProg.setDefine("SCANSIZE",tileSize*2);
        glProg.setDefine("TILESIZE",tileSize);
        glProg.setDefine("PREVSCALE",4);
        glProg.setDefine("INPUTSIZE",brTex2.glTexture.mSize);
        glProg.setDefine("LUCKYINPUT",true);
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setTexture("AlignVectors",medium);
        glProg.setTexture("InputBuffer",brTex2.glTexture);
        glProg.setTexture("MainBuffer",BaseFrame2.glTexture);
        glProg.drawBlocks(alignVectors[i-1].glTexture);
        alignVectorsTemporal[i-1] = alignVectors[i-1].glTexture.textureBuffer(alignVectors[i-1].glTexture.mFormat,true).asShortBuffer();*/
        alignRs.refDown2 = BaseFrame2;
        alignRs.refDown8 = BaseFrame8;
        alignRs.refDown32 = BaseFrame32;
        alignRs.refDown128 = BaseFrame128;
        alignRs.inputDown2 = brTex2;
        alignRs.inputDown8 = brTex8;
        alignRs.inputDown32 = brTex32;
        alignRs.inputDown128 = brTex128;
        alignRs.align128 = align128;
        alignRs.align32 = align32;
        alignRs.align8 = align8;
        alignRs.align2 = alignVectors[i-1];
        alignRs.AlignFrame();
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
        glProg.setDefine("INPUTSIZE",BaseFrame2.glTexture.mSize);
        glProg.useProgram(R.raw.spatialweights);
        glProg.setTexture("MainBuffer", BaseFrame2.glTexture);
        glProg.setTexture("InputBuffer", brTex2.glTexture);
        glProg.setTexture("AlignVectors", alignVectors[num-1].glTexture);
        glProg.drawBlocks(Weight[num-1]);
    }

    private GLTexture Merge(GLTexture Output, GLTexture inputRaw,int num) {
        //startT();
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("MIN",minMpy);
        glProg.setDefine("MPY",minMpy / images.get(num).pair.layerMpy);
        glProg.setDefine("WP",PhotonCamera.getParameters().whitePoint);
        glProg.setDefine("BAYER",PhotonCamera.getParameters().cfaPattern);
        glProg.setDefine("HDR",true);
        glProg.useProgram(R.raw.spatialmerge);
        /*short[] bufin = new short[alignVectors[0].glTexture.mSize.x*alignVectors[0].glTexture.mSize.y*4];
        for(int k =0; k<alignVectors[0].glTexture.mSize.x*alignVectors[0].glTexture.mSize.y*4; k+=4){
            bufin[k] = 0;
            bufin[k+1] = 0;
            bufin[k+2] = 0;
            bufin[k+3] = 0;
        }
        //alignVectorsTemporal[num-1].get(bufin);
        Log.d("AlignAndMerge","Vectors->"+Arrays.toString(bufin));
        GLTexture temporalFilteredVector = new GLTexture(alignVectors[num-1].glTexture.mSize,alignVectors[num-1].glTexture.mFormat,ShortBuffer.wrap(bufin));
         */
        //GLTexture temporalFilteredVector = new GLTexture(alignVectors[num-1].mSize,alignVectors[num-1].mFormat,alignVectorsTemporal[num-1]);
        short[] bufin = new short[alignVectors[0].glTexture.mSize.x*alignVectors[0].glTexture.mSize.y*4];
        alignVectors[num-1].allocation.copyTo(bufin);
        Log.d("AlignAndMerge","Vectors->"+Arrays.toString(bufin));
        glProg.setTexture("AlignVectors", alignVectors[num-1].glTexture);
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
        glProg.setVarU("alignsize", alignVectors[0].glTexture.mSize);
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
    GLTexture BaseFrame;
    GlAllocation  BaseFrame2, BaseFrame8, BaseFrame32,BaseFrame128;
    GlAllocation brTex2, brTex8, brTex32,brTex128;
    GlAllocation[] alignVectors;
    ShortBuffer[] alignVectorsTemporal;
    GlAllocation alignVector;
    GLTexture Weights,WeightsAlt;
    GLTexture[] Weight;
    GLTexture GainMap;
    AlignWithGL alignRs;
    GlAllocation align8,align32,align128;
    public static final int tileSize = 128;
    float minMpy = 1000.f;
    @Override
    public void Run() {
        glProg = basePipeline.glint.glProgram;
        alignRs = new AlignWithGL();
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
        BaseFrame2 = new GlAllocation(BaseFrame.mSize.x/2,BaseFrame.mSize.y/2,new GLFormat(GLFormat.DataType.FLOAT_16,2),GL_LINEAR,GL_CLAMP_TO_EDGE);
        BaseFrame8 = new GlAllocation(BaseFrame2.glTexture.mSize.x/4, BaseFrame2.glTexture.mSize.y/4, BaseFrame2.glTexture.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);
        BaseFrame32 = new GlAllocation(BaseFrame8.glTexture.mSize.x/4, BaseFrame8.glTexture.mSize.y/4, BaseFrame2.glTexture.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);
        BaseFrame128 = new GlAllocation(BaseFrame32.glTexture.mSize.x/4, BaseFrame32.glTexture.mSize.y/4, BaseFrame2.glTexture.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);

        basePipeline.main3 = new GLTexture(BaseFrame2.glTexture);
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
        brTex2 = new GlAllocation(BaseFrame2);
        brTex8 = new GlAllocation(BaseFrame8);
        brTex32 = new GlAllocation(BaseFrame32);
        brTex128 = new GlAllocation(BaseFrame128);

        int added = 1;
        alignVectors = new GlAllocation[images.size()-1];
        Weight = new GLTexture[images.size()-1];
        alignVectorsTemporal = new ShortBuffer[images.size()-1];
        for(int i = 1; i<images.size();i++){
            alignVectors[i-1] = new GlAllocation((brTex2.glTexture.mSize.x / (tileSize))+added, (brTex2.glTexture.mSize.y / (tileSize))+added, new GLFormat(GLFormat.DataType.SIGNED_16, 4));
            Weight[i-1] = new GLTexture(alignVectors[i-1].glTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16),GL_LINEAR,GL_CLAMP_TO_EDGE);
        }
        alignVector = new GlAllocation(alignVectors[0]);
        align8 = new GlAllocation((brTex8.glTexture.mSize.x / (tileSize))+added, (brTex8.glTexture.mSize.y / (tileSize))+added, alignVectors[0].glTexture.mFormat);
        align32 = new GlAllocation((brTex32.glTexture.mSize.x / (tileSize))+added, (brTex32.glTexture.mSize.y / (tileSize))+added,alignVectors[0].glTexture.mFormat);
        align128 = new GlAllocation((brTex128.glTexture.mSize.x / (tileSize))+added, (brTex128.glTexture.mSize.y / (tileSize))+added, alignVectors[0].glTexture.mFormat);
        Weights = new GLTexture(alignVectors[0].glTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16),GL_LINEAR,GL_CLAMP_TO_EDGE);
        WeightsAlt = new GLTexture(Weights);

        GLTexture inputraw = new GLTexture(BaseFrame);
        for (int i = 1; i < images.size(); i++) {
            CorrectedRaw(inputraw,i);
            BoxDown22(inputraw, brTex2);
            GaussDown44(brTex2, brTex8, true);
            GaussDown44(brTex8, brTex32, false);
            GaussDown44(brTex32, brTex128, false);
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
