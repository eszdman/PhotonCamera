package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;

class DebugShot implements Command {
    private CaptureController controller;
    private CaptureRequest.Builder captureRequestBuilder = null;

    public DebugShot(String[] str) {
        controller = PhotonCamera.getCaptureController();
    }

    @Override
    public void command() {
        controller.runDebug(captureRequestBuilder);
    }
}
