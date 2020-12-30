package com.eszdman.photoncamera.api;

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
        String[] names = new String[values().length];
        for (int i = 0; i < values().length; i++) {
            names[i] = values()[i].name();
        }
        return names;
    }
}
