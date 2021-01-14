package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.R;

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
        Log.d(Name,"whitelevel old:"+oldWhiteLevel);
        Log.d(Name,"whitelevel sensivity:"+sensitivity);
        glProg.setVar("sensivity",sensitivity);
        WorkingTexture = new GLTexture(inp);
    }

    @Override
    public void AfterRun() {
        inp.close();
    }
}
