package com.eszdman.photoncamera.OpenGL;

import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.api.Interface;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class GLInterface {
    public final GLProg glprogram;
    public Parameters parameters;
    public GLCoreBlockProcessing glProc;
    public GLContext glContext;
    public GLInterface(GLCoreBlockProcessing processing){
        glProc = processing;
        glprogram = glProc.mProgram;
    }

    public GLInterface(GLContext context){
        glContext = context;
        glprogram = glContext.mProgram;
    }

    static public String loadShader(int fragment) {
        StringBuilder source = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(Interface.getMainActivity().getResources().openRawResource(fragment)));
        for (Object line : reader.lines().toArray()) {
            source.append(line).append("\n");
        }
        return source.toString();
    }
}
