package com.eszdman.photoncamera.processing.opengl.scripts;

import android.graphics.Point;

import com.eszdman.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.eszdman.photoncamera.processing.opengl.GLOneScript;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.R;

public class NoiseDetection extends GLOneScript {
    public NoiseDetection(Point size) {
        super(size, null, null, R.raw.noisedetection44, "NoiseDetection444");
    }
    public NoiseDetection(Point size, GLCoreBlockProcessing glCoreBlockProcessing) {
        super(size, glCoreBlockProcessing, R.raw.noisedetection44, "NoiseDetection444");
    }
    @Override
    public void StartScript() {
        ScriptParams scriptParams = (ScriptParams)additionalParams;
        GLProg glProg = glOne.glprogram;
        glProg.setTexture("InputBuffer",scriptParams.textureInput);
        super.WorkingTexture = new GLTexture(scriptParams.textureInput.mSize,scriptParams.textureInput.mFormat,null);
    }
}
