package com.eszdman.photoncamera.api;

public enum CameraMode {
    UNLIMITED(2),
    PHOTO(0),
    NIGHT(1),
    ;
    //VIDEO(3);
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
