package com.eszdman.photoncamera.api;

import java.util.stream.Stream;

public enum CameraMode {
    UNLIMITED(0),
    PHOTO(1),
    NIGHT(2),
    VIDEO(3)
    ;
    public final int mNum;

    CameraMode(int number) {
        mNum = number;
    }

    public static String[] names() {
        return Stream.of(values()).map(Enum::name).toArray(String[]::new);
    }

}
