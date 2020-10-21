package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class LensCorrection extends Node {

    public LensCorrection() {
        super(R.raw.lenscorrection, "LensCorrection");
    }
    @Override
    public void AfterRun() {
        previousNode.WorkingTexture.close();
    }
    @Override
    public void Run() {
        Parameters params = glInt.parameters;
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setVar("intens",-.15f);
        glProg.setVar("start",0.5f);
        glProg.setVar("size",previousNode.WorkingTexture.mSize);
        GLTexture GainMapTex = new GLTexture(params.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4), FloatBuffer.wrap(params.gainMap));
        glProg.setTexture("GainMap",GainMapTex);
        float br = 0.f;
        br = params.gainMap[0];
        glProg.setVar("avrbr0",br);
        for(int i =0; i<params.gainMap.length;i++){
            br=(br+params.gainMap[i])/2.f;
        }
        Log.d(Name,"avrbr:"+br);
        glProg.setVar("avrbr",br);
        WorkingTexture = new GLTexture(previousNode.WorkingTexture);
    }
}
