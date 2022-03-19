package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.Parameters;

public class LFHDR extends Node {
    GLProg glProg;

    public LFHDR() {
        super("", "LFHDR");
    }

    @Override
    public void Compile() {
    }

    GLTexture SharpMask(GLTexture input) {
        glProg.useAssetProgram("laplacian554");
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("size", 1.7f);
        Log.d(Name, "Sharp mFormat:" + input.mFormat.toString());
        GLTexture output = new GLTexture(new Point(input.mSize.x, input.mSize.y), input.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    GLTexture Blur(GLTexture input) {
        glProg.useAssetProgram("gaussblur554");
        glProg.setTexture("InputBuffer", input);
        glProg.setVar("size", 1.7f);
        GLTexture output = new GLTexture(new Point(input.mSize.x, input.mSize.y), input.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    GLTexture ApplyMask(GLTexture input, GLTexture mask) {
        glProg.useAssetProgram("add4");
        glProg.setTexture("InputBuffer", input);
        glProg.setTexture("InputBuffer2", mask);
        GLTexture output = new GLTexture(new Point(input.mSize.x, input.mSize.y), input.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    GLTexture MergeHDR(GLTexture inputLow, GLTexture inputHigh) {
        glProg.useAssetProgram("mergehdr");
        glProg.setTexture("InputBufferLow", inputLow);
        glProg.setTexture("InputBufferHigh", inputHigh);
        GLTexture output = new GLTexture(inputLow.mSize, inputLow.mFormat, null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }

    GLTexture Debayer(GLTexture input) {
        GLInterface glint = basePipeline.glint;
        GLProg glProg = glint.glProgram;
        Parameters params = glint.parameters;
        glProg.useAssetProgram("demosaicp1");
        glProg.setTexture("RawBuffer", input);
        glProg.setVar("WhiteLevel", params.whiteLevel);
        glProg.setVar("CfaPattern", params.cfaPattern);
        GLTexture Output = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16), null);
        glProg.drawBlocks(Output);
        glProg.close();

        glProg.useAssetProgram("demosaicp2");
        glProg.setTexture("RawBuffer", input);
        glProg.setTexture("GreenBuffer", Output);
        glProg.setVar("WhiteLevel", params.whiteLevel);
        glProg.setVar("CfaPattern", params.cfaPattern);
        GLTexture output = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
        glProg.drawBlocks(output);
        glProg.close();

        return output;
    }

    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline) (basePipeline);
        glProg = basePipeline.glint.glProgram;
        GLTexture inputstacking = new GLTexture(basePipeline.glint.parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), postPipeline.stackFrame);
        GLTexture inputhdrlow = new GLTexture(basePipeline.glint.parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), postPipeline.lowFrame);
        GLTexture inputhdrhigh = new GLTexture(basePipeline.glint.parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), postPipeline.highFrame);
        GLTexture MaskStacking = SharpMask(Debayer(inputstacking));
        GLTexture BlurredHDR = Blur(MergeHDR(Debayer(inputhdrlow), Debayer(inputhdrhigh)));
        WorkingTexture = ApplyMask(BlurredHDR, MaskStacking);
        //WorkingTexture = MaskStacking;
        //WorkingTexture = SharpMask(Debayer(inputstacking));
    }
}
