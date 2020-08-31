package com.manual;

/**
 * Created by Vibhor on 10/08/2020
 */
public interface ManualMode {
    void init();

    double getCurrentExposureValue();

    double getCurrentISOValue();

    double getCurrentFocusValue();

    double getCurrentEvValue();

    boolean isManualMode();

    void retractAllKnobs();

    void rotate(int orientation);

}
