package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureResult;

import com.particlesdevs.photoncamera.capture.CaptureController;

import java.io.PrintWriter;
import java.util.List;

class PreviewKeys implements Command {
    private String[] commands;
    private PrintWriter mBufferOut;
    List<CaptureResult.Key<?>> resultKeys;

    public PreviewKeys(String[] str) {
        commands = str;
        resultKeys = CaptureController.mPreviewCaptureResult.getKeys();
    }

    @Override
    public void command() {
        StringBuilder keysStr = new StringBuilder();
        for (CaptureResult.Key<?> key : resultKeys) {
            keysStr.append(key.getName());
            keysStr.append(" ");
        }
        mBufferOut.println(keysStr.toString());
        return;
    }
}
