package com.particlesdevs.photoncamera.processing.parameters;

import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;


public class FrameNumberSelector {
    public static int frameCount;
    public static int throwCount;
    public static int getFrames() {
        double lightcycle = (Math.exp(1.3595 + 0.0020 * ExposureIndex.index()*6400.0/IsoExpoSelector.getISOAnalog())) / 9;
        double target = (Math.exp(1.3595 + 0.0020 * ExposureIndex.index()*6400.0/IsoExpoSelector.getISOAnalog())) / 14;
        lightcycle *= PhotonCamera.getSettings().frameCount;
        target *= PhotonCamera.getSettings().frameCount;
        frameCount = Math.min(Math.max((int) lightcycle, 4), PhotonCamera.getSettings().frameCount);
        throwCount = Math.min(Math.max((int) target, 4), PhotonCamera.getSettings().frameCount);
        if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED) frameCount = -1;
        if(PhotonCamera.getSettings().DebugData) frameCount = PhotonCamera.getSettings().frameCount;
        throwCount = (frameCount-throwCount);
        return frameCount;
    }
}
