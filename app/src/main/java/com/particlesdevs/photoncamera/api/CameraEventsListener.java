package com.particlesdevs.photoncamera.api;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;

import com.particlesdevs.photoncamera.capture.CaptureEventsListener;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;

import java.io.File;

public abstract class CameraEventsListener implements CaptureEventsListener, ProcessingEventsListener {
    protected String TAG = "CameraEventsListener";

    protected void logD(String msg) {
        Log.d(TAG, msg);
    }

    protected void logE(String msg) {
        Log.e(TAG, msg);
    }

    public abstract void onOpenCamera(CameraManager cameraManager);

    public abstract void onCameraRestarted();

    public abstract void onCharacteristicsUpdated(CameraCharacteristics characteristics);

    public abstract void onError(Object o);

    public abstract void onFatalError(String errorMsg);

    public abstract void onRequestTriggerMediaScanner(File f);
}
