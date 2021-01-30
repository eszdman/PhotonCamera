package com.particlesdevs.photoncamera.ui.camera.viewmodel;

import android.content.Context;
import android.view.View;
import android.widget.CheckedTextView;

import androidx.lifecycle.ViewModel;

import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.manual.ManualParamModel;
import com.particlesdevs.photoncamera.manual.model.EvModel;
import com.particlesdevs.photoncamera.manual.model.FocusModel;
import com.particlesdevs.photoncamera.manual.model.IsoModel;
import com.particlesdevs.photoncamera.manual.model.ManualModel;
import com.particlesdevs.photoncamera.manual.model.ShutterModel;
import com.particlesdevs.photoncamera.ui.camera.model.KnobModel;
import com.particlesdevs.photoncamera.ui.camera.model.ManualModeModel;
import com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview.KnobView;
import com.particlesdevs.photoncamera.util.Timer;

/**
 * The ViewModel responsible for initialising and updating {@link KnobModel} and {@link ManualModeModel}
 * <p>
 * This class also manages the attaching/detaching of {@link ManualModel} subclasses to {@link KnobView}
 * and setting listeners to models
 * <p>
 * Authors - Vibhor, KillerInk
 */
public class ManualModeViewModel extends ViewModel {
    private static final String TAG = "ManualModeViewModel";
    private final ManualModeModel manualModeModel;
    private final KnobModel knobModel;
    private ManualModel<?> mfModel, isoModel, expoTimeModel, evModel, selectedModel;
    private ManualParamModel manualParamModel;

    public ManualModeViewModel() {
        this.manualModeModel = new ManualModeModel();
        this.knobModel = new KnobModel();
    }

    public ManualModeModel getManualModeModel() {
        return manualModeModel;
    }

    public void setManualParamModel(ManualParamModel manualParamModel) {
        this.manualParamModel = manualParamModel;
    }

    public KnobModel getKnobModel() {
        return knobModel;
    }

    public void init(Context context) {
        addKnobs(context);
        setupOnClickListeners();
        setAutoText();
    }

    private void addKnobs(Context context) {
        Timer timer = Timer.InitTimer(TAG, "addKnobs");
        CaptureController.CameraProperties cameraProperties = new CaptureController.CameraProperties();
        if (manualParamModel != null) {
            manualParamModel.reset();
            mfModel = new FocusModel(context, cameraProperties.focusRange, manualParamModel, manualModeModel::setFocusText);
            evModel = new EvModel(context, cameraProperties.evRange, manualParamModel, manualModeModel::setEvText);
            isoModel = new IsoModel(context, cameraProperties.isoRange, manualParamModel, manualModeModel::setIsoText);
            expoTimeModel = new ShutterModel(context, cameraProperties.expRange, manualParamModel, manualModeModel::setExposureText);
            knobModel.setKnobVisible(false);
            manualModeModel.setCheckedTextViewId(-1);
        } else {
            try {
                throw new NullPointerException("manualParamModel is null, make sure to call setManualParamModel()");
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
        timer.endTimer();
    }

    public boolean togglePanelVisibility(boolean visible) {
        manualModeModel.setManualPanelVisible(visible);
        if (!visible) {
            manualParamModel.reset();
        }
        return visible;
    }

    private void setupOnClickListeners() {
        manualModeModel.setFocusTextClicked(v -> setListeners(v, mfModel));
        manualModeModel.setEvTextClicked(v -> setListeners(v, evModel));
        manualModeModel.setExposureTextClicked(v -> setListeners(v, expoTimeModel));
        manualModeModel.setIsoTextClicked(v -> setListeners(v, isoModel));
    }

    private void setListeners(View view, ManualModel<?> model) {
        setModelToKnob(view.getId(), model);
        view.setOnLongClickListener(v -> {
            if (selectedModel == model) {
                knobModel.setKnobResetCalled(true);
            }
            model.resetModel();
            return true;
        });
    }

    private void setAutoText() {
        evModel.setAutoTxt();
        mfModel.setAutoTxt();
        expoTimeModel.setAutoTxt();
        isoModel.setAutoTxt();
    }

    public void retractAllKnobs() {
        knobModel.setKnobVisible(false);
        knobModel.setKnobResetCalled(true);
        selectedModel = null;
        mfModel.resetModel();
        expoTimeModel.resetModel();
        isoModel.resetModel();
        evModel.resetModel();
        manualModeModel.setCheckedTextViewId(-1);
    }

    private void setModelToKnob(int viewId, ManualModel<?> modelToKnob) {
        if (modelToKnob == selectedModel) {
            knobModel.setManualModel(null);
            knobModel.setKnobVisible(false);
            manualModeModel.setCheckedTextViewId(-1);
            selectedModel = null;
        } else {
            if (modelToKnob.getKnobInfoList().size() > 1) {
                knobModel.setManualModel(modelToKnob);
                knobModel.setKnobVisible(true);
                manualModeModel.setCheckedTextViewId(viewId);
                selectedModel = modelToKnob;
            }
        }
    }
}
