package com.particlesdevs.photoncamera.pro;

import android.os.Build;
import android.util.Log;

import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.util.HttpLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import static com.particlesdevs.photoncamera.settings.PreferenceKeys.Key.ALL_DEVICES_NAMES_KEY;
import static com.particlesdevs.photoncamera.util.FileManager.sPHOTON_TUNING_DIR;

public class Specific {
    private static final String TAG = "Specific";

    public boolean isLoaded = false;
    public SpecificSetting specificSetting = new SpecificSetting();
    public float[] blackLevel;
    private final SettingsManager mSettingsManager;

    public Specific(SettingsManager mSettingsManager) {
        this.mSettingsManager = mSettingsManager;
    }
    ArrayList<String> loadNetwork(String device) throws IOException {
        ArrayList<String> inputStr = new ArrayList<String>();
        BufferedReader indevice = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/specific/" + device + "_specificsettings.txt", 100);
        String str;
        while ((str = indevice.readLine()) != null) {
            Log.d("Specific", "read:" + str);
            inputStr.add(str + "\n");
        }
        return inputStr;
    }
    ArrayList<String> loadLocal(File specifics) throws IOException {
        ArrayList<String> inputStr = new ArrayList<String>();
        String str;
        BufferedReader indevice = new BufferedReader(new FileReader(specifics));
        while ((str = indevice.readLine()) != null) {
            Log.d("Specific", "read:" + str);
            inputStr.add(str + "\n");
        }
        return inputStr;
    }
    public void loadSpecific(){
        isLoaded = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_loaded",false);
        boolean exists = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_exists",true);
        Log.d("Specific", "loaded: "+isLoaded+ " exists: " + exists);
        if(exists) {
            if (!isLoaded) {
                try {
                    //Set<String> mSupportedDevicesSet = mSettingsManager.getStringSet(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, ALL_DEVICES_NAMES_KEY, null);
                    //BufferedReader indevice = HttpLoader.readURL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/SupportedList.txt");
                    //boolean specificExists = mSupportedDevicesSet.contains(SupportedDevice.THIS_DEVICE);
                    //Log.d("Specific", "specificExists: "+specificExists);
                    //mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_exists", specificExists);
                    //if (!specificExists) return;
                    ArrayList<String> inputStr;

                    String device = Build.BRAND.toLowerCase() + "/" + Build.DEVICE.toLowerCase();
                    File deviceSpecific = new File(sPHOTON_TUNING_DIR, "DeviceSpecific.txt");
                    if(deviceSpecific.exists())
                        inputStr = loadLocal(deviceSpecific);
                    else
                        inputStr = loadNetwork(device);
                    for (String str : inputStr) {
                        String[] caseS = str.replace(" ","").replace("\n","").split("=");
                        switch (caseS[0]) {
                            case "isDualSessionSupported": {
                                specificSetting.isDualSessionSupported = Boolean.parseBoolean(caseS[1]);
                                break;
                            }
                            case "blackLevel": {
                                String[] bl = caseS[1].split(",");
                                blackLevel = new float[]{Float.parseFloat(bl[0]), Float.parseFloat(bl[1]), Float.parseFloat(bl[2]), Float.parseFloat(bl[3])};
                                break;
                            }
                            case "rawColorCorrection": {
                                specificSetting.isRawColorCorrection = Boolean.parseBoolean(caseS[1]);
                                break;
                            }
                            case "cameraIDS": {
                                Log.d("Specific", "Camera IDs Loaded: "+caseS[1]);
                                String[] ids = caseS[1].replace("{", "").replace("}", "").split(",");
                                specificSetting.cameraIDS = new int[ids.length];
                                for(int i =0; i<specificSetting.cameraIDS.length;i++){
                                    specificSetting.cameraIDS[i] = Integer.parseInt(ids[i]);
                                }
                                break;
                            }

                        }
                    }
                    mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_loaded", true);
                } catch (Exception e) {
                    Log.e(TAG,e.toString());
                }
            } else {
                specificSetting.isDualSessionSupported = mSettingsManager.getBoolean(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_is_dual_session", specificSetting.isDualSessionSupported);
            }
            saveSpecific();
        }
        isLoaded = true;
    }
    private void saveSpecific(){
        mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, "specific_is_dual_session", specificSetting.isDualSessionSupported);
    }
}
