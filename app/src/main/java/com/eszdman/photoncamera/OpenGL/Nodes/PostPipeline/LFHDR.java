package com.eszdman.photoncamera.OpenGL.Nodes.PostPipeline;

import android.graphics.Point;

import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.OpenGL.Nodes.Node;
import com.eszdman.photoncamera.R;
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
        GLTexture output = new GLTexture(new Point(input.mSize.x/4,input.mSize.y/4),input.mFormat,null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    GLTexture Blur(GLTexture input){
        glProg.useProgram(R.raw.gaussblur554);
        glProg.setTexture("InputBuffer",input);
        GLTexture output = new GLTexture(new Point(input.mSize.x/4,input.mSize.y/4),input.mFormat,null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    GLTexture ApplyMask(GLTexture input,GLTexture mask){
        glProg.useProgram(R.raw.add444);
        glProg.setTexture("InputBuffer",input);
        glProg.setTexture("InputBuffer2",mask);
        GLTexture output = new GLTexture(new Point(input.mSize.x/4,input.mSize.y/4),input.mFormat,null);
        glProg.drawBlocks(output);
        glProg.close();
        return output;
    }
    GLTexture MergeHDR(GLTexture inputlow,GLTexture inputhigh){
        return null;
    }
    GLTexture Debayer(GLTexture input){
        return null;
    }
    @Override
    public void Run() {
        glProg = basePipeline.glint.glprogram;
        GLTexture inputstacking = null;
        GLTexture inputhdrlow = null;
        GLTexture inputhdrhigh = null;
        GLTexture MaskStacking = SharpMask(Debayer(inputstacking));
        GLTexture BlurredHDR = MergeHDR(Debayer(inputhdrlow),Debayer(inputhdrhigh));
        WorkingTexture = ApplyMask(BlurredHDR,MaskStacking);
    }
}
