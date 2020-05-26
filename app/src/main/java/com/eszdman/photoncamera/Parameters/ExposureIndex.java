package com.eszdman.photoncamera.Parameters;

import com.eszdman.photoncamera.ui.CameraFragment;

public class ExposureIndex {
    public static final long sec = 1000000000;

    public static double index() {
        long exposureTime = CameraFragment.context.mPreviewExposuretime;
        int iso = CameraFragment.context.mPreviewIso;
        double time = (double) (exposureTime) / sec;
        return iso * time;
    }
}
