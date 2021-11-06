package com.particlesdevs.photoncamera.pro;

import android.os.Build;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.util.HttpLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;

public class Specific {
    private static final String TAG = "Specific";
    public SpecificSetting specificSetting;
    public float[] blackLevel;
    private final SettingsManager mSettingsManager;

    public Specific(SettingsManager mSettingsManager) {
        this.mSettingsManager = mSettingsManager;
    }

    public void loadSpecific() {
        boolean isSavedToPref = mSettingsManager.isSet(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_val");
        boolean exists = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_exists", true);
        if (exists) {
            if (!isSavedToPref) {
                boolean specificExists = isSupportedDevice();
                mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_exists", specificExists);
                if (specificExists) {
                    String json = loadNetwork(/*device*/Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase());
                    specificSetting = SpecificSetting.deserialize(json);
                    if (specificSetting != null) {
                        mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_val", json);
                    }
                }
            } else {
                String json = mSettingsManager.getString(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_val", "");
                specificSetting = SpecificSetting.deserialize(json);
            }
        }
        if (specificSetting == null) {
            specificSetting = new SpecificSetting();
        }
    }

    @Nullable
    private String loadNetwork(String device) {
        try (BufferedReader bufferedReader = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/specific/" + device + "_specificsettings.json")) {
            return bufferedReader.lines().collect(Collectors.joining("\n"));
        } catch (IOException | UncheckedIOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isSupportedDevice() {
        boolean specificExists = false;
        try (BufferedReader indevice = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/SupportedList.txt")) {
            String str;
            while ((str = indevice.readLine()) != null) {
                if (str.contains(SupportedDevice.THIS_DEVICE)) {
                    specificExists = true;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return specificExists;
    }
}
