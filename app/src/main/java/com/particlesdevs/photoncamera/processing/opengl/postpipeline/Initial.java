    package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.Converter;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class Initial extends Node {
    public Initial() {
        super(0, "Initial");
    }

    @Override
    public void AfterRun() {
        lutbm.recycle();
        lut.close();
    }

    @Override
    public void Compile() {}

    GLTexture lut;
    Bitmap lutbm;
    @Override
    public void Run() {
        GLInterface glint = basePipeline.glint;
        lutbm = BitmapFactory.decodeResource(PhotonCamera.getResourcesStatic(), R.drawable.lut);
        GLTexture TonemapCoeffs = new GLTexture(new Point(256,1),new GLFormat(GLFormat.DataType.FLOAT_16,1),FloatBuffer.wrap(PhotonCamera.getSettings().toneMap));
        /*GLTexture oldT = TonemapCoeffs;
        TonemapCoeffs = glUtils.interpolate(TonemapCoeffs,2);
        oldT.close();
        oldT = TonemapCoeffs;
        TonemapCoeffs = glUtils.interpolate(TonemapCoeffs,2);
        oldT.close();*/
        float green = ((((PostPipeline)basePipeline).analyzedBL[0]+((PostPipeline)basePipeline).analyzedBL[2]+0.0002f)/2.f)/(((PostPipeline)basePipeline).analyzedBL[1]+0.0001f);
        if(green > 0.0f && green < 1.7f) {
            float tcor = (green+1.f)/2.f;
            glProg.setDefine("TINT",tcor);
            glProg.setDefine("TINT2",((1.f/tcor+1.f)/2.f));
        }
        glProg.setDefine("DYNAMICBL",((PostPipeline)basePipeline).analyzedBL);
        glProg.setDefine("PRECISION",(float)DynamicBL.precisionFactor);
        glProg.useProgram(R.raw.initial);
        lut = new GLTexture(lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
        glProg.setTexture("TonemapTex",TonemapCoeffs);
        glProg.setTexture("InputBuffer",super.previousNode.WorkingTexture);
        glProg.setTexture("LookupTable",lut);
        //glProg.setTexture("GainMap", ((PostPipeline)basePipeline).GainMap);
        glProg.setVar("toneMapCoeffs", Converter.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        glProg.setVar("sensorToIntermediate",basePipeline.mParameters.sensorToProPhoto);
        glProg.setVar("intermediateToSRGB",basePipeline.mParameters.proPhotoToSRGB);
        glProg.setTexture("FusionMap",((PostPipeline)basePipeline).FusionMap);
        glProg.setVar("gain",1.f);
        Log.d(Name,"SensorPix:"+basePipeline.mParameters.sensorPix);
        glProg.setVar("activeSize",4,4,basePipeline.mParameters.sensorPix.right-basePipeline.mParameters.sensorPix.left-4,
                basePipeline.mParameters.sensorPix.bottom-basePipeline.mParameters.sensorPix.top-4);
        glProg.setVar("neutralPoint",basePipeline.mParameters.whitePoint);
        Log.d(Name,"compressor:"+1.f/((float)PhotonCamera.getSettings().compressor));
        float sat =(float) PhotonCamera.getSettings().saturation;
        if(PhotonCamera.getSettings().cfaPattern == 4) sat = 0.f;
        glProg.setVar("saturation",sat);
        //WorkingTexture = new GLTexture(super.previousNode.WorkingTexture.mSize,new GLFormat(GLFormat.DataType.FLOAT_16, GLConst.WorkDim),null);
        WorkingTexture = basePipeline.getMain();
    }
}
