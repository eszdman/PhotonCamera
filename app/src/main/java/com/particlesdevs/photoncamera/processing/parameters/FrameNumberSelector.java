package com.particlesdevs.photoncamera.processing.parameters;

import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;


public class FrameNumberSelector {
    public static int frameCount;
    public static int throwCount;
    public static int getFrames() {
        double lightcycle = (Math.exp(1.3595 + 1.0020 * PhotonCamera.getCaptureController().mPreviewIso/IsoExpoSelector.getISOAnalog())) / 9;
        double target = (Math.exp(1.3595 + 1.0020 * PhotonCamera.getCaptureController().mPreviewIso/IsoExpoSelector.getISOAnalog())) / 14;
        int frames = PhotonCamera.getSettings().getActiveFrameCount();
        lightcycle *= frames;
        target *= frames;
        frameCount = Math.min(Math.max((int) lightcycle, Math.min(8,frames)), frames);
        throwCount = Math.min(Math.max((int) target, Math.min(8,frames)), frames);
        if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED || PhotonCamera.getSettings().selectedMode == CameraMode.RAWVIDEO) frameCount = -1;
        if(PhotonCamera.getSettings().DebugData) frameCount = frames;
        throwCount = (frameCount-throwCount);
        return frameCount;
    }
}
