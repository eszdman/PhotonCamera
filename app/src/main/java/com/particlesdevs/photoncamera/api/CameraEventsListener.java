package com.particlesdevs.photoncamera.api;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import com.particlesdevs.photoncamera.util.Log;
import com.particlesdevs.photoncamera.capture.CaptureEventsListener;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;

import java.io.File;

/**
 * Class primarily responsible for interfacing between camera/capture/processing related events and the UI
 */
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

    public abstract void onRequestTriggerMediaScanner(Uri f);

    /** Fired when MediaRecorder video capture actually starts. */
    public void onVideoRecordingStarted() {}

    /** Periodic (~2Hz) progress while a MediaRecorder video is running. */
    public void onVideoRecordingTick(long elapsedMs, long estimatedBytes, long availableBytes) {}

    /** Fired when MediaRecorder video capture stops. */
    public void onVideoRecordingStopped() {}

    /** Fired when a seamless logical-member switch is applied (no reopen). */
    public void onLogicalMemberChanged(String memberId) {}

    /** Fired per tick while a smooth logical zoom animates (ratio rendered). */
    public void onLogicalZoomProgress(float ratio) {}
}
