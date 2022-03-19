package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_UNSIGNED_SHORT;
import static android.opengl.GLES20.glReadPixels;
import static android.opengl.GLES30.GL_RED;
import static android.opengl.GLES30.GL_RED_INTEGER;

public class InterpolateGainMap extends GLOneScript {
    public Parameters parameters;
    public InterpolateGainMap(Point size) {
        super(size, new GLCoreBlockProcessing(size,new GLFormat(GLFormat.DataType.FLOAT_32), GLDrawParams.Allocate.Direct),"interpolategainmap", "InterpolateGainMap");
    }

    @Override
    public void Run() {
        glOne.glProgram.setDefine("RAWSIZE",parameters.rawSize);
        glOne.glProgram.setDefine("CFAPATTERN",(int)parameters.cfaPattern);
        //GLUtils glUtils = new GLUtils(glOne.glProcessing);
        Compile();
        GLTexture input = new GLTexture(parameters.mapSize,new GLFormat(GLFormat.DataType.FLOAT_32,4),
                BufferUtils.getFrom(parameters.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        GLTexture outputTex = new GLTexture(parameters.rawSize,new GLFormat(GLFormat.DataType.FLOAT_32));
        glOne.glProgram.setTexture("GainMap",input);
        //glOne.glProgram.drawBlocks(outputTex,outputTex.mSize);
        //glUtils.convertVec4(outputTex,"in1/2.0");
        //glUtils.SaveProgResult(outputTex.mSize,"gainmap");
        outputTex.BufferLoad();
        glOne.glProcessing.drawBlocksToOutput();
        input.close();
        outputTex.close();
        /*Output = ByteBuffer.allocateDirect(size.x*size.y * 4)
                .order(ByteOrder.nativeOrder());
        Output.mark();
        glReadPixels(0, 0, size.x, size.y, GL_RED, GL_FLOAT, Output.reset());*/
        Output = glOne.glProcessing.mOutBuffer;
    }
}
