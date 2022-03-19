package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;

public class NoiseDetection extends GLOneScript {
    public NoiseDetection(Point size) {
        super(size, null, null, "noisedetection44", "NoiseDetection444");
    }

    public NoiseDetection(Point size, GLCoreBlockProcessing glCoreBlockProcessing) {
        super(size, glCoreBlockProcessing, "noisedetection44", "NoiseDetection444");
    }

    @Override
    public void StartScript() {
        ScriptParams scriptParams = (ScriptParams) additionalParams;
        GLProg glProg = glOne.glProgram;
        glProg.setTexture("InputBuffer", scriptParams.textureInput);
        super.WorkingTexture = new GLTexture(scriptParams.textureInput.mSize, scriptParams.textureInput.mFormat, null);
    }
}
