package com.eszdman.photoncamera.api.capture;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;

import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.api.capture.AbstractImageCapture;
import com.eszdman.photoncamera.api.session.CaptureSessionController;

import java.util.ArrayList;
import java.util.List;

public abstract class CapturePipe {

    private List<AbstractImageCapture> imageCaptureList;
    protected CaptureSessionController captureSessionController;

    public CapturePipe(CaptureSessionController captureSessionController)
    {
        imageCaptureList = new ArrayList<>();
        this.captureSessionController = captureSessionController;
    }

    public abstract void createCameraPreviewSession(SurfaceTexture surfaceTexture, Handler mBackgroundHandler);
    public abstract void captureStillPicture(int mTargetFormat, float mFocus, AutoFitTextureView surfaceTexture, Handler mBackgroundHandler, BurstCounter burstCounter);

    public abstract void findOutputSizes(CameraCharacteristics cameraCharacteristics, int targetFormat, int previewFormat);
    public abstract void createImageReader(int maxImages);
    public abstract void setSurfaces();
    public abstract void startCapture(Handler mBackgroundHandler);

    public void close()
    {
        for (AbstractImageCapture a : imageCaptureList)
            a.close();
        imageCaptureList.clear();
    }

    protected void add(AbstractImageCapture imageCapture)
    {
        imageCaptureList.add(imageCapture);
    }
    public AbstractImageCapture getAbstractImageCapture(int pos)
    {
        return imageCaptureList.get(pos);
    }
}
