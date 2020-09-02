package com.manual;


import android.app.Activity;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import com.eszdman.photoncamera.Parameters.IsoExpoSelector;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;
import com.manual.model.EvModel;
import com.manual.model.FocusModel;
import com.manual.model.IsoModel;
import com.manual.model.ManualModel;
import com.manual.model.ShutterModel;

/**
 * Created by Vibhor on 10/08/2020
 */
public final class ManualModeImpl implements ManualMode {

    private static final String TAG = "ManualModeImpl";
    private final Activity activity;
    private KnobView defaultKnobView;
    private ImageButton exposureButton, isoButton, focusButton, evButton;
    //private int exposureVisibilityState, isoVisibilityState, focusVisibilityState, evVisibilityState;
    private ConstraintLayout knob_container;//, focus_button_container, iso_button_container, exposure_button_container, ev_button_container;
    private ManualModel mfModel, isoModel, expotimeModel, evModel, selectedModel;
    private TextView mfTextView,isoTextview, expoTextView, evTextview;

    private ManualModel.ValueChangedEvent mfchanged = new ManualModel.ValueChangedEvent() {
        @Override
        public void onValueChanged(String value) {
            mfTextView.post(()-> mfTextView.setText(value));

        }
    };

    private ManualModel.ValueChangedEvent evchanged = new ManualModel.ValueChangedEvent() {
        @Override
        public void onValueChanged(String value) {
                evTextview.post(()->evTextview.setText(value));
        }
    };

    private ManualModel.ValueChangedEvent expochanged = new ManualModel.ValueChangedEvent() {
        @Override
        public void onValueChanged(String value) {

                expoTextView.post(()->expoTextView.setText(value));
        }
    };

    private ManualModel.ValueChangedEvent isochanged = new ManualModel.ValueChangedEvent() {
        @Override
        public void onValueChanged(String value) {
                isoTextview.post(()->isoTextview.setText(value));
        }
    };

    public ManualModeImpl(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void init() {
        if (activity == null)
            return;
        initialiseDataMembers();
        addKnobs();
        //hideAllKnobs("");
        setupOnClickListeners();
    }

    @Override
    public boolean isManualMode() {
        return !(getCurrentExposureValue() == -1.0 && getCurrentFocusValue() == -1.0 && getCurrentISOValue() == -1.0 && getCurrentEvValue() == 0);
    }

    @Override
    public double getCurrentExposureValue() {
        return expotimeModel.getCurrentInfo().value;
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
        if (defaultKnobView != null) {
            defaultKnobView.setKnobItemsRotation(Rotation.fromDeviceOrientation(Math.abs(orientation / 90) == 1 ? orientation + 180 : orientation));
            /*focus_button_container.animate().rotation(orientation).setDuration(Interface.i.mainActivity.RotationDur).start();
            exposure_button_container.animate().rotation(orientation).setDuration(Interface.i.mainActivity.RotationDur).start();
            iso_button_container.animate().rotation(orientation).setDuration(Interface.i.mainActivity.RotationDur).start();
            ev_button_container.animate().rotation(orientation).setDuration(Interface.i.mainActivity.RotationDur).start();*/
        }
    }

    private void initialiseDataMembers() {
        knob_container = activity.findViewById(R.id.knob_container);
        /*focus_button_container = activity.findViewById(R.id.focus_button_container);
        exposure_button_container = activity.findViewById(R.id.shutter_button_container);
        iso_button_container = activity.findViewById(R.id.iso_button_container);
        ev_button_container = activity.findViewById(R.id.ev_button_container);*/

        focusButton = activity.findViewById(R.id.focus_option);
        exposureButton = activity.findViewById(R.id.exposure_option);
        isoButton = activity.findViewById(R.id.iso_option);
        evButton = activity.findViewById(R.id.ev_option);
        mfTextView = (TextView)Interface.i.mainActivity.findViewById(R.id.focus_option_tv);
        evTextview = (TextView)Interface.i.mainActivity.findViewById(R.id.ev_option_tv);
        expoTextView = (TextView)Interface.i.mainActivity.findViewById(R.id.exposure_option_tv);
        isoTextview = (TextView)Interface.i.mainActivity.findViewById(R.id.iso_option_tv);
    }

    private void addKnobs() {
        knob_container.removeAllViews();
        defaultKnobView = new KnobView(activity);

        CameraCharactersticsOldWay aClass = new CameraCharactersticsOldWay();

        mfModel = new FocusModel(aClass.focusRange, mfchanged);

        evModel = new EvModel(aClass.evRange, evchanged);

        isoModel = new IsoModel(aClass.isoRange, isochanged);

        expotimeModel = new ShutterModel(aClass.expRange, expochanged);


        aClass.logIt();
        knob_container.addView(defaultKnobView, new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /*private void hideAllKnobs(String except) {
        if (!except.equalsIgnoreCase("focus"))
            setFocusKnobVisibility(0);
        if (!except.equalsIgnoreCase("exposure"))
            setExposureKnobVisibility(0);
        if (!except.equalsIgnoreCase("iso"))
            setISOKnobVisibility(0);
        if (!except.equalsIgnoreCase("ev"))
            setEVKnobVisibility(0);
    }*/

    private void setModelToKnob(ManualModel modelToKnob)
    {
        if (modelToKnob == selectedModel)
        {
            defaultKnobView.resetKnob();
            defaultKnobView.setKnobViewChangedListener(null);
            defaultKnobView.setVisibility(View.GONE);
            knob_container.setVisibility(View.GONE);
            selectedModel = null;
        }
        else {
            //defaultKnobView.resetKnob();
            defaultKnobView.setKnobViewChangedListener(modelToKnob);
            defaultKnobView.setKnobInfo(modelToKnob.getKnobInfo());
            defaultKnobView.setKnobItems(modelToKnob.getKnobInfoList());

            defaultKnobView.setVisibility(View.VISIBLE);
            knob_container.setVisibility(View.VISIBLE);
            selectedModel = modelToKnob;
        }
    }

    private void setupOnClickListeners() {

        focusButton.setOnClickListener(v -> {
            setModelToKnob(mfModel);
            defaultKnobView.setDashAroundAutoEnabled(false);
        });
        focusButton.setOnLongClickListener(v -> {
            defaultKnobView.resetKnob();
            mfModel.onSelectedKnobItemChanged(defaultKnobView,null,mfModel.getCurrentInfo());

            return true;
        });
        exposureButton.setOnClickListener(v -> {
            setModelToKnob(expotimeModel);
        });
        exposureButton.setOnLongClickListener(v -> {
            defaultKnobView.resetKnob();
            expotimeModel.onSelectedKnobItemChanged(defaultKnobView,null,expotimeModel.getCurrentInfo());

            return true;
        });
        isoButton.setOnClickListener(v -> {
            setModelToKnob(isoModel);
        });
        isoButton.setOnLongClickListener(v -> {
            defaultKnobView.resetKnob();
            isoModel.onSelectedKnobItemChanged(defaultKnobView,null,isoModel.getCurrentInfo());

            return true;
        });
        evButton.setOnClickListener(v -> {
           setModelToKnob(evModel);
        });
        evButton.setOnLongClickListener(v -> {
            defaultKnobView.resetKnob();
            evModel.onSelectedKnobItemChanged(defaultKnobView,null,evModel.getCurrentInfo());

            return true;
        });

        mfModel.fireValueChangedEvent(mfModel.getCurrentInfo().text);
        evModel.fireValueChangedEvent(evModel.getCurrentInfo().text);
        expotimeModel.fireValueChangedEvent(expotimeModel.getCurrentInfo().text);
        isoModel.fireValueChangedEvent(isoModel.getCurrentInfo().text);
    }

    /*private void setFocusKnobVisibility(int state) {
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
    }*/

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
