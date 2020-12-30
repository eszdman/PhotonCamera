package com.eszdman.photoncamera.capture;

import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureResult;

import java.io.File;

public interface CaptureEventsListener {
    void onOpenCamera(CameraManager cameraManager);

    void onFrameCountSet(int frameCount);

    void onCaptureStarted(Object o);

    void onFrameCaptured(Object o);

    void onCaptureProgressed(Object o);

    void onCaptureSequenceCompleted(Object o);

    void onCaptureCompleted(CaptureResult captureResult);

    void onCameraRestarted();

    void onCharacteristicsUpdated();

    void onError(Object o);

    void onFatalError(String errorMsg);

    void onRequestTriggerMediaScanner(File f);
}
