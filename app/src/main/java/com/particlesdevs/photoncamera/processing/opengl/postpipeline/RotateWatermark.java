package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class RotateWatermark extends Node {
    private int rotate;
    private boolean watermarkNeeded;
    private Bitmap watermark;
    public RotateWatermark(int rotation) {
        super(0, "Rotate");
        rotate = rotation;
        watermarkNeeded = PreferenceKeys.isShowWatermarkOn();
    }

    @Override
    public void Compile() {}
    GLTexture lut;
    Bitmap lutbm;
    @Override
    public void AfterRun() {
        if(watermark != null) watermark.recycle();
        lutbm.recycle();
        lut.close();
    }

    @Override
    public void Run() {
        if(watermarkNeeded) {
        glProg.useProgram(R.raw.addwatermark_rotate);
        watermark = BitmapFactory.decodeResource(PhotonCamera.getResourcesStatic(), R.drawable.photoncamera_watermark);
        glProg.setTexture("Watermark", new GLTexture(watermark,GL_LINEAR,GL_CLAMP_TO_EDGE,0));
        } else {
            glProg.useProgram(R.raw.rotate);
        }
        lutbm = BitmapFactory.decodeResource(PhotonCamera.getResourcesStatic(), R.drawable.lut);
        lut = new GLTexture(lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
        glProg.setTexture("LookupTable",lut);
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        int rot = -1;
        Log.d(Name,"Rotation:"+rotate);
        switch (rotate){
            case 0:
                //WorkingTexture = new GLTexture(size.x,size.y, previousNode.WorkingTexture.mFormat, null);
                rot = 0;
                break;
            case 90:
                //WorkingTexture = new GLTexture(size.y,size.x, previousNode.WorkingTexture.mFormat, null);
                rot = 3;
                break;
            case 180:
                //WorkingTexture = new GLTexture(size, previousNode.WorkingTexture.mFormat, null);
                rot = 2;
                break;
            case 270:
                //WorkingTexture = new GLTexture(size.y,size.x, previousNode.WorkingTexture.mFormat, null);
                rot = 1;
                break;
        }
        Log.d(Name,"selected rotation:"+rot);
        glProg.setVar("rotate",rot);
    }
}
