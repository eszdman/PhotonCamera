package com.particlesdevs.photoncamera.circularbarlib.console;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.view.View;

import com.particlesdevs.photoncamera.circularbarlib.api.ManualModeConsole;
import com.particlesdevs.photoncamera.circularbarlib.camera.CameraProperties;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.EvModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.FocusModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.IsoModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.ManualModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.ShutterModel;
import com.particlesdevs.photoncamera.circularbarlib.control.models.WbModel;
import com.particlesdevs.photoncamera.circularbarlib.model.KnobModel;
import com.particlesdevs.photoncamera.circularbarlib.model.ManualModeModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.ViewObserver;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobItemInfo;

import java.util.Observer;

/**
 * Responsible for initialising and updating {@link KnobModel} and
 * {@link ManualModeModel}
 * <p>
 * This class also manages the attaching/detaching of {@link ManualModel}
 * subclasses to {@link KnobView}
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
    private ManualModel<?> mfModel, isoModel, expoTimeModel, evModel, wbModel, selectedModel;
    private ViewObserver viewObserver;
    private boolean preserveManualWb = false;

    private ManualModeConsoleImpl() {
        this.manualModeModel = new ManualModeModel();
        this.knobModel = new KnobModel();
    }

    /**
     * The previously selected control while the wheel is open; shown as the
     * smaller inner ruler so both stay adjustable. Selecting it again swaps
     * the two wheels; closing the wheel forgets it.
     */
    private ManualModel<?> previousModel;
    /** Bar cell of the current primary control; the old one becomes the ring. */
    private int selectedViewId = -1;

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
        double preservedWb = (this.preserveManualWb) ? manualParamModel.getCurrentWbValue() : ManualParamModel.WB_AUTO;
        manualParamModel.reset();
        mfModel = new FocusModel(context, cameraCharacteristics, cameraProperties.focusRange, manualParamModel,
                manualModeModel::setFocusText);
        evModel = new EvModel(context, cameraCharacteristics, cameraProperties.evRange, manualParamModel,
                manualModeModel::setEvText);
        ((EvModel) evModel).setEvStep(
                (cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue()));
        isoModel = new IsoModel(context, cameraCharacteristics, cameraProperties.isoRange, manualParamModel,
                manualModeModel::setIsoText);
        expoTimeModel = new ShutterModel(context, cameraCharacteristics, cameraProperties.expRange, manualParamModel,
                manualModeModel::setExposureText);
        wbModel = new WbModel(context, cameraCharacteristics, null, manualParamModel,
                manualModeModel::setWbText);

        // Restore manual White Balance temperature across camera lenses if enabled
        if (preservedWb != ManualParamModel.WB_AUTO) {
            for (KnobItemInfo item : wbModel.getKnobInfoList()) {
                if (Math.abs(item.value - preservedWb) < 0.1) {
                    wbModel.onSelectedKnobItemChanged(item);
                    manualModeModel.setWbText(item.text);
                    break;
                }
            }
        }

        knobModel.setKnobVisible(false);
        knobModel.setSecondaryManualModel(null);
        selectedModel = null;
        previousModel = null;
        selectedViewId = -1;
        manualModeModel.setCheckedTextViewId(-1);
    }

    @Override
    public void setPanelVisibility(boolean visible) {
        manualModeModel.setManualPanelVisible(visible);
        if (!visible) {
            manualParamModel.reset(this.preserveManualWb);
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
        manualModeModel.setWbTextClicked(v -> setListeners(v, wbModel));
    }

    private void setListeners(View view, ManualModel<?> model) {
        setModelToKnob(view.getId(), model);
        view.setOnLongClickListener(v -> {
            if (selectedModel == model) {
                knobModel.setKnobResetCalled(true);
                model.resetModel();
            } else if (previousModel == model) {
                // Reset the inner ruler too, then re-notify so its wheel
                // snaps back to the auto position.
                model.resetModel();
                knobModel.setSecondaryManualModel(previousModel);
            } else {
                model.resetModel();
            }
            return true;
        });
    }

    private void setAutoText() {
        if (evModel != null)
            evModel.setAutoTxt();
        if (mfModel != null)
            mfModel.setAutoTxt();
        if (expoTimeModel != null)
            expoTimeModel.setAutoTxt();
        if (isoModel != null)
            isoModel.setAutoTxt();
        if (wbModel != null) {
            if (!this.preserveManualWb || manualParamModel.getCurrentWbValue() == ManualParamModel.WB_AUTO) {
                wbModel.setAutoTxt();
            }
        }
    }

    @Override
    public void retractAllKnobs() {
        knobModel.setKnobVisible(false);
        knobModel.setSecondaryManualModel(null);
        if (!this.preserveManualWb || this.manualParamModel.getCurrentWbValue() == ManualParamModel.WB_AUTO || !(selectedModel instanceof WbModel)) {
            knobModel.setKnobResetCalled(true);
        }
        selectedModel = null;
        previousModel = null;
        selectedViewId = -1;
        // Silent resets: the panel close already implies the action, and four
        // simultaneous ticks stack into a multi-buzz.
        if (mfModel != null)
            mfModel.resetModelSilently();
        if (expoTimeModel != null)
            expoTimeModel.resetModelSilently();
        if (isoModel != null)
            isoModel.resetModelSilently();
        if (evModel != null)
            evModel.resetModelSilently();
        if (wbModel != null && (!this.preserveManualWb || manualParamModel.getCurrentWbValue() == ManualParamModel.WB_AUTO))
            wbModel.resetModel();
        manualModeModel.setCheckedTextViewId(-1);
    }

    @Override
    public boolean isFocusParameterSelected() {
        return selectedModel instanceof FocusModel;
    }

    @Override
    public boolean isManualFocusModeActive() {
        if (mfModel == null) {
            return false;
        }
        KnobItemInfo currentInfo = mfModel.getCurrentInfo();
        return currentInfo != null && currentInfo.value != ManualParamModel.FOCUS_AUTO;
    }

    @Override
    public void setPreserveManualWb(boolean preserve) {
        this.preserveManualWb = preserve;
    }

    @Override
    public void setManualWbValue(double kelvinValue) {
        if (wbModel == null) {
            manualParamModel.setCurrentWbValue(kelvinValue);
            return;
        }

        // 1. Locate the matching knob item for the measured Kelvin value
        KnobItemInfo matchedItem = null;
        for (KnobItemInfo item : wbModel.getKnobInfoList()) {
            if (Math.abs(item.value - kelvinValue) < 0.1) {
                matchedItem = item;
                break;
            }
        }

        // 2. Synchronize WbModel, manual bar text, and active KnobView wheel rotation
        if (matchedItem != null) {
            wbModel.onSelectedKnobItemChanged(matchedItem);
            manualModeModel.setWbText(matchedItem.text);
            if (selectedModel == wbModel) {
                knobModel.setManualModel(wbModel);
            } else if (previousModel == wbModel) {
                knobModel.setSecondaryManualModel(wbModel);
            }
        } else {
            manualParamModel.setCurrentWbValue(kelvinValue);
        }
    }

    private void setModelToKnob(int viewId, ManualModel<?> modelToKnob) {
        if (modelToKnob == selectedModel) {
            // Toggle off: close the wheel and forget the remembered control.
            knobModel.setManualModel(null);
            knobModel.setSecondaryManualModel(null);
            knobModel.setKnobVisible(false);
            manualModeModel.setCheckedTextViewId(-1);
            selectedModel = null;
            previousModel = null;
            selectedViewId = -1;
        } else if (modelToKnob == previousModel && previousModel != null) {
            // Selecting the remembered (inner) control swaps the two wheels.
            ManualModel<?> outgoing = selectedModel;
            selectedModel = previousModel;
            previousModel = outgoing;
            knobModel.setManualModel(selectedModel);
            knobModel.setSecondaryManualModel(previousModel);
            // The outgoing primary becomes the secondary-filled remembered cell.
            manualModeModel.setCheckedTextViewIds(viewId, selectedViewId);
            selectedViewId = viewId;
        } else {
            if (modelToKnob.getKnobInfoList().size() > 1) {
                // The outgoing control becomes the inner ruler; an older inner
                // one is dropped.
                previousModel = selectedModel;
                selectedModel = modelToKnob;
                knobModel.setManualModel(modelToKnob);
                knobModel.setSecondaryManualModel(previousModel);
                knobModel.setKnobVisible(true);
                manualModeModel.setCheckedTextViewIds(viewId,
                        previousModel != null ? selectedViewId : -1);
                selectedViewId = viewId;
            }
        }
    }
}
