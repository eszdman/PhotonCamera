package com.eszdman.photoncamera.capture;

public interface CaptureEventsListener {
    void onFrameCountSet(int frameCount);

    void onCaptureStarted(Object o);

    void onFrameCaptured(Object o);

    void onCaptureProgressed(Object o);

    void onCaptureCompleted(Object o);

    void onCameraRestarted();

    void onCharacteristicsUpdated();

    void onError(Object o);
}
