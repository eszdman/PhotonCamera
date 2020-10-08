package com.eszdman.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import java.nio.ByteBuffer;

public class GLOneScript implements AutoCloseable {
    public GLTexture WorkingTexture;
    public final GLOneParams glOne;
    public final String Name;
    public ByteBuffer Output;
    public final int Rid;
    private long timeStart;
    public Point size;
    public Object additionalParams;
    public boolean hiddenScript = false;

    public GLOneScript(Point size, Bitmap output, GLFormat glFormat, int rid, String name) {
        this.size = size;
        if (glFormat == null) {
            hiddenScript = true;
            glFormat = new GLFormat(GLFormat.DataType.UNSIGNED_8, 4);
            glOne = new GLOneParams(new Point(1, 1), output, glFormat);
        } else {
            glOne = new GLOneParams(size, output, glFormat);
        }

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
        Compile();
        startT();
        StartScript();
        if (!hiddenScript) {
            glOne.glProcessing.drawBlocksToOutput();
        } else {
            glOne.glProgram.drawBlocks(WorkingTexture);
        }
        glOne.glProgram.close();
        endT();
        Output = glOne.glProcessing.mOutBuffer;
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
        glOne.glProcessing.close();
    }
}
