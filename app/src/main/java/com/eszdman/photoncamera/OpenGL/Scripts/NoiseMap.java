package com.eszdman.photoncamera.OpenGL.Scripts;

import com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.R;
public class NoiseMap {
    public static GLTexture Run(GLTexture input, GLCoreBlockProcessing glCoreBlockProcessing){
        NoiseDetection detection = new NoiseDetection(input.mSize,glCoreBlockProcessing);
        ScriptParams params = new ScriptParams();
        params.textureInput = input;
        detection.additionalParams = params;
        detection.Run();
        GaussianResize resize = new GaussianResize(detection.WorkingTexture.mSize,glCoreBlockProcessing,R.raw.gaussdown444,"GaussDown444");
        params.textureInput = detection.WorkingTexture;
        resize.additionalParams = params;
        resize.Run();
        detection.WorkingTexture.close();
        return resize.WorkingTexture;
    }
}
