package com.manual;


import android.app.Activity;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Const;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;

/**
 * Created by Vibhor on 10/08/2020
 */
public final class ManualModeImpl implements ManualMode {

    private static final String TAG = "ManualModeImpl";
    private final Activity activity;
    private KnobView exposureKnobView, isoKnobView, focusKnobView, evKnobView;
    private ImageButton exposureButton, isoButton, focusButton, evButton;
    private int exposureVisibilityState, isoVisibilityState, focusVisibilityState, evVisibilityState;
    private ConstraintLayout knob_container, focus_button_container, iso_button_container, exposure_button_container, ev_button_container;

    public ManualModeImpl(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void init() {
        if (activity == null)
            return;
        initialiseDataMembers();
        addKnobs();
        hideAllKnobs("");
        setupOnClickListeners();
    }

    @Override
    public boolean isManualMode() {
        return !(getCurrentExposureValue() == -1.0 && getCurrentFocusValue() == -1.0 && getCurrentISOValue() == -1.0 && getCurrentEvValue() == 0);
    }

    @Override
    public double getCurrentExposureValue() {
        return exposureKnobView.getCurrentKnobItem().value;
    }

    @Override
    public double getCurrentISOValue() {
        return isoKnobView.getCurrentKnobItem().value;
    }

    @Override
    public double getCurrentFocusValue() {
        return focusKnobView.getCurrentKnobItem().value;
    }

    @Override
    public double getCurrentEvValue() {
        return evKnobView.getCurrentKnobItem().value;
    }

    public void resetKnobs() {
        for (int i = 0; i < knob_container.getChildCount(); i++) {
            if (knob_container.getChildAt(i) instanceof KnobView) {
                KnobView kv = (KnobView) knob_container.getChildAt(i);
                kv.setTickByValue(kv.defaultValue);
            }
        }
    }

    @Override
    public void retractAllKnobs() {
        for (int i = 0; i < knob_container.getChildCount(); i++) {
            knob_container.getChildAt(i).setVisibility(View.INVISIBLE);
            if (knob_container.getChildAt(i) instanceof KnobView) {
                KnobView kv = (KnobView) knob_container.getChildAt(i);
                kv.setTickByValue(kv.defaultValue);
//                kv.updateText();// this causes lag
            }
        }
        focusButton.setSelected(false);
        exposureButton.setSelected(false);
        isoButton.setSelected(false);
        evButton.setSelected(false);
    }

    @Override
    public void rotate(int orientation) {
        if (focusKnobView != null && exposureKnobView != null && isoKnobView != null) {
            focusKnobView.setKnobItemsRotation(Rotation.fromDeviceOrientation(Math.abs(orientation / 90) == 1 ? orientation + 180 : orientation));
            exposureKnobView.setKnobItemsRotation(Rotation.fromDeviceOrientation(Math.abs(orientation / 90) == 1 ? orientation + 180 : orientation));
            evKnobView.setKnobItemsRotation(Rotation.fromDeviceOrientation(Math.abs(orientation / 90) == 1 ? orientation + 180 : orientation));
            isoKnobView.setKnobItemsRotation(Rotation.fromDeviceOrientation(Math.abs(orientation / 90) == 1 ? orientation + 180 : orientation));
            focus_button_container.animate().rotation(orientation).setDuration(Interface.i.mainActivity.RotationDur).start();
            exposure_button_container.animate().rotation(orientation).setDuration(Interface.i.mainActivity.RotationDur).start();
            iso_button_container.animate().rotation(orientation).setDuration(Interface.i.mainActivity.RotationDur).start();
            ev_button_container.animate().rotation(orientation).setDuration(Interface.i.mainActivity.RotationDur).start();
        }
    }

    private void initialiseDataMembers() {
        knob_container = activity.findViewById(R.id.knob_container);
        focus_button_container = activity.findViewById(R.id.focus_button_container);
        exposure_button_container = activity.findViewById(R.id.shutter_button_container);
        iso_button_container = activity.findViewById(R.id.iso_button_container);
        ev_button_container = activity.findViewById(R.id.ev_button_container);

        focusButton = activity.findViewById(R.id.focus_option);
        exposureButton = activity.findViewById(R.id.exposure_option);
        isoButton = activity.findViewById(R.id.iso_option);
        evButton = activity.findViewById(R.id.ev_option);
    }

    private void addKnobs() {
        knob_container.removeAllViews();
        CameraCharactersticsOldWay aClass = new CameraCharactersticsOldWay();
        focusKnobView = new FocusKnobView(activity);
        focusKnobView.setRange(aClass.focusRange);
        exposureKnobView = new ExposureTimeKnobView(activity);
        exposureKnobView.setRange(aClass.expRange);
        isoKnobView = new ISOKnobView(activity);
        isoKnobView.setRange(aClass.isoRange);
        evKnobView = new EVKnobView(activity);
        evKnobView.setRange(aClass.evRange);
        aClass.logIt();
        knob_container.addView(focusKnobView, new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        knob_container.addView(exposureKnobView, new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        knob_container.addView(isoKnobView, new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        knob_container.addView(evKnobView, new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void hideAllKnobs(String except) {
        if (!except.equalsIgnoreCase("focus"))
            setFocusKnobVisibility(0);
        if (!except.equalsIgnoreCase("exposure"))
            setExposureKnobVisibility(0);
        if (!except.equalsIgnoreCase("iso"))
            setISOKnobVisibility(0);
        if (!except.equalsIgnoreCase("ev"))
            setEVKnobVisibility(0);
    }

    private void setupOnClickListeners() {

        focusButton.setOnClickListener(v -> {
            hideAllKnobs("focus");
            setFocusKnobVisibility(focusVisibilityState ^= 1);
        });
        focusButton.setOnLongClickListener(v -> {
            focusKnobView.resetKnob();
            focusKnobView.doWhatever();
            focusKnobView.updateText();
            return true;
        });
        exposureButton.setOnClickListener(v -> {
            hideAllKnobs("exposure");
            setExposureKnobVisibility(exposureVisibilityState ^= 1);
        });
        exposureButton.setOnLongClickListener(v -> {
            exposureKnobView.resetKnob();
            exposureKnobView.doWhatever();
            exposureKnobView.updateText();
            return true;
        });
        isoButton.setOnClickListener(v -> {
            hideAllKnobs("iso");
            setISOKnobVisibility(isoVisibilityState ^= 1);
        });
        isoButton.setOnLongClickListener(v -> {
            isoKnobView.resetKnob();
            isoKnobView.doWhatever();
            isoKnobView.updateText();
            return true;
        });
        evButton.setOnClickListener(v -> {
            hideAllKnobs("ev");
            setEVKnobVisibility(evVisibilityState ^= 1);
        });
        evButton.setOnLongClickListener(v -> {
            evKnobView.resetKnob();
            evKnobView.doWhatever();
            evKnobView.updateText();
            return true;
        });
    }

    private void setFocusKnobVisibility(int state) {
        focusVisibilityState = state;
        focusKnobView.setVisibility(focusVisibilityState == 1 ? View.VISIBLE : View.INVISIBLE);
        focusButton.setSelected(focusVisibilityState == 1);
    }

    private void setExposureKnobVisibility(int state) {
        exposureVisibilityState = state;
        exposureKnobView.setVisibility(exposureVisibilityState == 1 ? View.VISIBLE : View.INVISIBLE);
        exposureButton.setSelected(exposureVisibilityState == 1);
    }

    private void setISOKnobVisibility(int state) {
        isoVisibilityState = state;
        isoKnobView.setVisibility(isoVisibilityState == 1 ? View.VISIBLE : View.INVISIBLE);
        isoButton.setSelected(isoVisibilityState == 1);
    }

    private void setEVKnobVisibility(int state) {
        evVisibilityState = state;
        evKnobView.setVisibility(evVisibilityState == 1 ? View.VISIBLE : View.INVISIBLE);
        evButton.setSelected(evVisibilityState == 1);
    }

    static class CameraCharactersticsOldWay {
        CameraCharacteristics cameraCharacteristics = CameraFragment.mCameraCharacteristics;
        Range<Float> focusRange = new Range<>(0f, 10f);
        Range<Integer> isoRange = new Range<>(IsoExpoSelector.getISOLOWExt(), IsoExpoSelector.getISOHIGHExt());
        Range<Long> expRange = new Range<>(IsoExpoSelector.getEXPLOW(), IsoExpoSelector.getEXPHIGH());
        Range<Float> evRange = new Range<>(cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE).getLower().floatValue(), cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE).getUpper().floatValue());

        public void logIt() {
            Log.d(TAG, "focusRange" + focusRange.toString());
            Log.d(TAG, "isoRange" + isoRange.toString());
            Log.d(TAG, "expRange" + expRange.toString());
            Log.d(TAG, "evCompRange" + evRange.toString());
        }

    }
}
