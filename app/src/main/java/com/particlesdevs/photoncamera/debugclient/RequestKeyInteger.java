package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

class RequestKeyInteger implements RequestKeyType {
    private CaptureRequest.Builder builder;
    private CaptureRequest.Key<?> key;
    String value;

    public RequestKeyInteger(CaptureRequest.Builder builder, CaptureRequest.Key<?> key, String value) {
        this.builder = builder;
        this.key = key;
        this.value = value;
    }

    @Override
    public void setKey() {
        builder.set((CaptureRequest.Key<Integer>) key, Integer.parseInt(value));
    }
}
