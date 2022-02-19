package com.particlesdevs.photoncamera.circularbarlib.console;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Vibrator;
import android.view.View;

import com.particlesdevs.photoncamera.circularbarlib.api.ManualModeConsole;
import com.particlesdevs.photoncamera.circularbarlib.camera.CameraProperties;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.EvModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.FocusModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.IsoModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.ManualModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.ShutterModel;
import com.particlesdevs.photoncamera.circularbarlib.model.KnobModel;
import com.particlesdevs.photoncamera.circularbarlib.model.ManualModeModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.ViewObserver;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;

import java.util.Observer;

/**
 * Responsible for initialising and updating {@link KnobModel} and {@link ManualModeModel}
 * <p>
 * This class also manages the attaching/detaching of {@link ManualModel} subclasses to {@link KnobView}
 * and setting listeners to models
 * <p>
 * Authors - Vibhor, KillerInk
 */
public class ManualModeConsoleImpl implements ManualModeConsole {
    private static final String TAG = "ManualModeConsole";
    private static ManualModeConsole sInstance;
    private final ManualModeModel manualModeModel;
    private final KnobModel knobModel;
    private final ManualParamModel manualParamModel = new ManualParamModel();
    private ManualModel<?> mfModel, isoModel, expoTimeModel, evModel, selectedModel;
    private ViewObserver viewObserver;

    private ManualModeConsoleImpl() {
        this.manualModeModel = new ManualModeModel();
        this.knobModel = new KnobModel();
    }

    public static ManualModeConsole getInstance() {
        if (sInstance == null) {
            sInstance = newInstance();
        }
        return sInstance;
    }

    public static ManualModeConsole newInstance() {
        return new ManualModeConsoleImpl();
    }

    public ManualModeModel getManualModeModel() {
        return manualModeModel;
    }

    @Override
    public void addParamObserver(Observer observer) {
        manualParamModel.addObserver(observer);
    }

    @Override
    public void removeParamObservers() {
        manualParamModel.deleteObservers();
    }

    @Override
    public ManualParamModel getManualParamModel() {
        return manualParamModel;
    }

    public KnobModel getKnobModel() {
        return knobModel;
    }

    @Override
    public void init(Activity activity, CameraCharacteristics cameraCharacteristics) {
        viewObserver = new ViewObserver(activity);
        addObserver();
        addKnobs(activity, cameraCharacteristics);
        setupOnClickListeners();
        setAutoText();
    }

    @Override
    public void onResume() {
        if (viewObserver != null) {
            viewObserver.enableOrientationListener();
        }
        addObserver();
    }

    @Override
    public void onPause() {
        if (viewObserver != null) {
            viewObserver.disableOrientationListener();
        }
        removeObservers();
    }

    @Override
    public void onDestroy() {
        sInstance = null;
    }

    private void addObserver() {
        if (viewObserver != null) {
            removeObservers();
            knobModel.addObserver(viewObserver);
            manualModeModel.addObserver(viewObserver);
        }
    }

    private void removeObservers() {
        knobModel.deleteObservers();
        manualModeModel.deleteObservers();
    }

    private void addKnobs(Context context, CameraCharacteristics cameraCharacteristics) {
        CameraProperties cameraProperties = new CameraProperties(cameraCharacteristics);
        manualParamModel.reset();
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mfModel = new FocusModel(context, cameraCharacteristics, cameraProperties.focusRange, manualParamModel, manualModeModel::setFocusText,v);
        evModel = new EvModel(context, cameraCharacteristics, cameraProperties.evRange, manualParamModel, manualModeModel::setEvText,v);
        ((EvModel) evModel).setEvStep((cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue()));
        isoModel = new IsoModel(context, cameraCharacteristics, cameraProperties.isoRange, manualParamModel, manualModeModel::setIsoText,v);
        expoTimeModel = new ShutterModel(context, cameraCharacteristics, cameraProperties.expRange, manualParamModel, manualModeModel::setExposureText,v);
        knobModel.setKnobVisible(false);
        manualModeModel.setCheckedTextViewId(-1);
    }

    @Override
    public void setPanelVisibility(boolean visible) {
        manualModeModel.setManualPanelVisible(visible);
        if (!visible) {
            manualParamModel.reset();
        }
    }

    @Override
    public boolean isManualMode() {
        return manualParamModel.isManualMode();
    }

    @Override
    public void resetAllValues() {
        manualParamModel.reset();
    }

    @Override
    public boolean isPanelVisible() {
        return manualModeModel.isManualPanelVisible();
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
        if(evModel != null)
        evModel.setAutoTxt();
        if(mfModel != null)
        mfModel.setAutoTxt();
        if(expoTimeModel != null)
        expoTimeModel.setAutoTxt();
        if(isoModel != null)
        isoModel.setAutoTxt();
    }

    @Override
    public void retractAllKnobs() {
        knobModel.setKnobVisible(false);
        knobModel.setKnobResetCalled(true);
        selectedModel = null;
        if(mfModel != null)
        mfModel.resetModel();
        if(expoTimeModel != null)
        expoTimeModel.resetModel();
        if(isoModel != null)
        isoModel.resetModel();
        if(evModel != null)
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
