package com.particlesdevs.photoncamera.ui.camera.model;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import com.particlesdevs.photoncamera.BR;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.manual.model.ManualModel;
import com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview.KnobView;

/**
 * The Observable data class responsible for the behaviour and appearance of {@link KnobView}
 * <p>
 * This model is attached to the said layout through DataBinding
 * for more information {@link R.layout#manual_palette }
 * <p>
 * Authors - Vibhor, KillerInk
 */
public class KnobModel extends BaseObservable {
    boolean knobResetCalled;
    private boolean knobVisible;
    private ManualModel manualModel;

    @Bindable
    public ManualModel getManualModel() {
        return manualModel;
    }

    public void setManualModel(ManualModel manualModel) {
        this.manualModel = manualModel;
        notifyPropertyChanged(BR.manualModel);
    }

    @Bindable
    public boolean isKnobResetCalled() {
        return knobResetCalled;
    }

    public void setKnobResetCalled(boolean resetCalled) {
        this.knobResetCalled = resetCalled;
        notifyPropertyChanged(BR.knobResetCalled);
    }

    @Bindable
    public boolean isKnobVisible() {
        return knobVisible;
    }

    public void setKnobVisible(boolean knobVisible) {
        this.knobVisible = knobVisible;
        notifyPropertyChanged(BR.knobVisible);
    }
}
