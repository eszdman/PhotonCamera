package com.particlesdevs.photoncamera.manual;


import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.manual.model.*;
import com.particlesdevs.photoncamera.util.Timer;

import java.util.Arrays;

/**
 * Created by Vibhor on 10/08/2020
 */
public final class ManualModeImpl implements ManualMode {

    private static final String TAG = "ManualModeImpl";
    private final Activity activity;
    private final TextView mfTextView, isoTextView, expoTextView, evTextView;
    private final KnobView knobView;
    private final View[] textViews;
    private ManualModel mfModel, isoModel, expoTimeModel, evModel, selectedModel;

    ManualModeImpl(Activity activity) {
        this.activity = activity;
        this.knobView = activity.findViewById(R.id.knobView);
        this.mfTextView = activity.findViewById(R.id.focus_option_tv);
        this.evTextView = activity.findViewById(R.id.ev_option_tv);
        this.expoTextView = activity.findViewById(R.id.exposure_option_tv);
        this.isoTextView = activity.findViewById(R.id.iso_option_tv);
        this.textViews = new View[]{mfTextView, evTextView, expoTextView, isoTextView};
    }

    @Override
    public void init() {
        if (activity == null)
            return;
        addKnobs();
        setupOnClickListeners();
    }

    @Override
    public boolean isManualMode() {
        return !(getCurrentExposureValue() == ManualModel.SHUTTER_AUTO
                && getCurrentFocusValue() == ManualModel.FOCUS_AUTO
                && getCurrentISOValue() == ManualModel.ISO_AUTO
                && getCurrentEvValue() == ManualModel.EV_AUTO);
    }

    @Override
    public double getCurrentExposureValue() {
        return expoTimeModel.getCurrentInfo().value;
    }

    @Override
    public double getCurrentISOValue() {
        return isoModel.getCurrentInfo().value;
    }

    @Override
    public double getCurrentFocusValue() {
        return mfModel.getCurrentInfo().value;
    }

    @Override
    public double getCurrentEvValue() {
        return evModel.getCurrentInfo().value;
    }

    @Override
    public void retractAllKnobs() {
        hideKnob();
        knobView.resetKnob();
        selectedModel = null;
        mfModel.resetModel();
        expoTimeModel.resetModel();
        isoModel.resetModel();
        evModel.resetModel();
        unSelectOthers(null);
    }

    private void addKnobs() {
        Timer timer = Timer.InitTimer(TAG, "addKnobs");
        CaptureController.CameraProperties cameraProperties = new CaptureController.CameraProperties();
        mfModel = new FocusModel(activity, cameraProperties.focusRange, value -> updateText(mfTextView, value));
        evModel = new EvModel(activity, cameraProperties.evRange, value -> updateText(evTextView, value));
        isoModel = new IsoModel(activity, cameraProperties.isoRange, value -> updateText(isoTextView, value));
        expoTimeModel = new ShutterModel(activity, cameraProperties.expRange, value -> updateText(expoTextView, value));
        hideKnob();
        unSelectOthers(null);
        timer.endTimer();
    }

    private void updateText(TextView tv, String value) {
        tv.post(() -> tv.setText(value));
    }

    private void setupOnClickListeners() {
        setListeners(mfTextView, mfModel);
        setListeners(expoTextView, expoTimeModel);
        setListeners(isoTextView, isoModel);
        setListeners(evTextView, evModel);
    }

    private void setListeners(TextView tv, ManualModel model) {
        tv.setOnClickListener(v -> setModelToKnob(v, model));
        tv.setOnLongClickListener(v -> {
            if (selectedModel == model)
                knobView.resetKnob();
            model.resetModel();
            return true;
        });
        model.fireValueChangedEvent(model.getAutoModel().text);
    }

    private void setModelToKnob(View view, ManualModel modelToKnob) {
        view.setSelected(!view.isSelected());
        unSelectOthers(view);
        if (modelToKnob == selectedModel) {
//            knobView.resetKnob();
            knobView.setKnobViewChangedListener(null);
            hideKnob();
            selectedModel = null;
        } else {
//            knobView.resetKnob();
            if (modelToKnob.getKnobInfoList().size() > 1) {
                knobView.setKnobViewChangedListener(modelToKnob);
                knobView.setKnobInfo(modelToKnob.getKnobInfo());
                knobView.setKnobItems(modelToKnob.getKnobInfoList());
                knobView.setTickByValue(modelToKnob.getCurrentInfo().value);
                showKnob();
                selectedModel = modelToKnob;
            }
        }
    }

    private void showKnob() {
        knobView.animate().translationY(0).scaleY(1).scaleX(1).setDuration(200).alpha(1f).start();
        knobView.setVisibility(View.VISIBLE);
    }

    private void hideKnob() {
        knobView.animate().translationY(knobView.getHeight() / 2.5f)
                .scaleY(.2f).scaleX(.2f).setDuration(200).alpha(0f).withEndAction(() -> knobView.setVisibility(View.GONE)).start();
    }

    private void unSelectOthers(@Nullable View v) {
        Arrays.stream(textViews).forEach(view -> {
            if (v == null || !v.equals(view))
                view.setSelected(false);
        });
    }
}
