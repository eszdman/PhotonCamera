    package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Converter;
import com.eszdman.photoncamera.processing.render.Parameters;
import com.eszdman.photoncamera.app.PhotonCamera;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_LUMINANCE;

public class Initial extends Node {
    public Initial(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void AfterRun() {
        lutbm.recycle();
        lut.close();
    }
    GLTexture lut;
    Bitmap lutbm;
    @Override
    public void Run() {
        GLInterface glint = basePipeline.glint;
        Parameters params = glint.parameters;
        lutbm = BitmapFactory.decodeResource(PhotonCamera.getCameraActivity().getResources(), R.drawable.lut);
        GLTexture TonemapCoeffs = new GLTexture(new Point(256,1),new GLFormat(GLFormat.DataType.FLOAT_16,1),FloatBuffer.wrap(PhotonCamera.getSettings().toneMap));
        lut = new GLTexture(lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
        glProg.setTexture("TonemapTex",TonemapCoeffs);
        glProg.setTexture("InputBuffer",super.previousNode.WorkingTexture);
        glProg.setTexture("LookupTable",lut);

        glProg.setVar("toneMapCoeffs", Converter.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        glProg.setVar("sensorToIntermediate",params.sensorToProPhoto);
        glProg.setVar("intermediateToSRGB",params.proPhotoToSRGB);
        glProg.setVar("gain", (float) PhotonCamera.getSettings().gain/2.f);
        glProg.setVar("Regeneration", ((PostPipeline)basePipeline).regenerationSense);
        Log.d(Name,"SensorPix:"+params.sensorPix);
        glProg.setVar("activeSize",4,4,params.sensorPix.right-params.sensorPix.left-4,params.sensorPix.bottom-params.sensorPix.top-4);
        glProg.setVar("neutralPoint",params.whitePoint);
        Log.d(Name,"compressor:"+1.f/((float)PhotonCamera.getSettings().compressor));
        float sat =(float) PhotonCamera.getSettings().saturation;
        if(PhotonCamera.getSettings().cfaPattern == 4) sat = 0.f;
        glProg.setVar("saturation",sat);
        //WorkingTexture = new GLTexture(super.previousNode.WorkingTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim),null);
        WorkingTexture = basePipeline.main1;
    }
}
