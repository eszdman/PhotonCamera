package com.particlesdevs.photoncamera.circularbarlib.model;

import com.particlesdevs.photoncamera.circularbarlib.control.models.ManualModel;

import java.util.Observable;

/**
 * Observable state for the manual slider strip(s).
 */
public class SliderModel extends Observable {
    boolean resetCalled;
    private boolean sliderVisible;
    private ManualModel<?> manualModel;
    private ManualModel<?> secondaryManualModel;

    public ManualModel<?> getManualModel() {
        return manualModel;
    }

    public void setManualModel(ManualModel<?> manualModel) {
        this.manualModel = manualModel;
        notifyObservers(SliderModelFields.MANUAL_MODEL);
    }

    /**
     * The remembered previous control, shown as the upper slider row.
     * Null when only one control is active.
     */
    public ManualModel<?> getSecondaryManualModel() {
        return secondaryManualModel;
    }

    public void setSecondaryManualModel(ManualModel<?> secondaryManualModel) {
        this.secondaryManualModel = secondaryManualModel;
        notifyObservers(SliderModelFields.SECONDARY_MODEL);
    }

    public boolean isResetCalled() {
        return resetCalled;
    }

    public void setResetCalled(boolean resetCalled) {
        this.resetCalled = resetCalled;
        notifyObservers(SliderModelFields.RESET);
    }

    public boolean isSliderVisible() {
        return sliderVisible;
    }

    public void setSliderVisible(boolean visible) {
        this.sliderVisible = visible;
        notifyObservers(SliderModelFields.VISIBILITY);
    }

    @Override
    public void notifyObservers(Object arg) {
        setChanged();
        super.notifyObservers(arg);
    }

    public enum SliderModelFields {
        MANUAL_MODEL, SECONDARY_MODEL, VISIBILITY, RESET
    }
}
