package com.particlesdevs.photoncamera.pro;

import android.os.Build;
import android.util.Log;

import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.util.HttpLoader;

import java.io.BufferedReader;
import java.io.IOException;

import static com.particlesdevs.photoncamera.settings.PreferenceKeys.Key.ALL_DEVICES_NAMES_KEY;

public class Specific {
    private static final String TAG = "Specific";
    public boolean isDualSessionSupported = true;
    private final SettingsManager mSettingsManager;

    public Specific(SettingsManager mSettingsManager) {
        this.mSettingsManager = mSettingsManager;
    }

    public void loadSpecific(){
        boolean loaded = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_loaded",false);
        boolean exists = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_exists",true);
        if(exists) {
            if (!loaded) {
                try {
                    BufferedReader indevice = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/SupportedList.txt");
                    String str;
                    boolean specificExists = false;
                    while ((str = indevice.readLine()) != null) {
                        if (str.contains(SupportedDevice.THIS_DEVICE)) specificExists = true;
                    }
                    mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_exists", specificExists);
                    if (!specificExists) return;
                    String device = Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase();
                    BufferedReader in = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/specific/" + device + "_specificsettings.txt");
                    while ((str = in.readLine()) != null) {
                        String[] caseS = str.split("=");
                        switch (caseS[0]) {
                            case "isDualSessionSupported":
                                isDualSessionSupported = Boolean.parseBoolean(caseS[1]);
                                break;
                        }
                    }
                    mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_loaded", true);
                } catch (IOException ignored) {
                }
            } else {
                isDualSessionSupported = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_is_dual_session", isDualSessionSupported);
            }
            saveSpecific();
        }
    }
    public void saveSpecific(){
        mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_is_dual_session", isDualSessionSupported);
    }
}
