package com.eszdman.photoncamera.Parameters;

import com.eszdman.photoncamera.Camera2Api;

public class ExposureIndex {
    public static final long sec = 1000000000;

    public static double index() {
        long exposureTime = Camera2Api.context.mPreviewExposuretime;
        int iso = Camera2Api.context.mPreviewIso;
        double time = (double) (exposureTime) / sec;
        return iso * time;
    }
}
