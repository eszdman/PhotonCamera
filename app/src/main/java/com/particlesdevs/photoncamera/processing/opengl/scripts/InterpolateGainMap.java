package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class InterpolateGainMap extends GLOneScript {
    public Parameters parameters;
    public InterpolateGainMap(Point size) {
        super(size, new GLCoreBlockProcessing(size,new GLFormat(GLFormat.DataType.UNSIGNED_16)), R.raw.interpolategainmap, "InterpolateGainMap");
    }

    @Override
    public void Run() {
        glOne.glProgram.setDefine("RAWSIZE",parameters.rawSize);
        glOne.glProgram.setDefine("CFAPATTERN",(int)parameters.cfaPattern);
        GLUtils glUtils = new GLUtils(glOne.glProcessing);
        Compile();
        GLTexture input = new GLTexture(parameters.mapSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),
                FloatBuffer.wrap(parameters.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        GLTexture outputTex = new GLTexture(parameters.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_16));
        glOne.glProgram.setTexture("GainMap",input);
        glOne.glProgram.drawBlocks(outputTex);
        outputTex.BufferLoad();
        glOne.glProcessing.drawBlocksToOutput();
        glUtils.SaveProgResult(parameters.rawSize,"gainmap");
        input.close();
        outputTex.close();
        Output = glOne.glProcessing.mOutBuffer;
    }
}
