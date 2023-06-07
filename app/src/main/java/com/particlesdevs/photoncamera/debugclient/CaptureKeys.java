package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.PrintWriter;

class CaptureKeys implements Command {
    private String[] commands;
    private PrintWriter mBufferOut;
    CaptureController controller;
    CaptureRequest.Builder captureRequestBuilder = null;

    public CaptureKeys(String[] str) {
        commands = str;
        controller = PhotonCamera.getCaptureController();
    }

    @Override
    public void command() {
        captureRequestBuilder = controller.getDebugCaptureRequestBuilder();
        if (captureRequestBuilder == null)
            mBufferOut.println("Error at creating builder!");
        mBufferOut.println("Builder created");
    }
}
