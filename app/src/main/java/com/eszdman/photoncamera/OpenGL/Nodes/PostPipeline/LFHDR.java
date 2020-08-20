package com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline;

import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;

public class LFHDR  extends Node {
    GLProg glProg;
    public LFHDR(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Compile() {}
    GLTexture SharpMask(GLTexture input){
        glProg.useProgram(R.raw.laplacian554);
        glProg.setTexture("InputBuffer",input);
        glProg.setvar("size",1.7f);
        Log.d(Name,"Sharp mFormat:"+input.mFormat.toString());
        GLTexture output = new GLTexture(new Point(input.mSize.x,input.mSize.y),input.mFormat,null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    GLTexture Blur(GLTexture input){
        glProg.useProgram(R.raw.gaussblur554);
        glProg.setTexture("InputBuffer",input);
        glProg.setvar("size",1.7f);
        GLTexture output = new GLTexture(new Point(input.mSize.x,input.mSize.y),input.mFormat,null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    GLTexture ApplyMask(GLTexture input,GLTexture mask){
        glProg.useProgram(R.raw.add4);
        glProg.setTexture("InputBuffer",input);
        glProg.setTexture("InputBuffer2",mask);
        GLTexture output = new GLTexture(new Point(input.mSize.x,input.mSize.y),input.mFormat,null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    GLTexture MergeHDR(GLTexture inputlow,GLTexture inputhigh){
        glProg.useProgram(R.raw.mergehdr);
        glProg.setTexture("InputBufferLow",inputlow);
        glProg.setTexture("InputBufferHigh",inputhigh);
        GLTexture output = new GLTexture(inputlow.mSize,inputlow.mFormat,null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    GLTexture Debayer(GLTexture input){
        GLInterface glint = basePipeline.glint;
        GLProg glProg = glint.glprogram;
        Parameters params = glint.parameters;
        glProg.useProgram(R.raw.demosaicp1);
        glProg.setTexture("RawBuffer",input);
        glProg.setvar("WhiteLevel",params.whitelevel);
        glProg.setvar("CfaPattern",params.cfaPattern);
        GLTexture Output = new GLTexture(params.rawSize,new GLFormat(GLFormat.DataType.FLOAT_16),null);
        glProg.drawBlocks(Output);
        glProg.close();

        glProg.useProgram(R.raw.demosaicp2);
        glProg.setTexture("RawBuffer",input);
        glProg.setTexture("GreenBuffer",Output);
        glProg.setvar("WhiteLevel",params.whitelevel);
        glProg.setvar("CfaPattern",params.cfaPattern);
        GLTexture output = new GLTexture(params.rawSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
        glProg.drawBlocks(output);
        glProg.close();

        return output;
    }
    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline)(basePipeline);
        glProg = basePipeline.glint.glprogram;
        GLTexture inputstacking = new GLTexture(basePipeline.glint.parameters.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_16),postPipeline.stackFrame);
        GLTexture inputhdrlow = new GLTexture(basePipeline.glint.parameters.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_16),postPipeline.lowFrame);;
        GLTexture inputhdrhigh = new GLTexture(basePipeline.glint.parameters.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_16),postPipeline.highFrame);;
        GLTexture MaskStacking = SharpMask(Debayer(inputstacking));
        GLTexture BlurredHDR = Blur(MergeHDR(Debayer(inputhdrlow),Debayer(inputhdrhigh)));
        WorkingTexture = ApplyMask(BlurredHDR,MaskStacking);
        //WorkingTexture = MaskStacking;
        //WorkingTexture = SharpMask(Debayer(inputstacking));
    }
}
