package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;

public class GaussianResize extends GLOneScript {
    public GaussianResize(Point size, GLCoreBlockProcessing glCoreBlockProcessing, int rid, String name) {
        super(size, glCoreBlockProcessing, rid, name);
    }

    public GaussianResize(Point size, int rid, String name) {
        super(size, null, null, rid, name);
    }

    @Override
    public void StartScript() {
        ScriptParams scriptParams = (ScriptParams) additionalParams;
        GLProg glProg = glOne.glProgram;
        if (scriptParams.input == null) {
            glProg.setTexture("InputBuffer", scriptParams.textureInput);
        } else {
            Log.d("Script:" + Name, "Wrong parameters");
        }
        size = new Point(size.x / 4, size.y / 4);
        WorkingTexture = new GLTexture(size, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
    }
}
