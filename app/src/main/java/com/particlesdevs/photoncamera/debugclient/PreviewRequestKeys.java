package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.PrintWriter;
import java.util.ArrayList;

class PreviewRequestKeys implements Command {
    private PrintWriter mBufferOut;
    private ArrayList<CaptureRequest.Key<?>> captureRequestKeys;

    public PreviewRequestKeys(String[] str) {
        captureRequestKeys = new ArrayList<>(CaptureController.mPreviewCaptureRequest.getKeys());
    }

    @Override
    public void command() {
        StringBuilder keysStr = new StringBuilder();
        for (CaptureRequest.Key<?> key : captureRequestKeys) {
            keysStr.append(key.getName());
            keysStr.append(" ");
        }
        mBufferOut.println(keysStr.toString());
        return;
    }
}
