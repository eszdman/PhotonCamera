package com.eszdman.photoncamera.api;

import java.util.stream.Stream;

public enum CameraMode {
    UNLIMITED,
    PHOTO,
    NIGHT,
    VIDEO;


    public static CameraMode valueOf(int modeOrdinal) {
        for (CameraMode mode : values()) {
            if (modeOrdinal == mode.ordinal()) {
                return mode;
            }
        }
        return PHOTO;
    }

    public static String[] names() {
        return Stream.of(values()).map(Enum::name).toArray(String[]::new);
    }

}
