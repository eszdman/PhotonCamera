package com.particlesdevs.photoncamera.circularbarlib.control;

import java.util.Observable;

/**
 * Observable data class which stores the manual shutter, focus, iso and ev values
 * <p>
 * Created by Vibhor Srivastava 29/Jan/2021
 */
public class ManualParamModel extends Observable {
    public static final double EXPOSURE_AUTO = 0;
    public static final double EV_AUTO = 0;
    public static final double ISO_AUTO = 0;
    public static final double FOCUS_AUTO = -1.0d;
    public static final double WB_AUTO = 0;
    public static final String ID_FOCUS = "focus";
    public static final String ID_EV = "ev";
    public static final String ID_SHUTTER = "shutter";
    public static final String ID_ISO = "iso";
    public static final String ID_WB = "wb";

    public static final String PANEL_INVISIBILITY = "panel_invisibility";
    private double currentFocusValue;
    private double currentEvValue;
    private double currentExposureValue;
    private double currentISOValue;
    private double currentWbValue;

    public ManualParamModel() {
    }

    public double getCurrentFocusValue() {
        return currentFocusValue;
    }

    public void setCurrentFocusValue(double currentFocusValue) {
        this.currentFocusValue = currentFocusValue;
        notifyObservers(ID_FOCUS);
    }

    public double getCurrentEvValue() {
        return currentEvValue;
    }

    public void setCurrentEvValue(double currentEvValue) {
        this.currentEvValue = currentEvValue;
        notifyObservers(ID_EV);
    }

    public double getCurrentExposureValue() {
        return currentExposureValue;
    }

    public void setCurrentExposureValue(double currentExposureValue) {
        this.currentExposureValue = currentExposureValue;
        notifyObservers(ID_SHUTTER);
    }

    public double getCurrentISOValue() {
        return currentISOValue;
    }

    public void setCurrentISOValue(double currentISOValue) {
        this.currentISOValue = currentISOValue;
        notifyObservers(ID_ISO);
    }

    public double getCurrentWbValue() {
        return currentWbValue;
    }

    public void setCurrentWbValue(double currentWbValue) {
        this.currentWbValue = currentWbValue;
        notifyObservers(ID_WB);
    }

    public boolean isManualMode() {
        return !(getCurrentExposureValue() == EXPOSURE_AUTO
                && getCurrentFocusValue() == FOCUS_AUTO
                && getCurrentISOValue() == ISO_AUTO
                && getCurrentEvValue() == EV_AUTO
                && getCurrentWbValue() == WB_AUTO);
    }

    public void reset() {
        reset(false);
    }

    public void reset(boolean preserveWb) {
        currentFocusValue = FOCUS_AUTO;
        currentEvValue = EV_AUTO;
        currentExposureValue = EXPOSURE_AUTO;
        currentISOValue = ISO_AUTO;
        if (!preserveWb) {
            currentWbValue = WB_AUTO;
        }
        notifyObservers(PANEL_INVISIBILITY);
    }

    @Override
    public void notifyObservers(Object arg) {
        setChanged();
        super.notifyObservers(arg);
    }
}
