package com.eszdman.photoncamera.processing.opengl.rawpipeline;

import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.ImageFrame;
import com.eszdman.photoncamera.processing.ProcessorBase;
import com.eszdman.photoncamera.processing.UnlimitedProcessor;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class AlignAndMerge extends Node {
    Point rawSize;
    GLProg glProg;

    public AlignAndMerge(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Compile() {}

    private void CorrectedRaw(GLTexture out, int number) {
        glProg.useProgram(R.raw.precorrection);
        GLTexture inraw = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), images.get(number).buffer);
        glProg.setTexture("InputBuffer",inraw);
        glProg.setVar("WhiteLevel", (float) PhotonCamera.getParameters().realWL);
        glProg.drawBlocks(out);
        inraw.close();
    }

    private GLTexture CorrectedRaw32(ByteBuffer input) {
        glProg.useProgram(R.raw.precorrection);
        glProg.setTexture("InputBuffer", new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), input));
        glProg.setVar("WhiteLevel", (float) PhotonCamera.getParameters().realWL);
        GLTexture output = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.FLOAT_32), null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    private void BoxDown22(GLTexture input,GLTexture out) {
        glProg.useProgram(R.raw.boxdown22);
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("CfaPattern", PhotonCamera.getParameters().cfaPattern);
        glProg.drawBlocks(basePipeline.main3);
        //glUtils.SaveProgResult(output.mSize,"boxdown");
        //glProg.close();
        //GLTexture median = glUtils.blur(output,5.0);
        //GLTexture laplaced = glUtils.ops(median,output,"in2.rgb,3.0*(in1.a-in2.a)");
        //median.close();

        //glUtils.median(basePipeline.main3,out,new Point(1,1));

        //GLTexture median = glUtils.blur(output,1.5);
        glUtils.median(basePipeline.main3, out,new Point(1,1));
        //glUtils.SaveProgResult(output.mSize,"aligninput");
        //GLTexture laplaced = glUtils.ops(blur,output,"-");
        //output.close();
        //return laplaced;
    }
    private void GaussDown44(GLTexture input,GLTexture out,boolean median) {
        /*glProg.useProgram(R.raw.gaussdown44);
        glProg.setTexture("InputBuffer", input);
        GLTexture output = new GLTexture(new Point(input.mSize.x / 4, input.mSize.y / 4), input.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;*/
        //glUtils.gaussdown(input,out,4,1.5);
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
    private void Align() {
        //startT();

        glProg.setDefine("SCANSIZE","("+tileSize+")");
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("MAXMP","("+2+")");
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setVar("prevLayerScale",0);
        glProg.setTexture("InputBuffer",brTex128);
        glProg.setTexture("MainBuffer",BaseFrame128);
        glProg.drawBlocks(vsmall,1);

        glProg.setDefine("SCANSIZE","("+tileSize+")");
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("MAXMP","("+3+")");
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setVar("prevLayerScale",4);
        glProg.setTexture("InputBuffer", brTex32);
        glProg.setTexture("AlignVectors",vsmall);
        glProg.setTexture("MainBuffer", BaseFrame32);
        glProg.drawBlocks(small,1);

        glProg.setDefine("SCANSIZE","("+tileSize/2+")");
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("MAXMP","("+2+")");
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setVar("prevLayerScale",4);
        glProg.setTexture("AlignVectors",small);
        glProg.setTexture("InputBuffer", brTex8);
        glProg.setTexture("MainBuffer", BaseFrame8);
        glProg.drawBlocks(medium,1);
        //small.close();

        glProg.setDefine("SCANSIZE","("+tileSize/2+")");
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.setDefine("MAXMP","("+1+")");
        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setVar("prevLayerScale",4);
        glProg.setTexture("AlignVectors",medium);
        glProg.setTexture("InputBuffer", brTex2);
        glProg.setTexture("MainBuffer", BaseFrame2);
        glProg.drawBlocks(alignVectors,1);
        //Log.d("Alignment","Size:"+alignVectors.mSize);
        //medium.close();

        //glUtils.convertVec4(brTex22,"in1.rg,0.5,1.0");
        //glUtils.SaveProgResult(large.mSize,"align");
        //endT("Alignment");
        //return large;


        /*glProg.useProgram(R.raw.pyramidalign);
        glProg.setTexture("InputBuffer", brTex3232);
        glProg.setTexture("MainBuffer", main3232);
        glProg.setVar("Mpy", 32);

        GLTexture out1 = new GLTexture(new Point(rawSize.x / (tileSize), rawSize.y / (tileSize)), new GLFormat(GLFormat.DataType.FLOAT_16, 2), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        glProg.setTexture("AlignVectors", out1);
        GLTexture out = new GLTexture(out1);
        glProg.drawBlocks(out);
        glProg.close();
        out1.close();
        glProg.useProgram(R.raw.pyramidalign);
        glProg.setTexture("InputBuffer", brTex88);
        glProg.setTexture("MainBuffer", main88);
        glProg.setVar("Mpy", 8);
        out1 = new GLTexture(out);
        glProg.setTexture("AlignVectors", out);
        glProg.drawBlocks(out1);
        glProg.close();
        out.close();
        glProg.useProgram(R.raw.pyramidalign);
        glProg.setTexture("InputBuffer", brTex22);
        glProg.setTexture("MainBuffer", main22);
        glProg.setVar("Mpy", 2);
        out = new GLTexture(out1);
        glProg.setTexture("AlignVectors", out1);
        glProg.drawBlocks(out);
        glProg.close();
        out1.close();
        endT("Alignment");
        glUtils.convertVec4(out,"/vec2("+(double)(tileSize)+")+vec2("+(1.5)+"),0.5,1.0");
        glUtils.SaveProgResult(out.mSize,"align");
        return out;*/


    }

    private void Weights() {
        //startT();
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.useProgram(R.raw.spatialweights);
        glProg.setTexture("InputBuffer22", brTex2);
        glProg.setTexture("MainBuffer22", BaseFrame2);
        glProg.setTexture("InputBuffer88", brTex8);
        glProg.setTexture("MainBuffer88", BaseFrame8);
        glProg.setTexture("InputBuffer32", brTex32);
        glProg.setTexture("MainBuffer32", BaseFrame32);
        glProg.setTexture("InputBuffer128", brTex128);
        glProg.setTexture("MainBuffer128", BaseFrame128);
        glProg.setTexture("AlignVectors", alignVectors);
        glProg.drawBlocks(Weights);
        //endT("Weights");
    }

    private GLTexture Merge(GLTexture Output, GLTexture inputRaw, GLTexture alignVectors, GLTexture weights, GLTexture mainFrame,int num) {
        //startT();
        glProg.setDefine("TILESIZE","("+tileSize+")");
        glProg.useProgram(R.raw.spatialmerge);
        glProg.setTexture("AlignVectors", alignVectors);
        glProg.setTexture("SpatialWeights", Weights);

        glProg.setTexture("MainBuffer", mainFrame);
        glProg.setTexture("InputBuffer", inputRaw);

        glProg.setTexture("InputBuffer22", brTex2);
        glProg.setTexture("MainBuffer22", BaseFrame2);

        glProg.setTexture("OutputBuffer", Output);
        glProg.setVar("alignk", 1.f / (float) (((RawPipeline) (basePipeline)).imageObj.size()));
        glProg.setVar("number",num+1);
        glProg.setVarU("rawsize", rawSize);
        glProg.setVarU("weightsize", weights.mSize);
        glProg.setVarU("alignsize", alignVectors.mSize);
        //GLTexture output = new GLTexture(rawSize,new GLFormat(GLFormat.DataType.FLOAT_16));
        GLTexture output = basePipeline.getMain();
        glProg.drawBlocks(output);
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
    GLTexture brTex2, brTex8, brTex32,brTex128;
    GLTexture alignVectors;
    GLTexture Weights;
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
        Log.d("AlignAndMerge", "Corrected raw elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        BaseFrame2 = new GLTexture(BaseFrame.mSize.x/2,BaseFrame.mSize.y/2,new GLFormat(GLFormat.DataType.FLOAT_16,4),GL_LINEAR,GL_CLAMP_TO_EDGE);
        basePipeline.main3 = new GLTexture(BaseFrame2);
        BoxDown22(BaseFrame, BaseFrame2);
        BaseFrame8 = new GLTexture(BaseFrame2.mSize.x/4, BaseFrame2.mSize.y/4, BaseFrame2.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);

        BaseFrame32 = new GLTexture(BaseFrame8.mSize.x/4, BaseFrame8.mSize.y/4, BaseFrame2.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);
        BaseFrame128 = new GLTexture(BaseFrame32.mSize.x/4, BaseFrame32.mSize.y/4, BaseFrame2.mFormat,GL_LINEAR,GL_CLAMP_TO_EDGE);
        GaussDown44(BaseFrame2, BaseFrame8,true);
        GaussDown44(BaseFrame8, BaseFrame32,true);
        GaussDown44(BaseFrame32,BaseFrame128,true);
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
        alignVectors = new GLTexture(new Point((brTex2.mSize.x / (tileSize))+added, (brTex2.mSize.y / (tileSize))+added), new GLFormat(GLFormat.DataType.FLOAT_16, 2),GL_LINEAR,GL_CLAMP_TO_EDGE);
        medium = new GLTexture(new Point((brTex8.mSize.x / (tileSize))+added, (brTex8.mSize.y / (tileSize))+added), new GLFormat(GLFormat.DataType.FLOAT_16, 2));
        small = new GLTexture(new Point((brTex32.mSize.x / (tileSize))+added, (brTex32.mSize.y / (tileSize))+added), new GLFormat(GLFormat.DataType.FLOAT_16, 2));
        vsmall = new GLTexture(new Point((brTex128.mSize.x / (tileSize))+added, (brTex128.mSize.y / (tileSize))+added), new GLFormat(GLFormat.DataType.FLOAT_16, 2));
        Weights = new GLTexture(new Point(brTex2.mSize.x/2, brTex2.mSize.y/2),new GLFormat(GLFormat.DataType.FLOAT_16),GL_LINEAR,GL_CLAMP_TO_EDGE);
        GLTexture inputraw = new GLTexture(BaseFrame);
        for (int i = 0; i < images.size(); i++) {
            CorrectedRaw(inputraw,i);
            //Less memory consumption
            if(i!=0) images.get(i).image.close();
            //long time2 = System.currentTimeMillis();
            BoxDown22(inputraw, brTex2);
            GaussDown44(brTex2, brTex8,true);
            GaussDown44(brTex8, brTex32,true);
            GaussDown44(brTex32,brTex128,true);
            //Log.d("AlignAndMerge", "Resize:" + (System.currentTimeMillis() - time2) + " ms");
            Align();
            //Weights();
            Output = Merge(Output, inputraw, alignVectors, alignVectors, BaseFrame,i);
        }
        alignVectors.close();
        inputraw.close();
        brTex2.close();
        brTex8.close();
        brTex32.close();
        Log.d("AlignAndMerge", "AlignmentAndMerge elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        WorkingTexture = RawOutput(Output);
    }
}
