package com.particlesdevs.photoncamera.manual;

import android.app.Activity;

/**
 * Created by Vibhor on 10/08/2020
 */
public interface ManualMode {
    static ManualMode getInstance(Activity activity) {
        return new ManualModeImpl(activity);
    }

    void init();

    double getCurrentExposureValue();

    double getCurrentISOValue();

    double getCurrentFocusValue();

    double getCurrentEvValue();

    boolean isManualMode();

    void retractAllKnobs();


}
