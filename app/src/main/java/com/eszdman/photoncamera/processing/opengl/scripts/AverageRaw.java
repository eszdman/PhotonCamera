package com.eszdman.photoncamera.processing.opengl.scripts;

import android.graphics.Point;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLOneScript;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;

import static com.eszdman.photoncamera.processing.ImageProcessing.unlimitedCounter;

public class AverageRaw extends GLOneScript {
    GLTexture in1,in2,oldWT;
    private GLProg glProg;
    public AverageRaw(Point size, String name) {
        super(size, new GLCoreBlockProcessing(size,new GLFormat(GLFormat.DataType.UNSIGNED_16)), R.raw.average, name);
    }

    @Override
    public void Run() {
        Compile();
        startT();

        AverageParams scriptParams = (AverageParams)additionalParams;
        glProg = glOne.glProgram;
        in1 = oldWT;
        in2 = new GLTexture(size,new GLFormat(GLFormat.DataType.UNSIGNED_16),scriptParams.inp2);
        if(in1 == null){
            glProg.setVar("first",1);
        } else {
            glProg.setVar("first",0);
            glProg.setTexture("InputBuffer", in1);
        }
        glProg.setTexture("InputBuffer2",in2);
        glProg.setVar("blacklevel", PhotonCamera.getParameters().blackLevel);
        glProg.setVar("unlimitedcount",Math.min(unlimitedCounter,1000));
        WorkingTexture = new GLTexture(size,new GLFormat(GLFormat.DataType.FLOAT_16));

        //WorkingTexture.BufferLoad();
        glProg.drawBlocks(WorkingTexture);
        //glOne.glProcessing.drawBlocksToOutput();
        oldWT = WorkingTexture;
        AfterRun();
        //glOne.glProgram.close();
        endT();
        //Output = glOne.glProcessing.mOutBuffer;
        //WorkingTexture.close();
    }

    public void FinalScript(){
        glProg = glOne.glProgram;
        glProg.useProgram(R.raw.toraw);
        glProg.setTexture("InputBuffer",WorkingTexture);
        glProg.setVar("whitelevel",1.f);
        //in1 = WorkingTexture;
        in1 = new GLTexture(size,new GLFormat(GLFormat.DataType.UNSIGNED_16));

        in1.BufferLoad();
        glOne.glProcessing.drawBlocksToOutput();
        in1.close();
        glProg.close();
        WorkingTexture.close();
        Output = glOne.glProcessing.mOutBuffer;
    }

    @Override
    public void AfterRun() {
        in2.close();

    }
}