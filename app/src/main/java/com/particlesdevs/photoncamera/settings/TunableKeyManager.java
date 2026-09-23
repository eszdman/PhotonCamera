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

    /**
     * Pseudo sensor id for the single global video-only tunable list.
     * Stored under {@code pref_sensorconfig_video_tunablekeys} so it shares
     * the same JSON format as per-sensor lists but is isolated from them.
     * This is the SDR list; see {@link #VIDEO_HDR_TUNABLE_ID} for HDR.
     */
    public static final String VIDEO_TUNABLE_ID = "video";

    /**
     * Pseudo sensor id for the global HDR-only tunable list, applied instead
     * of {@link #VIDEO_TUNABLE_ID} whenever HDR video is actually active.
     */
    public static final String VIDEO_HDR_TUNABLE_ID = "video_hdr";

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

    /** Load the video tunable list for a scope (legacy global list as fallback). */
    public static List<VendorTagUtils.TunableKey> loadVideoKeys(Context context, boolean selfie,
            String resolution) {
        return loadScopedVideoKeys(context, false, selfie, resolution);
    }

    /** Save the video tunable list for a scope. */
    public static void saveVideoKeys(Context context, boolean selfie, String resolution,
            List<VendorTagUtils.TunableKey> keys) {
        saveKeys(context, VideoResolutionScope.tunableId(false, selfie, resolution), keys);
    }

    /** Load the HDR video tunable list for a scope (legacy global list as fallback). */
    public static List<VendorTagUtils.TunableKey> loadVideoHdrKeys(Context context, boolean selfie,
            String resolution) {
        return loadScopedVideoKeys(context, true, selfie, resolution);
    }

    /** Save the HDR video tunable list for a scope. */
    public static void saveVideoHdrKeys(Context context, boolean selfie, String resolution,
            List<VendorTagUtils.TunableKey> keys) {
        saveKeys(context, VideoResolutionScope.tunableId(true, selfie, resolution), keys);
    }

    /**
     * Loads the scoped list, falling back to the legacy global list until the
     * scope has been saved once (even if saved empty), so existing values keep
     * applying after the per-resolution split.
     */
    private static List<VendorTagUtils.TunableKey> loadScopedVideoKeys(Context context,
            boolean hdr, boolean selfie, String resolution) {
        String scopedId = VideoResolutionScope.tunableId(hdr, selfie, resolution);
        if (isStored(context, scopedId)) {
            return loadKeys(context, scopedId);
        }
        return loadKeys(context, hdr ? VIDEO_HDR_TUNABLE_ID : VIDEO_TUNABLE_ID);
    }

    /** True when a tunable list was saved at least once for this id. */
    private static boolean isStored(Context context, String sensorId) {
        if (context == null || sensorId == null) return false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.contains(prefKey(sensorId));
    }

    /**
     * Seeds a scope's list from the legacy global list the first time it is
     * shown or used, so the settings UI and the capture path agree on the keys
     * that apply until the scope is edited.
     */
    public static void seedVideoScope(Context context, boolean hdr, boolean selfie, String resolution) {
        if (context == null) return;
        String scopedId = VideoResolutionScope.tunableId(hdr, selfie, resolution);
        if (isStored(context, scopedId)) return;
        saveKeys(context, scopedId,
                loadKeys(context, hdr ? VIDEO_HDR_TUNABLE_ID : VIDEO_TUNABLE_ID));
    }

    /**
     * Apply the video tunable list of a scope to a video-mode request builder.
     * Callers must gate on video mode; the list itself is resolved from the
     * scope so photo paths are never affected. The current {@code physicalId}
     * is still passed through for per-camera {@code setPhysicalCameraKey}
     * application.
     *
     * @param hdrActive true to apply the HDR list, false for the SDR list.
     */
    public static void applyVideoTunableKeys(CaptureRequest.Builder builder, String physicalId,
            boolean hdrActive, boolean selfie, String resolution) {
        Context context = PhotonCamera.getSettingsManagerStatic() != null
                ? PhotonCamera.getSettingsManagerStatic().getContext() : null;
        if (context == null || builder == null) return;
        List<VendorTagUtils.TunableKey> keys = hdrActive
                ? loadVideoHdrKeys(context, selfie, resolution)
                : loadVideoKeys(context, selfie, resolution);
        if (keys.isEmpty()) return;
        VendorTagUtils.applyTunableKeys(builder, keys, physicalId);
        if (hdrActive) {
            saveVideoHdrKeys(context, selfie, resolution, keys);
        } else {
            saveVideoKeys(context, selfie, resolution, keys);
        }
    }

    /**
     * Session-init subset of a key list. Returns live references into the
     * passed list (not copies), so support flags set on the subset persist
     * when the caller saves the full list back.
     */
    public static List<VendorTagUtils.TunableKey> sessionSubset(
            List<VendorTagUtils.TunableKey> keys) {
        List<VendorTagUtils.TunableKey> out = new ArrayList<>();
        if (keys == null) return out;
        for (VendorTagUtils.TunableKey key : keys) {
            if (VendorTagUtils.TunableKey.isSessionInit(key)) out.add(key);
        }
        return out;
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
     * Includes a self-healing mechanism in case the key was persisted as a String
     * by a legacy backup/restore routine.
     */
    public static boolean getSystemFlag(Context context, String sensorId, String flagName, boolean defaultValue) {
        if (context == null || sensorId == null || flagName == null || flagName.isEmpty()) return defaultValue;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String key = getSystemFlagKey(sensorId, flagName);
        try {
            return prefs.getBoolean(key, defaultValue);
        } catch (ClassCastException e) {
            Object raw = prefs.getAll().get(key);
            String s = (raw == null) ? null : String.valueOf(raw).trim();
            boolean healed = "1".equals(s) || "true".equalsIgnoreCase(s);
            prefs.edit().putBoolean(key, healed).apply();
            return healed;
        }
    }
}
