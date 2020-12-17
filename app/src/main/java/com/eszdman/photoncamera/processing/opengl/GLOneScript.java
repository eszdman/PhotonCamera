package com.eszdman.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.postpipeline.PostPipeline;

import java.nio.ByteBuffer;

public class GLOneScript implements AutoCloseable {
    public GLTexture WorkingTexture;
    public GLOneParams glOne;
    public final String Name;
    public ByteBuffer Output;
    public final int Rid;
    private long timeStart;
    public Point size;
    public GLFormat glFormat;
    Bitmap outbit;
    public Object additionalParams;
    public boolean hiddenScript = false;

    public GLOneScript(Point size, Bitmap output, GLFormat glFormat, int rid, String name) {
        outbit = output;
        this.glFormat = glFormat;
        this.size = size;
        Name = name;
        Rid = rid;
    }

    public GLOneScript(Point size, GLCoreBlockProcessing glCoreBlockProcessing, int rid, String name) {
        this.size = size;
        glOne = new GLOneParams(glCoreBlockProcessing);
        Name = name;
        Rid = rid;
    }

    public void StartScript() {
    }

    public void Run() {
        Point sizeo = new Point(size);
        if (glFormat == null) {
            hiddenScript = true;
            glFormat = new GLFormat(GLFormat.DataType.UNSIGNED_8, 4);
            sizeo = new Point(1, 1);
        }
        if (Output == null)
            glOne = new GLOneParams(sizeo, outbit, glFormat);
        else {
            glOne = new GLOneParams(sizeo, outbit, glFormat, Output);
        }
        Compile();
        startT();
        StartScript();
        if (!hiddenScript) {
            //glOne.glProgram.drawBlocks(WorkingTexture);
            WorkingTexture.BufferLoad();
            glOne.glProcessing.drawBlocksToOutput();

        } else {
            glOne.glProgram.drawBlocks(WorkingTexture);
        }
        AfterRun();
        endT();
        Output = glOne.glProcessing.mOutBuffer;
    }
    public void AfterRun(){

    }

    public void startT() {
        timeStart = System.currentTimeMillis();
    }

    public void endT() {
        Log.d("OneScript", "Name:" + Name + " elapsed:" + (System.currentTimeMillis() - timeStart) + " ms");
    }

    public void Compile() {
        glOne.glProgram.useProgram(Rid);
    }

    @Override
    public void close() {
        glOne.glProgram.close();
        glOne.glProcessing.close();
    }
}
