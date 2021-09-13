package com.particlesdevs.photoncamera.capture;

import android.hardware.camera2.CaptureResult;

public interface CaptureEventsListener {
    void onFrameCountSet(int frameCount);

    void onCaptureStillPictureStarted(Object o);

    void onBurstPrepared(Object o);

    void onFrameCaptureStarted(Object o);

    void onFrameCaptureProgressed(Object o);

    void onFrameCaptureCompleted(Object o);

    void onCaptureSequenceCompleted(Object o);

    void onPreviewCaptureCompleted(CaptureResult captureResult);
}
