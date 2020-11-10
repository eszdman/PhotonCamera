package com.eszdman.photoncamera.processing.parameters;

import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.app.PhotonCamera;


public class FrameNumberSelector {
    public static int frameCount;

    public static int getFrames() {
        double output = (Math.exp(1.3595 + 0.0020 * ExposureIndex.index()*6400.0/IsoExpoSelector.getISOAnalog())) / 17;
        output *= PhotonCamera.getSettings().frameCount;
        frameCount = Math.min(Math.max((int) output, 4), PhotonCamera.getSettings().frameCount);
        if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED) frameCount = -1;
        return frameCount;
    }
}
