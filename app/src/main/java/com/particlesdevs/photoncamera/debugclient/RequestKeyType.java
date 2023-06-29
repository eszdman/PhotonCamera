package com.particlesdevs.photoncamera.debugclient;

import android.hardware.camera2.CaptureRequest;

public interface RequestKeyType {
    static RequestKeyType createKeyType(CaptureRequest.Builder builder, CaptureRequest.Key<?> key, String type, String value) {
        try {
            TypeEnum typeEnum = TypeEnum.valueOf(type);
            switch (typeEnum) {
                case LONG: {
                    return new RequestKeyLong(builder, key, value);
                }
                case INTEGER: {
                    return new RequestKeyInteger(builder, key, value);
                }
                case STRING: {
                    return new RequestKeyString(builder, key, value);
                }
                case FLOAT: {
                    return new RequestKeyFloat(builder, key, value);
                }
                case DOUBLE: {
                    return new RequestKeyDouble(builder, key, value);
                }
                default:
                    throw new IllegalStateException("Unexpected value: " + type);
            }
        }catch (IllegalArgumentException e){
            throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    public void setKey();
}

