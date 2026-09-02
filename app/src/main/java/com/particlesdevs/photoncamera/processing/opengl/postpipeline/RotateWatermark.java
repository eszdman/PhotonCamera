package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;

import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.util.FileManager;

import java.io.File;
import java.io.IOException;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_REPEAT;

public class RotateWatermark extends Node {
    private int rotate;
    private boolean watermarkNeeded;
    private GLImage watermark;
    private GLImage noise;
    public RotateWatermark(int rotation) {
        super("", "Rotate");
        rotate = rotation;
        watermarkNeeded = PreferenceKeys.isShowWatermarkOn();
    }

    @Override
    public void Compile() {}
    @Override
    public void AfterRun() {
        if(watermark != null) watermark.close();
        if(noise != null) noise.close();
    }

    @Override
    public void Run() {

        //else lutbm = BitmapFactory.decodeResource(PhotonCamera.getResourcesStatic(), R.drawable.neutral_lut);
        glProg.setDefine("WATERMARK",watermarkNeeded);

        glProg.useAssetProgram("RotateWatermark/addwatermark_rotate");
        try {
            File waterExternal = new File(FileManager.sPHOTON_TUNING_DIR,"watermark.png");
            if (waterExternal.exists()) watermark = new GLImage(waterExternal);
            else watermark = new GLImage(PhotonCamera.getAssetLoader().getInputStream("watermark/photoncamera_watermark.png"));
            noise = new GLImage(PhotonCamera.getAssetLoader().getInputStream("noise.png"));
            glProg.setTexture("Watermark", new GLTexture(watermark,GL_LINEAR,GL_CLAMP_TO_EDGE,0));
            glProg.setTexture("Noise", new GLTexture(noise,GL_LINEAR,GL_REPEAT,0));
        } catch (IOException e) {
            Log.d(Name,"Failed to load watermark or noise texture:" + Log.getStackTraceString(e));
        }

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
        if(basePipeline.mParameters.mirror) {
            glProg.setVar("mirror", 1);
        } else {
            glProg.setVar("mirror", 0);
        }
        // The rotate shader positions samples with (rawSize - cropSize)
        // offsets, which assumed cropSize == crop-buffer size. Since
        // UpscaleCrop expands crops to full-frame size in place, both must
        // describe the actual rotate-input texture (which always matches the
        // pre-rotation output size), so every offset is 0 and the mirror
        // branches operate on full dimensions.
        Point rotInSize = previousNode.WorkingTexture.mSize;
        glProg.setVar("cropSize", rotInSize);
        glProg.setVar("rawSize", rotInSize);

    }
}
