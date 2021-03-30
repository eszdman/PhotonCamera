package com.particlesdevs.photoncamera.processing.opengl.rawpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.processor.ProcessorBase;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class HybridAlign extends Node {

    public HybridAlign() {
        super(0, "HybridAlign");
    }
    ArrayList<ImageFrame> images;
    public ArrayList<ByteBuffer> alignments;
    Point rawSize;

    private void CorrectedRaw(GLTexture out, int number) {
        glProg.useProgram(R.raw.precorrection);
        GLTexture inraw = new GLTexture(rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), images.get(number).buffer);
        glProg.setTexture("InputBuffer",inraw);
        glProg.setVar("WhiteLevel",(float)PhotonCamera.getParameters().whiteLevel);
        glProg.drawBlocks(out);
        inraw.close();
    }
    @Override
    public void Compile() {}

    private GLTexture Merge(GLTexture Output, GLTexture inputRaw, ByteBuffer alignVectors,int num) {
        glProg.setVar("alignk", 1.f / (float) (((RawPipeline) (basePipeline)).imageObj.size()));
        glProg.setVar("number",num+1);
        glProg.setVarU("rawsize", rawSize);
        GLTexture output = basePipeline.getMain();
        glProg.drawBlocks(output);
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

    @Override
    public void Run() {
        images = ((RawPipeline)basePipeline).images;
        alignments = ((RawPipeline)basePipeline).alignments;
        rawSize = ((RawPipeline)basePipeline).glint.parameters.rawSize;
        GLTexture inputraw = new GLTexture(rawSize,new GLFormat(GLFormat.DataType.FLOAT_16));
        GLTexture Output = new GLTexture(inputraw.mSize,inputraw.mFormat,images.get(0).buffer);
        for (int i = 1; i < images.size(); i++) {
            CorrectedRaw(inputraw,i);
            if(i!=0) images.get(i).image.close();

            Output = Merge(Output, inputraw, alignments.get(i),i);
        }
        WorkingTexture = RawOutput(Output);
    }
}
