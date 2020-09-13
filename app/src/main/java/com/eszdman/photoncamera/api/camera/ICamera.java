package com.eszdman.photoncamera.api.camera;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.view.Surface;

import java.util.List;

public interface ICamera {

    interface CameraEvents
    {
        void onCameraOpen();
        void onCameraClose();
    }

    void setCameraEventsListner(CameraEvents cameraEventsListner);
    void onResume();
    void onPause();

    boolean isCameraOpen();
    String getId();
    CaptureRequest.Builder createCaptureRequest(int template);
    CaptureRequest.Builder createReprocessCaptureRequest(TotalCaptureResult template);
    void createReprocessCaptureSession(List<Surface> var1, CameraCaptureSession.StateCallback var2, Handler var3, int width, int height);
    void createCaptureSession(List<Surface> var1, android.hardware.camera2.CameraCaptureSession.StateCallback var2, Handler var3);
    void openCamera(String id);
    void closeCamera();
}
