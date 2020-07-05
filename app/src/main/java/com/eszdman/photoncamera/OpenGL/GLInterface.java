package com.eszdman.photoncamera.OpenGL;

import android.annotation.SuppressLint;

import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.api.Interface;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

public class GLInterface {
    public static GLInterface i;
    public GLProg glprogram;
    public Parameters parameters;
    public ByteBuffer inputRaw;
    public GLCoreBlockProcessing glProc;
    public GLInterface(GLCoreBlockProcessing processing){
        i = this;
        glProc = processing;
        glprogram = glProc.mProgram;
    }
    @SuppressLint("NewApi")
    static public String loadShader(int fragment) {
        StringBuilder source = new StringBuilder();

        BufferedReader reader = new BufferedReader(new InputStreamReader(Interface.i.mainActivity.getResources().openRawResource(fragment)));
        for (Object line : reader.lines().toArray()) {
            source.append((String) line+"\n");
        }
        return source.toString();
    }
}
