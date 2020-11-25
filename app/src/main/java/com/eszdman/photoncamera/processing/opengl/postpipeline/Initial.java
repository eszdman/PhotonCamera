package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.processing.opengl.GLConst;
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
        Parameters params = glint.parameters;
        GLTexture TonemapCoeffs = new GLTexture(new Point(256,1),new GLFormat(GLFormat.DataType.FLOAT_16,1),FloatBuffer.wrap(PhotonCamera.getSettings().toneMap));
        glProg.setTexture("TonemapTex",TonemapCoeffs);
        glProg.setTexture("InputBuffer",super.previousNode.WorkingTexture);
        glProg.setVar("toneMapCoeffs", Converter.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        glProg.setVar("sensorToIntermediate",params.sensorToProPhoto);
        glProg.setVar("intermediateToSRGB",params.proPhotoToSRGB);
        glProg.setVar("gain", (float) PhotonCamera.getSettings().gain);
        Log.d(Name,"SensorPix:"+params.sensorPix);
        glProg.setVar("activeSize",params.sensorPix.left+4,params.sensorPix.top+4,params.sensorPix.right-4,params.sensorPix.bottom-4);
        glProg.setVar("neutralPoint",params.whitePoint);
        Log.d(Name,"compressor:"+1.f/((float)PhotonCamera.getSettings().compressor));
        float sat =(float) PhotonCamera.getSettings().saturation;
        if(PhotonCamera.getSettings().cfaPattern == -2) sat = 0.f;
        glProg.setVar("saturation",sat);
        //WorkingTexture = new GLTexture(super.previousNode.WorkingTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim),null);
        WorkingTexture = basePipeline.main1;
    }
}
