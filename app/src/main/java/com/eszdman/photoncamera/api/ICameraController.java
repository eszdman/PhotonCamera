package com.eszdman.photoncamera.api;

import android.hardware.camera2.CaptureResult;

import com.eszdman.photoncamera.AutoFitTextureView;
import com.eszdman.photoncamera.api.session.ICaptureSession;

public interface ICameraController
{
    int getPreviewWidth();
    int getPreviewHeight();
    CaptureResult getCaptureResult();
    void setEventsListner(CameraController.ControllerEvents eventsListner);
    void setCaptureListner(ImageCaptureResultCallback.CaptureEvents captureListner);
    void removeCaptureListner(ImageCaptureResultCallback.CaptureEvents captureListner);
    void setTextureView(AutoFitTextureView textureView);
    void onResume();
    void onPause();
    void restartCamera();
    void openCamera(int width, int height);
    void closeCamera();
    void startBackgroundThread();
    void stopBackgroundThread();
    void rebuildPreviewBuilder();
    void rebuildPreviewBuilderOneShot();
    ICaptureSession getiCaptureSession();
}
