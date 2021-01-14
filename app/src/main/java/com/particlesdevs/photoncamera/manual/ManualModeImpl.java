package com.particlesdevs.photoncamera.manual;


import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.util.Timer;
import com.particlesdevs.photoncamera.manual.model.*;

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
        hideKnob();
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
        CaptureController.CameraProperties cameraProperties = new CaptureController.CameraProperties();
        mfModel = new FocusModel(activity, cameraProperties.focusRange, mfChanged);
        evModel = new EvModel(activity, cameraProperties.evRange, evChanged);
        isoModel = new IsoModel(activity, cameraProperties.isoRange, isoChanged);
        expoTimeModel = new ShutterModel(activity, cameraProperties.expRange, expoChanged);
        hideKnob();
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
            hideKnob();
            selectedModel = null;
        } else {
            //defaultKnobView.resetKnob();
            if (modelToKnob.getKnobInfoList().size() > 1) {
                defaultKnobView.setKnobViewChangedListener(modelToKnob);
                defaultKnobView.setKnobInfo(modelToKnob.getKnobInfo());
                defaultKnobView.setKnobItems(modelToKnob.getKnobInfoList());
                defaultKnobView.setTickByValue(modelToKnob.getCurrentInfo().value);
                showKnob();
                selectedModel = modelToKnob;
            }
        }
    }

    private void showKnob() {
        defaultKnobView.animate().translationY(0).scaleY(1).scaleX(1).setDuration(200).alpha(1f).start();
        defaultKnobView.setVisibility(View.VISIBLE);
    }

    private void hideKnob() {
        defaultKnobView.animate().translationY(defaultKnobView.getHeight() / 2.5f)
                .scaleY(.2f).scaleX(.2f).setDuration(200).alpha(0f).withEndAction(() -> defaultKnobView.setVisibility(View.GONE)).start();
    }

    private void unSelectOthers(@Nullable View v) {
        Arrays.stream(views).forEach(view -> {
            if (v == null || !v.equals(view))
                view.setSelected(false);
        });
    }
}
