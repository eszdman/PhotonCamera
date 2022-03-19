package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLOneParams;
import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;

import java.nio.ByteBuffer;

public class GaussianResize extends GLOneScript {
    public ByteBuffer inputB;
    public Point sizeIn;
    public GaussianResize(GLCoreBlockProcessing glCoreBlockProcessing) {
        super(new Point(1,1), glCoreBlockProcessing, "gaussdown44", "GaussianResize");
    }

    @Override
    public void StartScript() {
        GLProg glProg = glOne.glProgram;
        GLTexture input = new GLTexture(sizeIn,new GLFormat(GLFormat.DataType.FLOAT_16),inputB);
        glProg.setTexture("InputBuffer", input);
        WorkingTexture = new GLTexture(new Point(sizeIn.x / 4, sizeIn.y / 4), new GLFormat(GLFormat.DataType.FLOAT_16));
    }

    @Override
    public void Run() {
        Compile();
        startT();
        StartScript();
        WorkingTexture.BufferLoad();
        glOne.glProcessing.drawBlocksToOutput(WorkingTexture.mSize,WorkingTexture.mFormat,Output);
        AfterRun();
        endT();
        WorkingTexture.close();
    }
}
