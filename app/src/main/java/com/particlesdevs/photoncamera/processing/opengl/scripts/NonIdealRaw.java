package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class NonIdealRaw extends GLOneScript {
    public ByteBuffer inp;
    public ByteBuffer prevmap;
    public Parameters parameters;
    public NonIdealRaw(Point size) {
        super(size, new GLCoreBlockProcessing(size,new GLFormat(GLFormat.DataType.UNSIGNED_16), GLDrawParams.Allocate.None), "nonidealraw", "NonIdealRaw");
    }

    @Override
    public void Run() {
        Compile();
        float maxmap = 0.f;
        for(int i =0; i<parameters.gainMap.length;i++){
            if(maxmap < parameters.gainMap[i]) maxmap = parameters.gainMap[i];
        }
        GLTexture input = new GLTexture(parameters.rawSize,new GLFormat(GLFormat.DataType.FLOAT_32),prevmap);
        GLTexture inpb = new GLTexture(parameters.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_16),inp);
        glOne.glProgram.setTexture("GainMap",input);
        glOne.glProgram.setTexture("InputBuffer",inpb);
        glOne.glProgram.setVar("MaxMap",maxmap);
        glOne.glProcessing.mOutBuffer = inp;
        inpb.BufferLoad();
        glOne.glProcessing.drawBlocksToOutput();
        inpb.close();
        input.close();
        Output = glOne.glProcessing.mOutBuffer;
    }
}
