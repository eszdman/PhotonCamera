package com.particlesdevs.photoncamera.ui.camera.model;

import android.view.View;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import com.particlesdevs.photoncamera.BR;
import com.particlesdevs.photoncamera.R;

/**
 * The Observable data class responsible for the behaviour and appearance of {@link R.id#manual_mode} layout and its child elements
 * <p>
 * This model is attached to the said layout through DataBinding
 * for more information {@link R.layout#manual_palette }
 * <p>
 * Authors - Vibhor, KillerInk
 */
public class ManualModeModel extends BaseObservable {
    private String focusText;
    private String exposureText;
    private String isoText;
    private String evText;
    private View.OnClickListener focusTextClicked;
    private View.OnClickListener exposureTextClicked;
    private View.OnClickListener evTextClicked;
    private View.OnClickListener isoTextClicked;
    private boolean manualPanelVisible;
    private int selectedTextViewId;

    @Bindable
    public int getSelectedTextViewId() {
        return selectedTextViewId;
    }

    public void setCheckedTextViewId(int selectedTextViewId) {
        this.selectedTextViewId = selectedTextViewId;
        notifyPropertyChanged(BR.selectedTextViewId);
    }

    @Bindable
    public boolean isManualPanelVisible() {
        return manualPanelVisible;
    }

    public void setManualPanelVisible(boolean manualPanelVisible) {
        this.manualPanelVisible = manualPanelVisible;
        notifyPropertyChanged(BR.manualPanelVisible);
    }

    @Bindable
    public View.OnClickListener getFocusTextClicked() {
        return focusTextClicked;
    }

    public void setFocusTextClicked(View.OnClickListener focusTextClicked) {
        this.focusTextClicked = focusTextClicked;
        notifyPropertyChanged(BR.focusTextClicked);
    }

    @Bindable
    public View.OnClickListener getExposureTextClicked() {
        return exposureTextClicked;
    }

    public void setExposureTextClicked(View.OnClickListener exposureTextClicked) {
        this.exposureTextClicked = exposureTextClicked;
        notifyPropertyChanged(BR.exposureTextClicked);
    }

    @Bindable
    public View.OnClickListener getEvTextClicked() {
        return evTextClicked;
    }

    public void setEvTextClicked(View.OnClickListener evTextClicked) {
        this.evTextClicked = evTextClicked;
        notifyPropertyChanged(BR.evTextClicked);
    }

    @Bindable
    public View.OnClickListener getIsoTextClicked() {
        return isoTextClicked;
    }

    public void setIsoTextClicked(View.OnClickListener isoTextClicked) {
        this.isoTextClicked = isoTextClicked;
        notifyPropertyChanged(BR.isoTextClicked);
    }

    @Bindable
    public String getFocusText() {
        return focusText;
    }

    public void setFocusText(String focusText) {
        this.focusText = focusText;
        notifyPropertyChanged(BR.focusText);
    }

    @Bindable
    public String getExposureText() {
        return exposureText;
    }

    public void setExposureText(String exposureText) {
        this.exposureText = exposureText;
        notifyPropertyChanged(BR.exposureText);
    }

    @Bindable
    public String getIsoText() {
        return isoText;
    }

    public void setIsoText(String isoText) {
        this.isoText = isoText;
        notifyPropertyChanged(BR.isoText);
    }

    @Bindable
    public String getEvText() {
        return evText;
    }

    public void setEvText(String evText) {
        this.evText = evText;
        notifyPropertyChanged(BR.evText);
    }
}
