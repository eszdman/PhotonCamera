package com.eszdman.photoncamera.capture;

import android.hardware.camera2.CaptureResult;

public interface CaptureEventsListener {
    void onFrameCountSet(int frameCount);

    void onCaptureStarted(Object o);

    void onFrameCaptured(Object o);

    void onCaptureProgressed(Object o);

    void onCaptureSequenceCompleted(Object o);

    void onCaptureCompleted(CaptureResult captureResult);
}
