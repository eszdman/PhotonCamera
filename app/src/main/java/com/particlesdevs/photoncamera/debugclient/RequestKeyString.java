package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

class RequestKeyString implements RequestKeyType {
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;

    public RequestKeyString(CaptureRequest.Builder builder, CaptureRequest.Key<?> key, String value) {
        this.builder = builder;
        this.key = key;
        this.value = value;
    }

    @Override
    public void setKey() {
        builder.set((CaptureRequest.Key<String>) key, value);
    }
}
