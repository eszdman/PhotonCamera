package com.particlesdevs.photoncamera.pro;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.util.HttpLoader;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class SensorSpecifics {
    public SpecificSettingSensor[] specificSettingSensor;
    public SpecificSettingSensor selectedSensorSpecifics = null;
    public SensorSpecifics(SettingsManager mSettingsManager){
        boolean loaded = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_loaded",false);
        boolean exists = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_exists",true);
        if(exists) {
            int count = 0;
            String device = Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase();
            String fullSpec = "";
            ArrayList<String> inputStr = new ArrayList<String>();
            try {
                try {

                    exists = false;
                    BufferedReader indevice = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/specific/sensors/" + device + ".txt");
                    String str;
                    count = 0;
                    while ((str = indevice.readLine()) != null) {
                        Log.d("SensorSpecifics", "read:" + str);
                        if (str.contains("sensor")) count++;
                        inputStr.add(str + "\n");
                    }
                    Log.d("SensorSpecifics", "SensorCount:" + count);
                    exists = true;
                } catch (Exception e){
                    if(loaded){
                        inputStr = mSettingsManager.getArrayList(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_loaded", new HashSet<>());
                        for (String str2 : inputStr) {
                            if (str2.contains("sensor")) count++;
                        }
                        exists = true;
                    }
                }
                specificSettingSensor = new SpecificSettingSensor[count];
                count = 0;
                for (String str2 : inputStr) {
                    if (str2.contains("sensor")) {
                        String[] vals = str2.split("_");
                        vals[1] = vals[1].replace("\n", "");
                        specificSettingSensor[count] = new SpecificSettingSensor();
                        specificSettingSensor[count].id = Integer.parseInt(vals[1]);
                        count++;
                    } else {
                        String[] valsIn = str2.split("=");
                        if(valsIn.length <= 1) continue;
                        valsIn[0] = valsIn[0].replace(" ","");
                        valsIn[1] = valsIn[1].replace(" ","");
                        String[] istr = valsIn[1].replace("{", "").replace("}", "").split(",");
                        SpecificSettingSensor current = specificSettingSensor[count - 1];
                        switch (valsIn[0]) {
                            case "NoiseModelA": {
                                for (int i = 0; i < 4; i++) {
                                    current.NoiseModelerArr[0][i] = Double.parseDouble(istr[i]);
                                }
                                break;
                            }
                            case "NoiseModelB": {
                                for (int i = 0; i < 4; i++) {
                                    current.NoiseModelerArr[1][i] = Double.parseDouble(istr[i]);
                                }
                                break;
                            }
                            case "NoiseModelC": {
                                for (int i = 0; i < 4; i++) {
                                    current.NoiseModelerArr[2][i] = Double.parseDouble(istr[i]);
                                }
                                break;
                            }
                            case "NoiseModelD": {
                                for (int i = 0; i < 4; i++) {
                                    current.NoiseModelerArr[3][i] = Double.parseDouble(istr[i]);
                                }
                                current.ModelerExists = true;
                                break;
                            }
                            case "captureSharpeningS": {
                                current.captureSharpeningS = (float)Double.parseDouble(valsIn[1]);
                                break;
                            }
                            case "captureSharpeningIntense": {
                                current.captureSharpeningIntense = (float)Double.parseDouble(valsIn[1]);
                                break;
                            }
                            case "aberrationCorrection": {
                                for (int i = 0; i < 8; i++) {
                                    current.aberrationCorrection[i] = (float)Double.parseDouble(istr[i]);
                                }
                                break;
                            }

                        }
                        mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_val",inputStr);
                        loaded = true;

                    }
                }

            } catch (Exception ignored) {}

            mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_loaded", loaded);
            mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_exists", exists);
        }
    }
    public void selectSpecifics(int id){
        if(specificSettingSensor != null) {
            for (SpecificSettingSensor specifics : specificSettingSensor) {
                if (specifics != null && specifics.id == id) selectedSensorSpecifics = specifics;
            }
        }
    }

}
