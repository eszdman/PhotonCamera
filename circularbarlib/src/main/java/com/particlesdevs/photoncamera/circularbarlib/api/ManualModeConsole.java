package com.particlesdevs.photoncamera.circularbarlib.api;

import android.app.Activity;
import android.hardware.camera2.CameraCharacteristics;

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
}
