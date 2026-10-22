package com.particlesdevs.photoncamera.circularbarlib.model;

import android.view.View;

import com.particlesdevs.photoncamera.circularbarlib.R;

import java.util.Objects;
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
    private String wbText;
    private String focusAutoText;
    private String exposureAutoText;
    private String isoAutoText;
    private String wbAutoText;
    private View.OnClickListener focusTextClicked;
    private View.OnClickListener exposureTextClicked;
    private View.OnClickListener evTextClicked;
    private View.OnClickListener isoTextClicked;
    private View.OnClickListener wbTextClicked;
    private boolean manualPanelVisible;
    private int selectedTextViewId;
    private int secondaryTextViewId = -1;

    public int getSelectedTextViewId() {
        return selectedTextViewId;
    }

    /** View id of the remembered control shown as the inner ruler, or -1. */
    public int getSecondaryTextViewId() {
        return secondaryTextViewId;
    }

    public void setCheckedTextViewId(int selectedTextViewId) {
        setCheckedTextViewIds(selectedTextViewId, -1);
    }

    /**
     * Selected renders the filled selection pill; secondary (the remembered
     * control riding the inner ruler) renders the same pill as a thin ring.
     */
    public void setCheckedTextViewIds(int selectedTextViewId, int secondaryTextViewId) {
        this.selectedTextViewId = selectedTextViewId;
        this.secondaryTextViewId = secondaryTextViewId;
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

    public View.OnClickListener getWbTextClicked() {
        return wbTextClicked;
    }

    public void setWbTextClicked(View.OnClickListener wbTextClicked) {
        this.wbTextClicked = wbTextClicked;
        notifyObservers(ManualModelFields.WB_LISTENER);
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

    public String getWbText() {
        return wbText;
    }

    public void setWbText(String wbText) {
        this.wbText = wbText;
        notifyObservers(ManualModelFields.WB_TEXT);
    }

    public String getFocusAutoText() {
        return focusAutoText;
    }

    public void setFocusAutoText(String focusAutoText) {
        if (Objects.equals(this.focusAutoText, focusAutoText)) return;
        this.focusAutoText = focusAutoText;
        notifyObservers(ManualModelFields.FOCUS_AUTO_TEXT);
    }

    public String getExposureAutoText() {
        return exposureAutoText;
    }

    public void setExposureAutoText(String exposureAutoText) {
        if (Objects.equals(this.exposureAutoText, exposureAutoText)) return;
        this.exposureAutoText = exposureAutoText;
        notifyObservers(ManualModelFields.EXP_AUTO_TEXT);
    }

    public String getIsoAutoText() {
        return isoAutoText;
    }

    public void setIsoAutoText(String isoAutoText) {
        if (Objects.equals(this.isoAutoText, isoAutoText)) return;
        this.isoAutoText = isoAutoText;
        notifyObservers(ManualModelFields.ISO_AUTO_TEXT);
    }

    public String getWbAutoText() {
        return wbAutoText;
    }

    public void setWbAutoText(String wbAutoText) {
        if (Objects.equals(this.wbAutoText, wbAutoText)) return;
        this.wbAutoText = wbAutoText;
        notifyObservers(ManualModelFields.WB_AUTO_TEXT);
    }

    @Override
    public void notifyObservers(Object arg) {
        setChanged();
        super.notifyObservers(arg);
    }

    public enum ManualModelFields {
        FOCUS_TEXT, EXP_TEXT, ISO_TEXT, EV_TEXT, WB_TEXT, PANEL_VISIBILITY, SELECTED_TV, FOCUS_LISTENER, EXP_LISTENER, EV_LISTENER, ISO_LISTENER, WB_LISTENER, FOCUS_AUTO_TEXT, EXP_AUTO_TEXT, ISO_AUTO_TEXT, WB_AUTO_TEXT
    }
}
