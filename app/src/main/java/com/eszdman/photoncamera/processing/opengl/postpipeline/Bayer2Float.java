package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLConst;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

public class Bayer2Float extends Node {

    public Bayer2Float(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Compile() {}

    @Override
    public void Run() {
        Parameters parameters = PhotonCamera.getParameters();
        GLTexture in = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16), ((PostPipeline)(basePipeline)).stackFrame);
        glProg.useProgram(R.raw.tofloat);
        glProg.setTexture("InputBuffer",in);
        glProg.setVar("CfaPattern",parameters.cfaPattern);
        glProg.setVar("whitePoint",parameters.whitePoint);
        glProg.setVar("whitelevel",(float)(parameters.whiteLevel));
        Log.d(Name,"CfaPattern:"+parameters.cfaPattern);
        basePipeline.main1 = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim));
        basePipeline.main2 = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim));
        basePipeline.main3 = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim));
        glProg.drawBlocks(basePipeline.main3);
        /*glProg.useProgram(R.raw.medianfilterhotpixel);
        glProg.setVar("CfaPattern",parameters.cfaPattern);
        glProg.setTexture("InputBuffer",basePipeline.main2);
        glProg.drawBlocks(basePipeline.main3);*/
        //glUtils.blursmall(basePipeline.main2,basePipeline.main3,3,0.25);
        WorkingTexture = basePipeline.main3;
        glProg.closed = true;
    }
}
