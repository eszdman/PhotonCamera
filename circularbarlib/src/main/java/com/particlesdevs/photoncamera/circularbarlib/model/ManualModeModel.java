package com.particlesdevs.photoncamera.circularbarlib.model;

import android.view.View;

import com.particlesdevs.photoncamera.circularbarlib.R;

import java.util.Observable;

/**
 * The Observable data class responsible for the behaviour and appearance of {@link R.id#manual_mode} layout and its child elements
 * <p>
 * This model is attached to the said layout through DataBinding
 * for more information {@link R.layout#manual_palette }
 * <p>
 * Authors - Vibhor, KillerInk
 */
public class ManualModeModel extends Observable {
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

    public int getSelectedTextViewId() {
        return selectedTextViewId;
    }

    public void setCheckedTextViewId(int selectedTextViewId) {
        this.selectedTextViewId = selectedTextViewId;
        notifyObservers(ManualModelFields.SELECTED_TV);
    }

    public boolean isManualPanelVisible() {
        return manualPanelVisible;
    }

    public void setManualPanelVisible(boolean manualPanelVisible) {
        this.manualPanelVisible = manualPanelVisible;
        notifyObservers(ManualModelFields.PANEL_VISIBILITY);
    }

    public View.OnClickListener getFocusTextClicked() {
        return focusTextClicked;
    }

    public void setFocusTextClicked(View.OnClickListener focusTextClicked) {
        this.focusTextClicked = focusTextClicked;
        notifyObservers(ManualModelFields.FOCUS_LISTENER);
    }

    public View.OnClickListener getExposureTextClicked() {
        return exposureTextClicked;
    }

    public void setExposureTextClicked(View.OnClickListener exposureTextClicked) {
        this.exposureTextClicked = exposureTextClicked;
        notifyObservers(ManualModelFields.EXP_LISTENER);
    }

    public View.OnClickListener getEvTextClicked() {
        return evTextClicked;
    }

    public void setEvTextClicked(View.OnClickListener evTextClicked) {
        this.evTextClicked = evTextClicked;
        notifyObservers(ManualModelFields.EV_LISTENER);
    }

    public View.OnClickListener getIsoTextClicked() {
        return isoTextClicked;
    }

    public void setIsoTextClicked(View.OnClickListener isoTextClicked) {
        this.isoTextClicked = isoTextClicked;
        notifyObservers(ManualModelFields.ISO_LISTENER);
    }

    public String getFocusText() {
        return focusText;
    }

    public void setFocusText(String focusText) {
        this.focusText = focusText;
        notifyObservers(ManualModelFields.FOCUS_TEXT);
    }

    public String getExposureText() {
        return exposureText;
    }

    public void setExposureText(String exposureText) {
        this.exposureText = exposureText;
        notifyObservers(ManualModelFields.EXP_TEXT);

    }

    public String getIsoText() {
        return isoText;
    }

    public void setIsoText(String isoText) {
        this.isoText = isoText;
        notifyObservers(ManualModelFields.ISO_TEXT);
    }

    public String getEvText() {
        return evText;
    }

    public void setEvText(String evText) {
        this.evText = evText;
        notifyObservers(ManualModelFields.EV_TEXT);
    }

    @Override
    public void notifyObservers(Object arg) {
        setChanged();
        super.notifyObservers(arg);
    }

    public enum ManualModelFields {
        FOCUS_TEXT, EXP_TEXT, ISO_TEXT, EV_TEXT, PANEL_VISIBILITY, SELECTED_TV, FOCUS_LISTENER, EXP_LISTENER, EV_LISTENER, ISO_LISTENER
    }
}
