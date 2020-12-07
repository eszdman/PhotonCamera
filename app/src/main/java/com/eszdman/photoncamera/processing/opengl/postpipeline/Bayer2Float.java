package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLDrawParams;
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
        glProg.setVar("patSize",2);
        glProg.setVar("whitePoint",parameters.whitePoint);
        Log.d(Name,"whitelevel:"+parameters.whiteLevel);
        glProg.setVarU("whitelevel",(parameters.whiteLevel));
        Log.d(Name,"CfaPattern:"+parameters.cfaPattern);
        basePipeline.main1 = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        basePipeline.main2 = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        basePipeline.main3 = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, GLDrawParams.WorkDim));
        WorkingTexture = basePipeline.main2;
        /*glProg.drawBlocks(basePipeline.main3);
        glProg.useProgram(R.raw.demosaicantiremosaic);
        glProg.setTexture("RawBuffer",basePipeline.main3);*/


        //glUtils.convertVec4(WorkingTexture,"in1.rgb,1.0");
        //glUtils.SaveProgResult(in.mSize,"bayer",4,".jpg");

        glProg.drawBlocks(WorkingTexture);

        glProg.closed = true;
    }
}
