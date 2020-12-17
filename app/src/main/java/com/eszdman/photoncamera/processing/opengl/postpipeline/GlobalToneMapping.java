package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.eszdman.photoncamera.processing.opengl.GLDrawParams;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.R;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class GlobalToneMapping extends Node {
    public GlobalToneMapping(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Compile() {}
    @Override
    public void Run() {
        GLTexture lowRes0 = glUtils.interpolate(previousNode.WorkingTexture,1.0/8.0);
        GLTexture lowRes = glUtils.interpolate(lowRes0,1.0/8.0);
        glProg.useProgram(R.raw.globaltonemaping);
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setTexture("LowRes",lowRes);
        glProg.setVar("insize",previousNode.WorkingTexture.mSize);
        glProg.setVar("lowsize",lowRes.mSize.x,lowRes.mSize.y);
        glProg.setVar("str",0.020f);
        GLTexture out1 = basePipeline.getMain();
        glProg.drawBlocks(out1);
        glProg.close();
        GLTexture lowRes2 = glUtils.interpolate(lowRes,1.0/8.0);
        lowRes.close();
        lowRes0.close();
        WorkingTexture = basePipeline.getMain();
        glProg.useProgram(R.raw.globaltonemaping);
        glProg.setTexture("InputBuffer",out1);
        glProg.setTexture("LowRes",lowRes2);
        glProg.setVar("insize",previousNode.WorkingTexture.mSize);
        glProg.setVar("lowsize",lowRes2.mSize.x,lowRes2.mSize.y);
        glProg.setVar("str",0.090f);
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
        lowRes2.close();
    }
}
