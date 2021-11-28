package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;

import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

public class GLOneParams {
    public final GLProg glProgram;
    public final GLCoreBlockProcessing glProcessing;
    public GLOneParams(Point size, Bitmap out, GLFormat glFormat, ByteBuffer outbuffer) {
        glProcessing = new GLCoreBlockProcessing(size, out, glFormat,outbuffer);
        glProgram = glProcessing.mProgram;
    }
    public GLOneParams(Point size, Bitmap out, GLFormat glFormat) {
        glProcessing = new GLCoreBlockProcessing(size, out, glFormat, GLDrawParams.Allocate.Direct);
        glProgram = glProcessing.mProgram;
    }

    public GLOneParams(GLCoreBlockProcessing glCoreBlockProcessing) {
        glProcessing = glCoreBlockProcessing;
        glProgram = glProcessing.mProgram;
    }

    static public String loadShader(int fragment) {
        StringBuilder source = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(PhotonCamera.getResourcesStatic().openRawResource(fragment)));
        for (Object line : reader.lines().toArray()) {
            source.append(line).append("\n");
        }
        return source.toString();
    }
}
