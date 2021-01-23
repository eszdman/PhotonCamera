package com.particlesdevs.photoncamera.pro;

import android.os.Build;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;
import com.particlesdevs.photoncamera.settings.SettingsManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.particlesdevs.photoncamera.settings.PreferenceKeys.Key.ALL_DEVICES_NAMES_KEY;

public class SupportedDevice {
    public static final String THIS_DEVICE = Build.BRAND.toLowerCase() + ":" + Build.DEVICE.toLowerCase();
    private static final String TAG = "SupportedDevice";
    private final SettingsManager mSettingsManager;
    private Set<String> mSupportedDevicesSet = new LinkedHashSet<>();
    private boolean loaded = false;
    private int checkedCount = 0;

    public SupportedDevice(SettingsManager manager) {
        mSettingsManager = manager;
    }

    public void loadCheck() {
        new Thread(() -> {
            try {
                if (checkedCount < 1) {
                    loadSupportedDevicesList();
                    isSupported();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (!loaded && mSettingsManager.isSet(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, ALL_DEVICES_NAMES_KEY))
                mSupportedDevicesSet = mSettingsManager.getStringSet(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, ALL_DEVICES_NAMES_KEY, null);
        }).start();
    }

    private void isSupported() {
        Log.d(TAG, "isSupported(): checkedCount = " + checkedCount);
        checkedCount++;
        if (mSupportedDevicesSet == null) {
            return;
        }
        if (mSupportedDevicesSet.contains(THIS_DEVICE)) {
            PhotonCamera.showToastFast(R.string.device_support);
        } else {
            PhotonCamera.showToastFast(R.string.device_unsupport);
        }
    }

    public boolean isSupportedDevice() {
        if (mSupportedDevicesSet == null) {
            return false;
        }
        return mSupportedDevicesSet.contains(THIS_DEVICE);
    }

    private void loadSupportedDevicesList() throws IOException {
        URL supportedList = new URL("https://raw.githubusercontent.com/eszdman/PhotonCamera/dev/app/SupportedList.txt");
        HttpURLConnection conn = (HttpURLConnection) supportedList.openConnection();
        conn.setConnectTimeout(200); // timing out in a 200 ms
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String str;
        while ((str = in.readLine()) != null) {
            mSupportedDevicesSet.add(str);
            Log.d(TAG, "Loaded:" + str);
        }
        loaded = true;
        in.close();
        mSettingsManager.set(PreferenceKeys.Key.DEVICES_PREFERENCE_FILE_NAME.mValue, ALL_DEVICES_NAMES_KEY, mSupportedDevicesSet);
    }
}
