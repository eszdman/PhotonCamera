package com.eszdman.photoncamera.processing.opengl.scripts;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLOneScript;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.R;

import java.nio.ByteBuffer;

public class RawSensivity extends GLOneScript {
    public float sensitivity;
    public float oldWhiteLevel;
    public ByteBuffer input;
    public RawSensivity(Point size) {
        super(size, null, new GLFormat(GLFormat.DataType.UNSIGNED_16), R.raw.rawsensivity, "RawSensivity");
    }
    GLTexture inp;
    @Override
    public void StartScript() {
        GLProg glProg = glOne.glProgram;
        inp = new GLTexture(size, new GLFormat(GLFormat.DataType.UNSIGNED_16),input);
        glProg.setTexture("RawBuffer",inp);
        glProg.setVar("whitelevel",oldWhiteLevel);
        glProg.setVar("sensivity",sensitivity);
        WorkingTexture = new GLTexture(inp);
    }

    @Override
    public void AfterRun() {
        inp.close();
    }
}
