package com.eszdman.photoncamera.OpenGL;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Point;

import com.eszdman.photoncamera.api.Interface;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class GLOneParams {
    public GLProg glprogram;
    public GLCoreBlockProcessing glProc;
    public GLOneParams(Point size, Bitmap out, GLFormat glFormat){
        glProc = new GLCoreBlockProcessing(size,out,glFormat);
        glprogram = glProc.mProgram;
    }
    @SuppressLint("NewApi")
    static public String loadShader(int fragment) {
        StringBuilder source = new StringBuilder();

        BufferedReader reader = new BufferedReader(new InputStreamReader(Interface.i.mainActivity.getResources().openRawResource(fragment)));
        for (Object line : reader.lines().toArray()) {
            source.append(line +"\n");
        }
        return source.toString();
    }
}
