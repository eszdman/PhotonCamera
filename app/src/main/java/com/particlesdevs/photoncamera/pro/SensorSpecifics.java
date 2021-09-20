package com.particlesdevs.photoncamera.pro;

import android.os.Build;
import android.util.Log;

import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.util.HttpLoader;

import java.io.BufferedReader;
import java.util.ArrayList;

public class SensorSpecifics {
    public SpecificSettingSensor[] specificSettingSensor;
    public SpecificSettingSensor selectedSensorSpecifics = null;
    public SensorSpecifics(SettingsManager mSettingsManager){
        boolean loaded = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_loaded",false);
        boolean exists = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "sensor_specific_exists",true);

        if(exists) {
            int count;
            String device = Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase();
            String fullSpec = "";
            ArrayList<String> inputStr = new ArrayList<String>();
            try {
                exists = false;
                BufferedReader indevice = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/specific/sensors/" + device + ".txt");
                String str;
                count = 0;
                while ((str = indevice.readLine()) != null) {
                    Log.d("SensorSpecifics","read:"+str);
                    if (str.contains("sensor")) count++;
                    inputStr.add(str + "\n");
                }
                Log.d("SensorSpecifics","SensorCount:"+count);
                exists = true;
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
                            case "CaptureSharpeningS":{
                                current.captureSharpeningS = Float.parseFloat(valsIn[1]);
                                break;
                            }
                            case "captureSharpeningIntense":{
                                current.captureSharpeningIntense = Float.parseFloat(valsIn[1]);
                                break;
                            }
                        }
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
