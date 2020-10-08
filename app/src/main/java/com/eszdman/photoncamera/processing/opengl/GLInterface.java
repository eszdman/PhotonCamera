package com.eszdman.photoncamera.processing.opengl;

import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.render.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class GLInterface {
    public final GLProg glProgram;
    public Parameters parameters;
    public GLCoreBlockProcessing glProcessing;
    public GLContext glContext;
    public GLUtils glUtils;

    public GLInterface(GLCoreBlockProcessing processing) {
        glProcessing = processing;
        glProgram = glProcessing.mProgram;
    }

    public GLInterface(GLContext context) {
        glContext = context;
        glProgram = glContext.mProgram;
    }

    static public String loadShader(int fragment) {
        StringBuilder source = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(PhotonCamera.getCameraActivity().getResources().openRawResource(fragment)));
        for (Object line : reader.lines().toArray()) {
            source.append(line).append("\n");
        }
        return source.toString();
    }
}
