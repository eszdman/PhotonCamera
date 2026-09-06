package com.particlesdevs.photoncamera.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CaptureRequest;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.particlesdevs.photoncamera.api.VendorTagUtils;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.Log;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores and applies user-defined {@link VendorTagUtils.TunableKey}s per physical sensor.
 * Keys are serialized as JSON under {@code pref_sensorconfig_<sensorId>_tunablekeys}.
 */
public class TunableKeyManager {
    private static final String TAG = "TunableKeyManager";

    private TunableKeyManager() {}

    private static String prefKey(String sensorId) {
        return "pref_sensorconfig_" + sensorId + "_tunablekeys";
    }

    public static List<VendorTagUtils.TunableKey> loadKeys(Context context, String sensorId) {
        if (context == null || sensorId == null) return new ArrayList<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = prefs.getString(prefKey(sensorId), null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type listType = new TypeToken<List<VendorTagUtils.TunableKey>>() {}.getType();
            List<VendorTagUtils.TunableKey> keys = new Gson().fromJson(json, listType);
            return keys != null ? keys : new ArrayList<>();
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse tunable keys for sensor " + sensorId, e);
            return new ArrayList<>();
        }
    }

    public static void saveKeys(Context context, String sensorId, List<VendorTagUtils.TunableKey> keys) {
        if (context == null || sensorId == null || keys == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(prefKey(sensorId), new Gson().toJson(keys)).apply();
    }

    /**
     * Load the tunable keys for the given physical sensor, apply them to the builder
     * (which tests support and updates flags) and persist the updated status back so
     * the settings UI can show green/red feedback.
     */
    public static void applyTunableKeys(CaptureRequest.Builder builder, String physicalId) {
        Context context = PhotonCamera.getSettingsManagerStatic() != null
                ? PhotonCamera.getSettingsManagerStatic().getContext() : null;
        if (context == null || builder == null || physicalId == null || physicalId.isEmpty()) return;
        List<VendorTagUtils.TunableKey> keys = loadKeys(context, physicalId);
        if (keys.isEmpty()) return;
        VendorTagUtils.applyTunableKeys(builder, keys, physicalId);
        saveKeys(context, physicalId, keys);
    }

    /**
     * Universal check whether a tunable key is supported or holds an expected value
     * in SharedPreferences for the given sensor.
     *
     * @param context       the application or preference context
     * @param sensorId      physical camera ID
     * @param keyName       name of the vendor tag / key (e.g. "android.lens.opticalStabilizationMode")
     * @param expectedValue expected string value (e.g. "1"), or null if only checking support
     * @return true if the key exists and is either marked supported or matches expectedValue
     */
    public static boolean hasKey(Context context, String sensorId, String keyName, String expectedValue) {
        if (context == null || sensorId == null || keyName == null) return false;
        List<VendorTagUtils.TunableKey> keys = loadKeys(context, sensorId);
        for (VendorTagUtils.TunableKey key : keys) {
            if (key != null && keyName.equalsIgnoreCase(key.name)) {
                if (key.supported) {
                    return true;
                }
                if (expectedValue != null && expectedValue.equalsIgnoreCase(key.value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getSystemFlagKey(String sensorId, String flagName) {
        return "pref_sysflag_" + sensorId + "_" + flagName;
    }

    /**
     * Universal method to save a hidden system boolean flag for a given sensor.
     * Isolated from user tunable keys.
     */
    public static void setSystemFlag(Context context, String sensorId, String flagName, boolean value) {
        if (context == null || sensorId == null || flagName == null || flagName.isEmpty()) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(getSystemFlagKey(sensorId, flagName), value).apply();
    }

    /**
     * Universal method to read a hidden system boolean flag for a given sensor.
     */
    public static boolean getSystemFlag(Context context, String sensorId, String flagName, boolean defaultValue) {
        if (context == null || sensorId == null || flagName == null || flagName.isEmpty()) return defaultValue;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(getSystemFlagKey(sensorId, flagName), defaultValue);
    }
}
