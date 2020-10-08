package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Converter;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.app.PhotonCamera;

import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class Initial extends Node {
    public Initial(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run() {
        Node Previous = super.previousNode;
        GLInterface glint = basePipeline.glint;
        GLProg glProg = glint.glProgram;
        Parameters params = glint.parameters;
        GLTexture GainMapTex = new GLTexture(params.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4),FloatBuffer.wrap(params.gainMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        GLTexture TonemapCoeffs = new GLTexture(new Point(256,1),new GLFormat(GLFormat.DataType.FLOAT_16,1),FloatBuffer.wrap(PhotonCamera.getSettings().toneMap));
        glProg.setTexture("TonemapTex",TonemapCoeffs);
        glProg.setTexture("Fullbuffer",super.previousNode.WorkingTexture);
        glProg.setTexture("GainMap",GainMapTex);
        glProg.setVar("RawSizeX",params.rawSize.x);
        glProg.setVar("RawSizeY",params.rawSize.y);
        glProg.setVar("toneMapCoeffs", Converter.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        glProg.setVar("sensorToIntermediate",params.sensorToProPhoto);
        glProg.setVar("intermediateToSRGB",params.proPhotoToSRGB);
        glProg.setVar("gain", (float) PhotonCamera.getSettings().gain);
        glProg.setVar("neutralPoint",params.whitePoint);
        float sat =(float) PhotonCamera.getSettings().saturation;
        if(PhotonCamera.getSettings().cfaPattern == -2) sat = 0.f;
        glProg.setVar("saturation",sat);
        for(int i =0; i<4;i++){
            params.blackLevel[i]/=params.whiteLevel;
        }
        glProg.setVar("blackLevel",params.blackLevel);
        super.WorkingTexture = new GLTexture(super.previousNode.WorkingTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
    }
}
