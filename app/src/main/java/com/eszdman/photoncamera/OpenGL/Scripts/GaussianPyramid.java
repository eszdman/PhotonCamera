package com.eszdman.photoncamera.OpenGL.Scripts;

import android.graphics.Point;

import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.R;
import java.nio.ByteBuffer;

public class GaussianPyramid {
public static GLTexture[] Run(Point size, GLTexture input){
    GLTexture Output[] = new GLTexture[1];
    GaussianResize resizer = new GaussianResize(size,R.raw.gaussdown44,"GaussDown44");
    ScriptParams parameters = new ScriptParams();
    parameters.textureInput = input;
    resizer.additionalParams = parameters;
    resizer.Run();
    Output[0] = resizer.WorkingTexture;
    return Output;
    }
}
