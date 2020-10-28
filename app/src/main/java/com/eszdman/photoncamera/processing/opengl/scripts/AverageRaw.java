package com.eszdman.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLOneScript;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;

import static com.eszdman.photoncamera.processing.ImageProcessing.unlimitedCounter;

public class AverageRaw extends GLOneScript {
    GLTexture oldWT,in2;
    private GLProg glProg;
    public AverageRaw(Point size, String name) {
        super(size, new GLCoreBlockProcessing(size,new GLFormat(GLFormat.DataType.UNSIGNED_16)), R.raw.average, name);
    }
    @Override
    public void StartScript() {
        hiddenScript = true;
        glProg = glOne.glProgram;
        AverageParams scriptParams = (AverageParams)additionalParams;
        in2 = new GLTexture(size,new GLFormat(GLFormat.DataType.UNSIGNED_16),scriptParams.inp2);
            if(WorkingTexture != null) {
                glProg.setTexture("InputBuffer",WorkingTexture);
                glProg.setVar("first",0);
            } else{
                WorkingTexture = new GLTexture(size,new GLFormat(GLFormat.DataType.UNSIGNED_16),scriptParams.inp1);
                glProg.setVar("first",1);
            }

        glProg.setTexture("InputBuffer2",in2);
        glProg.setVar("blacklevel", PhotonCamera.getParameters().blackLevel);
        Log.v(Name,"cnt:"+unlimitedCounter);
        glProg.setVar("unlimitedcount",Math.min(unlimitedCounter,100));
        oldWT = WorkingTexture;
        WorkingTexture = new GLTexture(oldWT.mSize,new GLFormat(GLFormat.DataType.FLOAT_16));
    }
    public void FinalScript(){
        glProg = glOne.glProgram;
        glProg.useProgram(R.raw.toraw);
        glProg.setTexture("InputBuffer",WorkingTexture);
        glProg.setVar("whitelevel",1023.f);
        oldWT = WorkingTexture;
        WorkingTexture = new GLTexture(size,new GLFormat(GLFormat.DataType.UNSIGNED_16));
        WorkingTexture.BufferLoad();
        glOne.glProcessing.drawBlocksToOutput();
        oldWT.close();
        glProg.close();
        Output = glOne.glProcessing.mOutBuffer;
    }

    @Override
    public void AfterRun() {
        oldWT.close();
        in2.close();
    }
}