package com.eszdman.photoncamera.processing.opengl.rawpipeline;

import android.accounts.OnAccountsUpdateListener;
import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.ImageFrame;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;

public class AlignAndMerge extends Node {
    Point rawSize;
    GLProg glProg;

    public AlignAndMerge(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Compile() {
    }

    private GLTexture CorrectedRaw(ByteBuffer input,GLTexture out) {
        glProg.useProgram(R.raw.precorrection);
        GLTexture inraw = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), input);
        glProg.setTexture("InputBuffer",inraw);
        glProg.setVar("WhiteLevel", (float) PhotonCamera.getParameters().realWL);
        glProg.drawBlocks(out);
        inraw.close();
        return out;
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
        glUtils.median(basePipeline.main3,out,new Point(1,1));
        //GLTexture median = glUtils.blur(output,1.5);
        //GLTexture median = glUtils.median(output,1.5);
        //glUtils.SaveProgResult(output.mSize,"aligninput");
        //GLTexture laplaced = glUtils.ops(blur,output,"-");
        //output.close();
        //return laplaced;
    }
    private void GaussDown44(GLTexture input,GLTexture out) {
        /*glProg.useProgram(R.raw.gaussdown44);
        glProg.setTexture("InputBuffer", input);
        GLTexture output = new GLTexture(new Point(input.mSize.x / 4, input.mSize.y / 4), input.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;*/
        //glUtils.gaussdown(input,out,4,1.5);
        glUtils.median(input,basePipeline.main3, new Point(1,1));
        glUtils.interpolate(basePipeline.main3,out,1.0/4.0);
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
    final int tileSize = 32;
    GLTexture medium;
    GLTexture small;
    private void Align(GLTexture brTex22, GLTexture brTex88, GLTexture brTex3232, GLTexture main22, GLTexture main88, GLTexture main3232,GLTexture large) {
        startT();



        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setVar("prevLayerScale",0);
        glProg.setTexture("InputBuffer",brTex3232);
        glProg.setVar("size",brTex3232.mSize);
        glProg.setTexture("MainBuffer",main3232);
        glProg.drawBlocks(small,true);

        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setVar("prevLayerScale",4);
        glProg.setTexture("AlignVectors",small);
        glProg.setVar("size",brTex88.mSize);
        glProg.setTexture("InputBuffer",brTex88);
        glProg.setTexture("MainBuffer",main88);
        glProg.drawBlocks(medium,true);
        //small.close();

        glProg.useProgram(R.raw.pyramidalign2);
        glProg.setVar("prevLayerScale",4);
        glProg.setTexture("AlignVectors",medium);
        glProg.setVar("size",brTex22.mSize);
        glProg.setTexture("InputBuffer",brTex22);
        glProg.setTexture("MainBuffer",main22);
        glProg.drawBlocks(large);
        Log.d("Alignment","Size:"+large.mSize);
        //medium.close();

        //glUtils.convertVec4(brTex22,"in1.rg,0.5,1.0");
        //glUtils.SaveProgResult(large.mSize,"align");
        endT("Alignment");
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

    private GLTexture Weights(GLTexture brTex22, GLTexture base22, GLTexture align) {
        startT();
        glProg.useProgram(R.raw.spatialweights);
        glProg.setTexture("InputBuffer22", brTex22);
        glProg.setTexture("MainBuffer22", base22);
        glProg.setTexture("AlignVectors", align);
        glProg.setVarU("rawsize", rawSize);
        GLTexture output = new GLTexture(new Point(align.mSize.x, align.mSize.y), new GLFormat(GLFormat.DataType.FLOAT_16), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        glProg.drawBlocks(output);
        glProg.close();
        endT("Weights");
        return output;
    }

    private GLTexture Merge(GLTexture Output, GLTexture inputRaw, GLTexture alignVectors, GLTexture weights, GLTexture mainFrame, GLTexture brTex22, GLTexture base22, int num) {
        startT();
        glProg.useProgram(R.raw.spatialmerge);
        glProg.setTexture("AlignVectors", alignVectors);
        //glProg.setTexture("SpatialWeights", weights);

        glProg.setTexture("MainBuffer", mainFrame);
        glProg.setTexture("InputBuffer", inputRaw);

        glProg.setTexture("InputBuffer22", brTex22);
        glProg.setTexture("MainBuffer22", base22);

        glProg.setTexture("OutputBuffer", Output);
        glProg.setVar("alignk", 1.f / (float) (((RawPipeline) (basePipeline)).imageObj.size()));
        glProg.setVar("number",num);
        glProg.setVarU("rawsize", rawSize);
        glProg.setVarU("weightsize", weights.mSize);
        glProg.setVarU("alignsize", alignVectors.mSize);
        //GLTexture output = new GLTexture(rawSize,new GLFormat(GLFormat.DataType.FLOAT_16));
        GLTexture output = basePipeline.getMain();
        glProg.drawBlocks(output);
        //glProg.drawBlocks(Output,128,true);
        //Output.close();
        endT("Merge");
        return output;
    }

    private GLTexture RawOutput(GLTexture input) {
        startT();
        glProg.useProgram(R.raw.toraw);
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("whitelevel", (float) PhotonCamera.getParameters().whiteLevel);
        GLTexture output = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), null);
        glProg.drawBlocks(output);
        glProg.closed = true;
        endT("RawOutput");
        return output;
    }

    @Override
    public void Run() {
        glProg = basePipeline.glint.glProgram;
        RawPipeline rawPipeline = (RawPipeline) basePipeline;
        rawSize = rawPipeline.glint.parameters.rawSize;
        ArrayList<ImageFrame> images = rawPipeline.images;
        long time = System.currentTimeMillis();
        GLTexture BaseFrame = new GLTexture(rawSize,new GLFormat(GLFormat.DataType.FLOAT_16));
        CorrectedRaw(images.get(0).buffer,BaseFrame);
        basePipeline.main2 = new GLTexture(BaseFrame);
        basePipeline.main1 = new GLTexture(BaseFrame);
        Log.d("AlignAndMerge", "Corrected raw elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        GLTexture BaseFrame22 = new GLTexture(BaseFrame.mSize.x/2,BaseFrame.mSize.y/2,BaseFrame.mFormat);
        basePipeline.main3 = new GLTexture(BaseFrame22);
        BoxDown22(BaseFrame,BaseFrame22);
        GLTexture BaseFrame88 = new GLTexture(BaseFrame22.mSize.x/4,BaseFrame22.mSize.y/4,BaseFrame22.mFormat);
        GaussDown44(BaseFrame22,BaseFrame88);
        GLTexture BaseFrame3232 = new GLTexture(BaseFrame88.mSize.x/4,BaseFrame88.mSize.y/4,BaseFrame22.mFormat);
        GaussDown44(BaseFrame88,BaseFrame3232);
        GLTexture Output = basePipeline.getMain();
        Log.d("AlignAndMerge", "Resize elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        time = System.currentTimeMillis();
        Log.d("AlignAndMerge","ImagesCount:"+images.size());
        GLTexture alignVectors,brTex22,brTex88,brTex3232;
        brTex22 = new GLTexture(BaseFrame22);
        brTex88 = new GLTexture(BaseFrame88);
        brTex3232 = new GLTexture(BaseFrame3232);
        alignVectors = new GLTexture(new Point(brTex22.mSize.x / (tileSize), brTex22.mSize.y / (tileSize)), new GLFormat(GLFormat.DataType.FLOAT_16, 3));
        medium = new GLTexture(new Point(brTex88.mSize.x / (tileSize), brTex88.mSize.y / (tileSize)), new GLFormat(GLFormat.DataType.FLOAT_16, 3));
        small = new GLTexture(new Point(brTex3232.mSize.x / (tileSize), brTex3232.mSize.y / (tileSize)), new GLFormat(GLFormat.DataType.FLOAT_16, 3));
        GLTexture inputraw = new GLTexture(BaseFrame);
        for (int i = 0; i < images.size(); i++) {
            CorrectedRaw(images.get(i).buffer,inputraw);
            //Less memory consumption
            if(i!=0) images.get(i).image.close();
            long time2 = System.currentTimeMillis();
            BoxDown22(inputraw,brTex22);
            GaussDown44(brTex22,brTex88);
            GaussDown44(brTex88,brTex3232);
            Log.d("AlignAndMerge", "Resize:" + (System.currentTimeMillis() - time2) + " ms");
            Align(brTex22, brTex88, brTex3232, BaseFrame22, BaseFrame88, BaseFrame3232, alignVectors);
            //GLTexture weights = Weights(brTex22, BaseFrame22, alignVectors);
            Output = Merge(Output, inputraw, alignVectors, alignVectors, BaseFrame, brTex22, BaseFrame22,i);
        }
        alignVectors.close();
        inputraw.close();
        brTex22.close();
        brTex88.close();
        brTex3232.close();
        Log.d("AlignAndMerge", "AlignmentAndMerge elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        WorkingTexture = RawOutput(Output);
    }
}
