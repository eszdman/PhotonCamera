package com.particlesdevs.photoncamera.api;

import androidx.annotation.StringRes;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.util.stream.Stream;

public enum CameraMode {
    UNLIMITED(R.string.mode_unlimited),
    PHOTO(R.string.mode_photo),
    NIGHT(R.string.mode_night),
    VIDEO(R.string.mode_video);

    String mName;

    CameraMode(@StringRes int stringId) {
        mName = PhotonCamera.getStringStatic(stringId);
    }

    public static CameraMode valueOf(int modeOrdinal) {
        for (CameraMode mode : values()) {
            if (modeOrdinal == mode.ordinal()) {
                return mode;
            }
        }
        return PHOTO;
    }

    public static String[] names() {
        return Stream.of(values()).map(mode -> mode.mName).toArray(String[]::new);
    }

}
