package com.manual;


import android.app.Activity;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.util.Timer;
import com.manual.model.*;

import java.util.Arrays;

/**
 * Created by Vibhor on 10/08/2020
 */
public final class ManualModeImpl implements ManualMode {

    private static final String TAG = "ManualModeImpl";
    private final Activity activity;
    private final ManualModel.ValueChangedEvent evChanged = new ManualModel.ValueChangedEvent() {
        @Override
        public void onValueChanged(String value) {
            evTextView.post(() -> evTextView.setText(value));
        }
    };
    private final ManualModel.ValueChangedEvent expoChanged = new ManualModel.ValueChangedEvent() {
        @Override
        public void onValueChanged(String value) {
            expoTextView.post(() -> expoTextView.setText(value));
        }
    };
    private final ManualModel.ValueChangedEvent isoChanged = new ManualModel.ValueChangedEvent() {
        @Override
        public void onValueChanged(String value) {
            isoTextView.post(() -> isoTextView.setText(value));
        }
    };
    private KnobView defaultKnobView;
    private ManualModel mfModel, isoModel, expoTimeModel, evModel, selectedModel;
    private TextView mfTextView, isoTextView, expoTextView, evTextView;
    private final ManualModel.ValueChangedEvent mfChanged = new ManualModel.ValueChangedEvent() {
        @Override
        public void onValueChanged(String value) {
            mfTextView.post(() -> mfTextView.setText(value));

        }
    };
    private View[] views;

    ManualModeImpl(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void init() {
        if (activity == null)
            return;
        initialiseDataMembers();
        addKnobs();
        setupOnClickListeners();
    }

    @Override
    public boolean isManualMode() {
        return !(getCurrentExposureValue() == -1.0 && getCurrentFocusValue() == -1.0 && getCurrentISOValue() == -1.0 && getCurrentEvValue() == 0);
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
        defaultKnobView.setVisibility(View.GONE);
        defaultKnobView.resetKnob();
        selectedModel = null;
        mfModel.resetModel();
        expoTimeModel.resetModel();
        isoModel.resetModel();
        evModel.resetModel();
        unSelectOthers(null);
    }

   /* @Override
    public void rotate(int orientation, int duration) {
        if (defaultKnobView != null) {
            defaultKnobView.setKnobItemsRotation(Rotation.fromDeviceOrientation(orientation));
            for (int i = 0; i < buttons_container.getChildCount(); i++) {
                buttons_container.getChildAt(i).animate().rotation(orientation).setDuration(duration).start();
            }
        }
    }*/

    private void initialiseDataMembers() {
        defaultKnobView = activity.findViewById(R.id.knobView);
        mfTextView = activity.findViewById(R.id.focus_option_tv);
        evTextView = activity.findViewById(R.id.ev_option_tv);
        expoTextView = activity.findViewById(R.id.exposure_option_tv);
        isoTextView = activity.findViewById(R.id.iso_option_tv);
        views = new View[]{mfTextView, evTextView, expoTextView, isoTextView};
    }

    private void addKnobs() {
        Timer timer = Timer.InitTimer(TAG, "addKnobs");
        CameraCharacteristicsOldWay aClass = new CameraCharacteristicsOldWay();
        mfModel = new FocusModel(activity, aClass.focusRange, mfChanged);
        evModel = new EvModel(activity, aClass.evRange, evChanged);
        isoModel = new IsoModel(activity, aClass.isoRange, isoChanged);
        expoTimeModel = new ShutterModel(activity, aClass.expRange, expoChanged);

        aClass.logIt();
        defaultKnobView.setVisibility(View.GONE);
        unSelectOthers(null);
        timer.endTimer();
    }

    private void setupOnClickListeners() {

        mfTextView.setOnClickListener(v -> setModelToKnob(v, mfModel));
        mfTextView.setOnLongClickListener(v -> {
            if (selectedModel == mfModel)
                defaultKnobView.resetKnob();
            mfModel.resetModel();
            return true;
        });
        expoTextView.setOnClickListener(v -> setModelToKnob(v, expoTimeModel));
        expoTextView.setOnLongClickListener(v -> {
            if (selectedModel == expoTimeModel)
                defaultKnobView.resetKnob();
            expoTimeModel.resetModel();
            return true;
        });
        isoTextView.setOnClickListener(v -> setModelToKnob(v, isoModel));
        isoTextView.setOnLongClickListener(v -> {
            if (selectedModel == isoModel)
                defaultKnobView.resetKnob();
            isoModel.resetModel();
            return true;
        });
        evTextView.setOnClickListener(v -> setModelToKnob(v, evModel));
        evTextView.setOnLongClickListener(v -> {
            if (selectedModel == evModel)
                defaultKnobView.resetKnob();
            evModel.resetModel();
            return true;
        });

        mfModel.fireValueChangedEvent(mfModel.getAutoModel().text);
        evModel.fireValueChangedEvent(evModel.getAutoModel().text);
        expoTimeModel.fireValueChangedEvent(expoTimeModel.getAutoModel().text);
        isoModel.fireValueChangedEvent(isoModel.getAutoModel().text);
    }

    private void setModelToKnob(View view, ManualModel modelToKnob) {
        view.setSelected(!view.isSelected());
        unSelectOthers(view);
        if (modelToKnob == selectedModel) {
//            defaultKnobView.resetKnob();
            defaultKnobView.setKnobViewChangedListener(null);
            defaultKnobView.setVisibility(View.GONE);
            selectedModel = null;
        } else {
            //defaultKnobView.resetKnob();
            if (modelToKnob.getKnobInfoList().size() > 1) {
                defaultKnobView.setKnobViewChangedListener(modelToKnob);
                defaultKnobView.setKnobInfo(modelToKnob.getKnobInfo());
                defaultKnobView.setKnobItems(modelToKnob.getKnobInfoList());
                defaultKnobView.setTickByValue(modelToKnob.getCurrentInfo().value);
                defaultKnobView.setVisibility(View.VISIBLE);
                selectedModel = modelToKnob;
            }
        }
    }

    private void unSelectOthers(@Nullable View v) {
        Arrays.stream(views).forEach(view -> {
            if (v == null || !v.equals(view))
                view.setSelected(false);
        });
    }

    static class CameraCharacteristicsOldWay {
        CameraCharacteristics cameraCharacteristics = CaptureController.mCameraCharacteristics;
        Float minFocal = CaptureController.mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        Float maxFocal = CaptureController.mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);
        Range<Float> focusRange = (!(minFocal == null || maxFocal == null || minFocal == 0.0f)) ? new Range<>(Math.min(minFocal, maxFocal), Math.max(minFocal, maxFocal)) : null;
        Range<Integer> isoRange = new Range<>(IsoExpoSelector.getISOLOWExt(), IsoExpoSelector.getISOHIGHExt());
        Range<Long> expRange = new Range<>(IsoExpoSelector.getEXPLOW(), IsoExpoSelector.getEXPHIGH());
        float evStep = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();
        Range<Float> evRange = new Range<>((cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE).getLower() * evStep), (cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE).getUpper() * evStep));

        public void logIt() {
            String lens = PhotonCamera.getSettings().mCameraID;
            Log.d(TAG, "focusRange(" + lens + ") : " + (focusRange == null ? "Fixed [" + maxFocal + "]" : focusRange.toString()));
            Log.d(TAG, "isoRange(" + lens + ") : " + isoRange.toString());
            Log.d(TAG, "expRange(" + lens + ") : " + expRange.toString());
            Log.d(TAG, "evCompRange(" + lens + ") : " + evRange.toString());
        }

    }
}
