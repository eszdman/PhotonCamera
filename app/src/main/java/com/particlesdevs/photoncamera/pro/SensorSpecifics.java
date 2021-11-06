package com.particlesdevs.photoncamera.pro;

import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.util.HttpLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static com.particlesdevs.photoncamera.util.FileManager.sPHOTON_TUNING_DIR;

public class SensorSpecifics {
    public ArrayList<SpecificSettingSensor> specificSettingSensorList;
    public SpecificSettingSensor selectedSensorSpecifics = null;

    public SensorSpecifics(SettingsManager mSettingsManager) {
        boolean isSavedToPref = mSettingsManager.isSet(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_val");
        boolean exists = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_exists", true);
        if (exists) {
            String json;
            File localFile = new File(sPHOTON_TUNING_DIR, "SensorSpecifics.json");
            if (localFile.exists())
                json = loadLocal(localFile);
            else
                json = loadNetwork(/*device*/Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase());

            if (json == null && isSavedToPref) {
                json = mSettingsManager.getString(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_val", "");
            }
            specificSettingSensorList = SpecificSettingSensor.deserializeList(json);

            Log.d("TAG", "SensorSpecificsJSON: "+json);
            Log.d("TAG", "SensorSpecificsList: "+specificSettingSensorList);

            mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_exists", specificSettingSensorList != null);
            if (specificSettingSensorList != null) {
                mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_val", json);
            }
        }
    }

    @Nullable
    private String loadLocal(File specifics) {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(specifics))) {
            return bufferedReader.lines().collect(Collectors.joining("\n"));
        } catch (IOException | UncheckedIOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private String loadNetwork(String device) {
        try (BufferedReader bufferedReader = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/specific/sensors/" + device + ".json")) {
            return bufferedReader.lines().collect(Collectors.joining("\n"));
        } catch (IOException | UncheckedIOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void selectSpecifics(int id) {
        if (specificSettingSensorList != null) {
            for (SpecificSettingSensor specifics : specificSettingSensorList) {
                if (specifics != null && specifics.id == id) {
                    selectedSensorSpecifics = specifics;
                    break;
                }
            }
        }
    }

}
