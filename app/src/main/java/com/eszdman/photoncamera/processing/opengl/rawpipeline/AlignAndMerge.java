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

    private GLTexture CorrectedRaw(ByteBuffer input) {
        glProg.useProgram(R.raw.precorrection);
        glProg.setTexture("InputBuffer", new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), input));
        glProg.setVar("WhiteLevel", (float) PhotonCamera.getParameters().realWL);
        GLTexture output = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.FLOAT_16), null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
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

    private GLTexture BoxDown22(GLTexture input) {
        glProg.useProgram(R.raw.boxdown22);
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("CfaPattern", PhotonCamera.getParameters().cfaPattern);
        GLTexture output = new GLTexture(new Point(rawSize.x / 2, rawSize.y / 2), new GLFormat(GLFormat.DataType.FLOAT_16,4), null);
        glProg.drawBlocks(output);
        //glUtils.SaveProgResult(output.mSize,"boxdown");
        //glProg.close();
        //GLTexture median = glUtils.blur(output,5.0);
        //GLTexture laplaced = glUtils.ops(median,output,"in2.rgb,3.0*(in1.a-in2.a)");
        //median.close();
        //median = glUtils.median(laplaced,new Point(1,1));
        //GLTexture median = glUtils.blur(output,1.5);
        //GLTexture median = glUtils.median(output,1.5);
        //glUtils.SaveProgResult(output.mSize,"aligninput");
        //GLTexture laplaced = glUtils.ops(blur,output,"-");
        //output.close();
        //return laplaced;
        return output;
    }
    private GLTexture GaussDown44(GLTexture input) {
        /*glProg.useProgram(R.raw.gaussdown44);
        glProg.setTexture("InputBuffer", input);
        GLTexture output = new GLTexture(new Point(input.mSize.x / 4, input.mSize.y / 4), input.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;*/
        GLTexture out = glUtils.gaussdown(input,4,1.5);
        return out;
    }

    private GLTexture LaplacDown44(GLTexture input) {
        glProg.useProgram(R.raw.laplaciandown44);
        glProg.setTexture("InputBuffer", input);
        GLTexture output = new GLTexture(new Point(input.mSize.x / 4, input.mSize.y / 4), input.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    private GLTexture Align(GLTexture brTex22, GLTexture brTex88, GLTexture brTex3232, GLTexture main22, GLTexture main88, GLTexture main3232) {
        startT();
        glProg.useProgram(R.raw.pyramidalign);
        glProg.setTexture("InputBuffer", brTex3232);
        glProg.setTexture("MainBuffer", main3232);
        glProg.setVar("Mpy", 32);
        glProg.setVar("maxSize", brTex3232.mSize.x, brTex3232.mSize.y);
        int tileSize = 128;
        GLTexture alignVectors = new GLTexture(new Point(rawSize.x / tileSize, rawSize.y / tileSize), new GLFormat(GLFormat.DataType.FLOAT_16, 2), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        glProg.setTexture("AlignVectors", alignVectors);
        glProg.drawBlocks(alignVectors);
        glProg.close();

        glProg.useProgram(R.raw.pyramidalign);
        glProg.setTexture("InputBuffer", brTex88);
        glProg.setTexture("MainBuffer", main88);
        glProg.setVar("Mpy", 8);
        glProg.setVar("minSize", (int) (brTex88.mSize.x * 0.08), (int) (brTex88.mSize.y * 0.08));
        glProg.setVar("maxSize", (int) (brTex88.mSize.x * (1.0 - 0.08)), (int) (brTex88.mSize.y * (1.0 - 0.08)));
        glProg.setTexture("AlignVectors", alignVectors);
        glProg.drawBlocks(alignVectors);
        glProg.close();

        glProg.useProgram(R.raw.pyramidalign);
        glProg.setTexture("InputBuffer", brTex22);
        glProg.setTexture("MainBuffer", main22);
        glProg.setVar("Mpy", 2);
        glProg.setVar("minSize", (int) (brTex22.mSize.x * 0.15), (int) (brTex22.mSize.y * 0.15));
        glProg.setVar("maxSize", (int) (brTex22.mSize.x * 0.83), (int) (brTex22.mSize.y * 0.83));
        glProg.setTexture("AlignVectors", alignVectors);
        glProg.drawBlocks(alignVectors);
        glProg.close();
        endT("Alignment");
        return alignVectors;
    }

    private GLTexture Weights(GLTexture brTex22, GLTexture base22, GLTexture align) {
        startT();
        glProg.useProgram(R.raw.spatialweights);
        glProg.setTexture("InputBuffer22", brTex22);
        glProg.setTexture("MainBuffer22", base22);
        glProg.setTexture("AlignVectors", align);
        glProg.setVarU("rawsize", rawSize);
        GLTexture output = new GLTexture(new Point(align.mSize.x, align.mSize.y), new GLFormat(GLFormat.DataType.FLOAT_16), null, GL_LINEAR, GL_CLAMP_TO_EDGE);
        glProg.drawBlocks(output);
        glProg.close();
        endT("Weights");
        return output;
    }

    private GLTexture Merge(GLTexture Output, GLTexture inputRaw, GLTexture alignVectors, GLTexture weights, GLTexture mainFrame, GLTexture brTex22, GLTexture base22) {
        startT();
        glProg.useProgram(R.raw.spatialmerge);
        glProg.setTexture("AlignVectors", alignVectors);
        //glProg.setTexture("SpatialWeights", weights);

        glProg.setTexture("MainBuffer", mainFrame);
        glProg.setTexture("InputBuffer", inputRaw);

        glProg.setTexture("InputBuffer22", brTex22);
        glProg.setTexture("MainBuffer22", base22);

        glProg.setTexture("OutputBuffer", Output);
        glProg.setVar("alignk", 1.f / (float) ((RawPipeline) (basePipeline)).imageObj.size());
        glProg.setVarU("rawsize", rawSize);
        glProg.setVarU("weightsize", weights.mSize);
        glProg.setVarU("alignsize", alignVectors.mSize);
        GLTexture output = new GLTexture(rawSize,new GLFormat(GLFormat.DataType.FLOAT_16),null);
        glProg.drawBlocks(output);
        //glProg.drawBlocks(Output,128,true);
        Output.close();
        glProg.close();
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
        glProg.close();
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
        GLTexture BaseFrame = CorrectedRaw(images.get(0).buffer);
        Log.d("AlignAndMerge", "Corrected raw elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        GLTexture BaseFrame22 = BoxDown22(BaseFrame);
        GLTexture BaseFrame88 = GaussDown44(BaseFrame22);
        GLTexture BaseFrame3232 = GaussDown44(BaseFrame88);
        GLTexture Output = CorrectedRaw(images.get(0).buffer);
        Log.d("AlignAndMerge", "Resize elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        time = System.currentTimeMillis();
        Log.d("AlignAndMerge","ImagesCount:"+images.size());
        for (int i = 1; i < images.size(); i++) {
            GLTexture inputraw = CorrectedRaw(images.get(i).buffer);
            //Less memory consumption
            images.get(i).image.close();
            long time2 = System.currentTimeMillis();
            GLTexture brTex22 = BoxDown22(inputraw);
            GLTexture brTex88 = GaussDown44(brTex22);
            GLTexture brTex3232 = GaussDown44(brTex88);
            Log.d("AlignAndMerge", "Resize:" + (System.currentTimeMillis() - time2) + " ms");
            GLTexture alignVectors = Align(brTex22, brTex88, brTex3232, BaseFrame22, BaseFrame88, BaseFrame3232);
            //GLTexture weights = Weights(brTex22, BaseFrame22, alignVectors);
            Output = Merge(Output, inputraw, alignVectors, alignVectors, BaseFrame, brTex22, BaseFrame22);
            alignVectors.close();
            inputraw.close();
            brTex22.close();
            brTex88.close();
            brTex3232.close();
        }
        Log.d("AlignAndMerge", "AlignmentAndMerge elapsed time:" + (System.currentTimeMillis() - time) + " ms");
        WorkingTexture = RawOutput(Output);
    }
}
