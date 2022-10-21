package com.particlesdevs.photoncamera.circularbarlib.model;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.control.models.ManualModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;

import java.util.Observable;

/**
 * The Observable data class responsible for the behaviour and appearance of {@link KnobView}
 * <p>
 * This model is attached to the said layout through DataBinding
 * for more information {@link R.layout#manual_palette }
 * <p>
 * Authors - Vibhor, KillerInk
 */
public class KnobModel extends Observable {
    boolean knobResetCalled;
    private boolean knobVisible;
    private ManualModel<?> manualModel;

    public ManualModel<?> getManualModel() {
        return manualModel;
    }

    public void setManualModel(ManualModel<?> manualModel) {
        this.manualModel = manualModel;
        notifyObservers(KnobModelFields.MANUAL_MODEL);

    }

    public boolean isKnobResetCalled() {
        return knobResetCalled;
    }

    public void setKnobResetCalled(boolean resetCalled) {
        this.knobResetCalled = resetCalled;
        notifyObservers(KnobModelFields.RESET);

    }

    public boolean isKnobVisible() {
        return knobVisible;
    }

    public void setKnobVisible(boolean knobVisible) {
        this.knobVisible = knobVisible;
        notifyObservers(KnobModelFields.VISIBILITY);


    }

    @Override
    public void notifyObservers(Object arg) {
        setChanged();
        super.notifyObservers(arg);

    }

    public enum KnobModelFields {
        MANUAL_MODEL, VISIBILITY, RESET
    }
}
