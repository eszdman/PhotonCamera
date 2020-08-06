package com.eszdman.photoncamera.OpenGL.Nodes.RawPipeline;

import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class AlignAndMerge extends Node {
    private final int TileSize = 32;
    Point rawsize;
    GLProg glProg;
    public AlignAndMerge(int rid, String name) {
        super(rid, name);
    }
    private GLTexture CorrectedRaw(ByteBuffer input){
        glProg.useProgram(R.raw.precorrection);
        glProg.setTexture("InputBuffer",new GLTexture(rawsize,new GLFormat(GLFormat.DataType.UNSIGNED_16),input));
        glProg.setvar("WhiteLevel",(float)Interface.i.parameters.whitelevel);
        GLTexture output = new GLTexture(rawsize,new GLFormat(GLFormat.DataType.FLOAT_32),null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    private GLTexture BoxDown22(GLTexture input){
        glProg.useProgram(R.raw.boxdown22);
        glProg.setTexture("InputBuffer",input);
        GLTexture output = new GLTexture(new Point(rawsize.x/2,rawsize.y/2),new GLFormat(GLFormat.DataType.FLOAT_16),null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    private GLTexture GaussDown44(GLTexture input){
        glProg.useProgram(R.raw.gaussdown44);
        glProg.setTexture("InputBuffer",input);
        GLTexture output = new GLTexture(new Point(input.mSize.x/4,input.mSize.y/4),input.mFormat,null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    private GLTexture Align(GLTexture brTex22,GLTexture brTex88,GLTexture brTex3232,GLTexture main22,GLTexture main88,GLTexture main3232){

        glProg.useProgram(R.raw.pyramidalign);
        glProg.setTexture("InputBuffer",brTex3232);
        glProg.setTexture("MainBuffer",main3232);
        glProg.setvar("Mpy", 32);
        GLTexture alignVectors = new GLTexture(new Point(rawsize.x/TileSize,rawsize.y/TileSize),new GLFormat(GLFormat.DataType.UNSIGNED_8,2),null);
        glProg.setTexture("AlignVectors",alignVectors);
        glProg.drawBlocks(alignVectors);
        glProg.close();

        glProg.useProgram(R.raw.pyramidalign);
        glProg.setTexture("InputBuffer",brTex88);
        glProg.setTexture("MainBuffer",main88);
        glProg.setvar("Mpy", 8);
        glProg.setTexture("AlignVectors",alignVectors);
        glProg.drawBlocks(alignVectors);
        glProg.close();

        glProg.useProgram(R.raw.pyramidalign);
        glProg.setTexture("InputBuffer",brTex22);
        glProg.setTexture("MainBuffer",main22);
        glProg.setvar("Mpy", 2);
        glProg.setTexture("AlignVectors",alignVectors);
        glProg.drawBlocks(alignVectors);
        glProg.close();

        return alignVectors;
    }
    private GLTexture Merge(GLTexture Output,GLTexture inputraw,GLTexture alignVectors, GLTexture mainFrame22,GLTexture brTex22){
        glProg.useProgram(R.raw.spatialmerge);
        glProg.setTexture("InputBuffer",inputraw);
        glProg.setTexture("AlignVectors",alignVectors);
        glProg.setTexture("OutputBuffer",Output);
        glProg.setTexture("MainBuffer",mainFrame22);
        glProg.servaru("rawsize",rawsize.x,rawsize.y);
        //GLTexture output = new GLTexture(rawsize,new GLFormat(GLFormat.DataType.FLOAT_16),null);
        glProg.drawBlocks(Output);
        glProg.close();
        return Output;
    }
    private GLTexture RawOutput(GLTexture input){
        glProg.useProgram(R.raw.toraw);
        glProg.setTexture("InputBuffer",input);
        glProg.setvar("whitelevel", (float)Interface.i.parameters.whitelevel);
        GLTexture output = new GLTexture(rawsize,new GLFormat(GLFormat.DataType.UNSIGNED_16),null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    @Override
    public void Run() {
        glProg = basePipeline.glint.glprogram;
        RawPipeline rawPipeline = (RawPipeline)basePipeline;
        rawsize = rawPipeline.glint.parameters.rawSize;
        ArrayList<ByteBuffer> images = rawPipeline.images;
        long time = System.currentTimeMillis();
        GLTexture BaseFrame = CorrectedRaw(images.get(0));
        Log.d("AlignAndMerge","Corrected raw elapsed time:"+(System.currentTimeMillis()-time)+" ms");
        GLTexture BaseFrame22 = BoxDown22(BaseFrame);
        GLTexture BaseFrame88 = GaussDown44(BaseFrame22);
        GLTexture BaseFrame3232 = GaussDown44(BaseFrame88);
        Log.d("AlignAndMerge","Resize elapsed time:"+(System.currentTimeMillis()-time)+" ms");
        GLTexture Output = BaseFrame;
        time = System.currentTimeMillis();
        for(int i =1; i<images.size();i++){
            GLTexture inputraw = CorrectedRaw(images.get(i));
            GLTexture brTex22 = BoxDown22(inputraw);
            GLTexture brTex88 = GaussDown44(brTex22);
            GLTexture brTex3232 = GaussDown44(brTex88);
            GLTexture AlignVectors = Align(brTex22,brTex88,brTex3232,BaseFrame22,BaseFrame88,BaseFrame3232);
            Output = Merge(Output,inputraw,AlignVectors,BaseFrame22,brTex22);
        }
        Log.d("AlignAndMerge","AlignmentAndMerge elapsed time:"+(System.currentTimeMillis()-time)+" ms");
        WorkingTexture = RawOutput(Output);
    }
}
