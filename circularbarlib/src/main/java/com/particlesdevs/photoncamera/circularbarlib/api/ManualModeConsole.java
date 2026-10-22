package com.particlesdevs.photoncamera.circularbarlib.api;

import android.app.Activity;
import android.hardware.camera2.CameraCharacteristics;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;

import java.util.Observer;

public interface ManualModeConsole {

    void init(Activity activity, CameraCharacteristics cameraCharacteristics);

    void onResume();

    void onPause();

    void onDestroy();

    void addParamObserver(Observer observer);

    ManualParamModel getManualParamModel();

    void removeParamObservers();

    void setPanelVisibility(boolean visible);

    void resetAllValues();

    boolean isManualMode();

    boolean isPanelVisible();

    void retractAllKnobs();

    boolean isFocusParameterSelected();

    boolean isManualFocusModeActive();

    void setPreserveManualWb(boolean preserve);

    void setManualWbValue(double kelvinValue);

    /**
     * Live values shown next to the "A" label while the matching control is in
     * auto. Pass {@code null} to clear a value (the label then shows plain "A").
     */
    void setAutoValues(@Nullable String focus, @Nullable String exposure,
                       @Nullable String iso, @Nullable String wb);
}
