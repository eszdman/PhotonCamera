package com.eszdman.photoncamera.api.capture;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;

import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.api.capture.AbstractImageCapture;
import com.eszdman.photoncamera.api.session.CaptureSessionController;
import com.eszdman.photoncamera.api.session.ICaptureSession;

import java.util.ArrayList;
import java.util.List;

public abstract class CapturePipe implements ICaptureSession.CaptureSessionEvents {

    private List<AbstractImageCapture> imageCaptureList;
    protected CaptureSessionController captureSessionController;
    protected BurstCounter burstCounter;
    protected Handler mBackgroundHandler;

    public CapturePipe(CaptureSessionController captureSessionController)
    {
        imageCaptureList = new ArrayList<>();
        this.captureSessionController = captureSessionController;
    }

    public abstract void createCaptureSession(SurfaceTexture surfaceTexture);
    public abstract void captureStillPicture(float mFocus, AutoFitTextureView surfaceTexture);

    public abstract void findOutputSizes(CameraCharacteristics cameraCharacteristics);
    public abstract void createImageReader(int maxImages);
    public abstract void setSurfaces();
    public abstract void startCapture();
    public abstract void setCaptureResult(TotalCaptureResult captureResult);

    public void setBurstCounter(BurstCounter burstCounter)
    {
        this.burstCounter = burstCounter;
    }

    public void setmBackgroundHandler(Handler mBackgroundHandler)
    {
        this.mBackgroundHandler = mBackgroundHandler;
    }

    public void close()
    {
        for (AbstractImageCapture a : imageCaptureList)
            try {
                a.close();
            }
        catch (NullPointerException ex)
        {
        }
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
