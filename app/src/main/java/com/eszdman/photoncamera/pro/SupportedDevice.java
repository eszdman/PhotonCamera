package com.eszdman.photoncamera.pro;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.util.Pair;

import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.settings.SettingsManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.eszdman.photoncamera.settings.PreferenceKeys.Preference.ALL_CAMERA_IDS_KEY;
import static com.eszdman.photoncamera.settings.PreferenceKeys.Preference.ALL_DEVICES_NAMES_KEY;
import static com.eszdman.photoncamera.settings.PreferenceKeys.Preference.CAMERA_COUNT_KEY;

public class SupportedDevice {
    private final SettingsManager mSettingsManager;
    private Set<String> mSupportedDevices = new LinkedHashSet<>();
    public SupportedDevice(SettingsManager manager){
        mSettingsManager = manager;
        new Thread(() -> {
            try {
                if (!mSettingsManager.isSet(PreferenceKeys.Preference.DEVICES_PREFERENCE_FILE_NAME.mValue, ALL_DEVICES_NAMES_KEY)) {
                    LoadSupported();
                } else {
                    mSupportedDevices = mSettingsManager.getStringSet(PreferenceKeys.Preference.DEVICES_PREFERENCE_FILE_NAME.mValue, ALL_DEVICES_NAMES_KEY, null);
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }).start();
    }
    public boolean isSupported(){
        boolean supported = false;
        String deviceC = Build.BRAND.toLowerCase() + ":" + Build.DEVICE.toLowerCase();
        for(String device : mSupportedDevices){
            if(deviceC.equals(device)) supported = true;
        }
        return supported;
    }
    private void LoadSupported() throws IOException {
        URL supportedList = new URL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/SupportedList.txt");
        HttpURLConnection conn=(HttpURLConnection) supportedList.openConnection();
        conn.setConnectTimeout(200); // timing out in a 200 ms
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String str;
        while ((str = in.readLine()) != null) {
            mSupportedDevices.add(str);
            Log.d("SupportedDevice","Loaded:"+str);
        }
        in.close();
        mSettingsManager.set(PreferenceKeys.Preference.DEVICES_PREFERENCE_FILE_NAME.mValue, ALL_DEVICES_NAMES_KEY, mSupportedDevices);
    }
}
