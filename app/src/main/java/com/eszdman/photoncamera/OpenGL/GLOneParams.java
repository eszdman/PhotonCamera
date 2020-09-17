package com.eszdman.photoncamera.OpenGL;

import android.graphics.Bitmap;
import android.graphics.Point;

import com.eszdman.photoncamera.app.PhotonCamera;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class GLOneParams {
    public final GLProg glprogram;
    public final GLCoreBlockProcessing glProc;
    public GLOneParams(Point size, Bitmap out, GLFormat glFormat){
        glProc = new GLCoreBlockProcessing(size,out,glFormat);
        glprogram = glProc.mProgram;
    }
    public GLOneParams(GLCoreBlockProcessing glCoreBlockProcessing){
        glProc = glCoreBlockProcessing;
        glprogram = glProc.mProgram;
    }

    static public String loadShader(int fragment) {
        StringBuilder source = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(PhotonCamera.getMainActivity().getResources().openRawResource(fragment)));
        for (Object line : reader.lines().toArray()) {
            source.append(line).append("\n");
        }
        return source.toString();
    }
}
