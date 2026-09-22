package com.particlesdevs.photoncamera.settings;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import com.particlesdevs.photoncamera.util.Log;

import androidx.annotation.StringRes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.IszLensUtil;
import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Created by Vibhor 06/09/2020
 */
public class PreferenceKeys {
    public static final String SCOPE_GLOBAL = SettingsManager.SCOPE_GLOBAL;
    private static final String TAG = "PreferenceKeys";
    private static final Set<String> COMMON_KEYS = new HashSet<>();
    private static final String PER_LENS_KEY_PREFIX = "settings_for_camera_";
    /**
     * Per-lens photo frame-rate keys: prefix + camera id, with an optional
     * Quad Bayer suffix. Kept out of the per-lens JSON snapshots (they are
     * already lens-scoped) so they survive backup/restore as plain main-prefs
     * keys.
     */
    private static final String FPS_LENS_KEY_PREFIX = "pref_fps_preview_key_lens_";
    private static final String FPS_LENS_QUAD_SUFFIX = "_quad";
    /** Per-lens video/RAW-video frame rate: prefix + camera id. */
    private static final String VIDEO_FPS_LENS_KEY_PREFIX = "pref_video_fps_key_lens_";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PreferenceKeys preferenceKeys;

    static {
        COMMON_KEYS.add(Key.CAMERA_ID.mValue);
        COMMON_KEYS.add(Key.KEY_SAVE_PER_LENS_SETTINGS.mValue);
        COMMON_KEYS.add(Key.KEY_PRESERVE_MANUAL_WB.mValue);
        COMMON_KEYS.add(Key.KEY_SHOW_AF_DATA.mValue);
        COMMON_KEYS.add(Key.KEY_THEME_ACCENT.mValue);
        COMMON_KEYS.add(Key.KEY_THEME.mValue);
        COMMON_KEYS.add(Key.KEY_SHOW_GRID.mValue);
        COMMON_KEYS.add(Key.KEY_LENS_BAR_POSITION.mValue);
        COMMON_KEYS.add(Key.KEY_LENS_MM_EQUIVALENT.mValue);
        COMMON_KEYS.add(Key.KEY_SHOW_WATERMARK.mValue);
        COMMON_KEYS.add(Key.KEY_SHOW_ROUND_EDGE.mValue);
        COMMON_KEYS.add(Key.KEY_CAMERA_SOUNDS.mValue);
        COMMON_KEYS.add(Key.KEY_HAPTICS.mValue);
        COMMON_KEYS.add(Key.KEY_VIEWFINDER_BACKGROUND.mValue);
        COMMON_KEYS.add(Key.KEY_AF_MODE.mValue);
        COMMON_KEYS.add(Key.KEY_FOCUS_PEAK.mValue);
        COMMON_KEYS.add(Key.KEY_AE_MODE.mValue);
        COMMON_KEYS.add(Key.CAMERA_MODE.mValue);
        // The legacy photo frame rate is only the seed/fallback for the
        // per-lens keys, so it must not travel through lens snapshots.
        COMMON_KEYS.add(Key.KEY_FPS_PREVIEW.mValue);
        COMMON_KEYS.add(Key.KEY_SAVE_RAW.mValue);
        COMMON_KEYS.add(Key.KEY_SAVE_HEIC.mValue);
        COMMON_KEYS.add(Key.KEY_ZOOM_LOCK.mValue);
        COMMON_KEYS.add(Key.KEY_AUTO_ZOOM_SWITCH.mValue);
        // Video settings are global: per-lens copies would resurrect another
        // lens's resolution/bitrate/codec when switching lenses.
        COMMON_KEYS.add(Key.KEY_VIDEO_RESOLUTION.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_RESOLUTION_SELFIE.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_BITRATE.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_HEVC.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_HDR.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_HDR_TRANSFER.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_COLOR_RANGE.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_OIS.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_HDR_SESSION_TYPE.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_SDR_SESSION_TYPE.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_USE_LOGICAL_ID.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_LOGICAL_ID.mValue);
        COMMON_KEYS.add(Key.KEY_VIDEO_LOGICAL_LENSES.mValue);
        // Legacy global video frame rate: now only the seed/fallback for the
        // per-lens video keys, so it must not travel through lens snapshots.
        COMMON_KEYS.add(Key.KEY_VIDEO_FPS.mValue);
        // Audio settings are global like video.
        COMMON_KEYS.add(Key.KEY_AUDIO_BITRATE.mValue);
        COMMON_KEYS.add(Key.KEY_AUDIO_STEREO.mValue);
        COMMON_KEYS.add(Key.KEY_AUDIO_SOURCE.mValue);
        // Hidden camera modes and their order are global like the selected mode.
        COMMON_KEYS.add(Key.KEY_HIDE_MODES.mValue);
        COMMON_KEYS.add(Key.KEY_MODE_ORDER.mValue);
        // Geotagging is a device-wide capture setting.
        COMMON_KEYS.add(Key.KEY_SAVE_LOCATION.mValue);
    }

    private final SettingsManager settingsManager;

    private PreferenceKeys(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public static void initialise(SettingsManager settingsManager) {
        preferenceKeys = new PreferenceKeys(settingsManager);
    }
    public static void setDefaults(Context context) {
        SettingsManager settingsManager = preferenceKeys.settingsManager;
        Resources resources = context.getResources();

        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_HDRX, resources.getBoolean(R.bool.pref_hdrx_mode_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_SAVE_HEIC, resources.getBoolean(R.bool.pref_save_heic_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_EIS_PHOTO, resources.getBoolean(R.bool.pref_eis_photo_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_ZOOM_LOCK, resources.getBoolean(R.bool.pref_zoom_lock_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_AUTO_ZOOM_SWITCH, resources.getBoolean(R.bool.pref_auto_zoom_switch_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_QUAD_BAYER, resources.getBoolean(R.bool.pref_quad_bayer_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_REMOSAIC, resources.getBoolean(R.bool.pref_remosaic_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_ULTRAHDR, resources.getBoolean(R.bool.pref_ultrahdr_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_FPS_PREVIEW, 0);
        // The video/raw-video rate starts out inheriting the shared value; it
        // diverges only once the user changes it in a video mode.
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_FPS, getFpsMode());
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_AE_MODE, resources.getString(R.string.pref_ae_mode_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.CAMERA_MODE, resources.getString(R.string.pref_camera_mode_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_COUNTDOWN_TIMER, 0);
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_HAPTICS, resources.getBoolean(R.bool.pref_haptics_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_BRACKETING_MODE, 0); // Default to disable bracketing
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_AE_METERING_STD, -1); // Default to Off
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_RESOLUTION, resources.getString(R.string.pref_video_resolution_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_RESOLUTION_SELFIE, resources.getString(R.string.pref_video_resolution_selfie_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_BITRATE, resources.getString(R.string.video_bitrate_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_HEVC, false);
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_HDR, false);
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_HDR_TRANSFER, resources.getString(R.string.video_hdr_transfer_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_COLOR_RANGE, resources.getString(R.string.video_color_range_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_OIS, resources.getBoolean(R.bool.pref_video_ois_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_HDR_SESSION_TYPE, resources.getString(R.string.video_session_type_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_SDR_SESSION_TYPE, resources.getString(R.string.video_session_type_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_USE_LOGICAL_ID, false);
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_LOGICAL_ID, resources.getString(R.string.video_logical_id_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_VIDEO_LOGICAL_LENSES, resources.getString(R.string.video_logical_lenses_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_AUDIO_BITRATE, resources.getString(R.string.audio_bitrate_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_AUDIO_STEREO, true);
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_AUDIO_SOURCE, resources.getString(R.string.audio_source_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_LENS_BAR_POSITION, resources.getString(R.string.pref_lens_bar_position_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_LENS_MM_EQUIVALENT, resources.getBoolean(R.bool.pref_lens_mm_equivalent_default));
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_RAWVIDEO_DOWNSCALE_4X, false);
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_RAWVIDEO_WRITE_ZIP, true);
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_RAWVIDEO_CROP_169, true);
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_SAVE_LOCATION, resources.getBoolean(R.bool.pref_save_location_default));
        // Migrate the legacy "Write ZIP" switch into the container selector
        // (true -> zip, false -> dng) the first time the new key is seen.
        if (!settingsManager.getDefaultPreferences().contains(Key.KEY_RAWVIDEO_CONTAINER.mValue)) {
            settingsManager.set(SCOPE_GLOBAL, Key.KEY_RAWVIDEO_CONTAINER,
                    settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_RAWVIDEO_WRITE_ZIP)
                            ? CONTAINER_ZIP : CONTAINER_DNG);
        }
        settingsManager.setInitial(SCOPE_GLOBAL, Key.KEY_RAWVIDEO_CONTAINER, CONTAINER_ZIP);

        settingsManager.setDefaults(Key.CAMERA_ID, resources.getString(R.string.camera_id_default), new String[]{"0", "1"});
        settingsManager.setDefaults(Key.TONEMAP, resources.getString(R.string.tonemap_default), new String[]{resources.getString(R.string.tonemap_default)});
        settingsManager.setDefaults(Key.GAMMA, resources.getString(R.string.gamma_default), new String[]{resources.getString(R.string.gamma_default)});
        settingsManager.setDefaults(Key.KEY_SHOW_AF_DATA, "0", new String[]{"0", "1", "2", "3", "4"});


        settingsManager.addListener((settingsManager1, key) -> {
            // Guard against null key (can happen during preference restore)
            if (key == null) {
                return;
            }
            
            if (isPerLensSettingsOn()) {
                if (key.equals(Key.CAMERA_ID.mValue)) {
                    loadSettingsForCamera(getCameraID());
                }
                if (!COMMON_KEYS.contains(key)) {
                    saveJsonForCamera(getCameraID());
                }
            }
            PhotonCamera.getSettings().loadCache();
            if (Key.KEY_HAPTICS.mValue.equals(key) && !isHapticsOn()
                    && PhotonCamera.getVibration() != null) {
                PhotonCamera.getVibration().cancel();
            }
            //Log.d(TAG, key + " : changed!");
        });
    }

    /**
     * Keys that are already lens/sensor scoped and must not be copied into the
     * per-lens JSON snapshots (loading one lens would otherwise resurrect
     * another lens's values).
     */
    private static boolean isLensScopedKey(String key) {
        return key != null && (key.startsWith("pref_tunable_")
                || key.startsWith("pref_sensorconfig_")
                || key.startsWith(FPS_LENS_KEY_PREFIX)
                || key.startsWith(VIDEO_FPS_LENS_KEY_PREFIX));
    }

    public static void addIds(String[] ids){
        if(ids != null) {
            SettingsManager settingsManager = preferenceKeys.settingsManager;
            Log.d(TAG, "Added IDS:" + Arrays.toString(ids));
            settingsManager.setDefaults(Key.CAMERA_ID, ids[0], ids);
            Map<String, ?> map = settingsManager.getDefaultPreferences().getAll();
            map.keySet().removeAll(COMMON_KEYS);
            // Exclude keys that are already lens/sensor scoped
            map.keySet().removeIf(PreferenceKeys::isLensScopedKey);
            String json = GSON.toJson(map);
            for (String cameraId : ids) { //Makes a copy of default settings for each camera
                settingsManager.setInitial(Key.PER_LENS_FILE_NAME.mValue, PER_LENS_KEY_PREFIX + cameraId, json);
            }
        }
    }

    private static void saveJsonForCamera(String cameraID) {
        SettingsManager settingsManager = preferenceKeys.settingsManager;
        Map<String, ?> map = settingsManager.getDefaultPreferences().getAll();
        map.keySet().removeAll(COMMON_KEYS);
        // Exclude keys that are already lens/sensor scoped
        map.keySet().removeIf(PreferenceKeys::isLensScopedKey);
        String hashmapAsJson = GSON.toJson(map);
        String alreadySavedJSON = settingsManager.getString(Key.PER_LENS_FILE_NAME.mValue, PER_LENS_KEY_PREFIX + cameraID, "");
        if (!alreadySavedJSON.equals(hashmapAsJson)) {
            settingsManager.set(Key.PER_LENS_FILE_NAME.mValue, PER_LENS_KEY_PREFIX + getCameraID(), hashmapAsJson);
//            Log.d(TAG, PER_LENS_KEY_PREFIX + getCameraID() + " : JSON : " + hashmapAsJson);
        }
    }

    public static void loadSettingsForCamera(String cameraID) {
        SettingsManager settingsManager = preferenceKeys.settingsManager;
        String alreadySavedJSON = settingsManager.getString(Key.PER_LENS_FILE_NAME.mValue, PER_LENS_KEY_PREFIX + cameraID, null);
        if (alreadySavedJSON == null) {
            return;
        }
        HashMap<String, ?> map = GSON.fromJson(alreadySavedJSON, HashMap.class);
        if (map == null) {
            return;
        }
        for (Map.Entry<String, ?> e : map.entrySet()) {
            String key = e.getKey();
            // Skip keys that are already lens/sensor scoped or global
            if (isLensScopedKey(key) || COMMON_KEYS.contains(key)) {
                continue;
            }
            Object value = e.getValue();
            // Normalize boolean values to the "1"/"0" String convention used by
            // SettingsManager/ManagedSwitchPreference. This also fixes up legacy
            // data that may have been serialized as a raw Boolean ("true"/"false").
            if (value instanceof Boolean) {
                value = ((Boolean) value) ? "1" : "0";
            }
            settingsManager.set(SCOPE_GLOBAL, key, value.toString());
        }
    }

    public static void setActivityTheme(Activity activity) {
        SettingsManager sm = preferenceKeys.settingsManager;

        String theme = sm.getString(SCOPE_GLOBAL, Key.KEY_THEME_ACCENT, activity.getResources().getString(R.string.pref_theme_accent_default_value));

        if (isShowGradientOn()) {
            activity.getTheme().applyStyle(R.style.ThemeOverlay_Photon_GradientBackground, true);
        }

        int themeRes = resolveAccentTheme(theme);
        if (themeRes != 0) {
            activity.getTheme().applyStyle(themeRes, true);
        }
    }

    private static int resolveAccentTheme(String accent) {
        if (accent == null) {
            return 0;
        }
        switch (accent.toLowerCase(Locale.ROOT)) {
            case "default":
                // Material You dynamic color on Android 12+, legacy purple below.
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? R.style.ThemeOverlay_Photon_Dynamic
                        : R.style.ThemeOverlay_Photon_Default;
            case "red":
                return R.style.ThemeOverlay_Photon_Red;
            case "blue":
                return R.style.ThemeOverlay_Photon_Blue;
            case "orange":
                return R.style.ThemeOverlay_Photon_Orange;
            case "green":
                return R.style.ThemeOverlay_Photon_Green;
            case "eszdman":
                return R.style.ThemeOverlay_Photon_Eszdman;
            case "pink":
                return R.style.ThemeOverlay_Photon_Pink;
            case "cyan":
                return R.style.ThemeOverlay_Photon_Cyan;
            case "teal":
                return R.style.ThemeOverlay_Photon_Teal;
            case "white":
                return R.style.ThemeOverlay_Photon_White;
            default:
                return 0;
        }
    }

    /**
     * Helper functions for some keys defined in PreferenceFragment.
     */
    public static int getAfDataValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_SHOW_AF_DATA);
    }

    public static boolean isAfDataOn() {
        return getAfDataValue() > 0;
    }

    public static boolean isFullDebugOn() {
        return getAfDataValue() == 3;
    }

    public static boolean isHorizonOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_SHOW_HORIZON);
    }

    public static int isSystemNrOn() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_ENABLE_SYSTEM_NR);
    }

    public static boolean isRemosaicOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_REMOSAIC);
    }

    public static boolean isDisableAligningOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_DISABLE_ALIGNINIG);
    }

    public static boolean isShowWatermarkOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_SHOW_WATERMARK);
    }

    public static boolean isPerLensSettingsOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_SAVE_PER_LENS_SETTINGS);
    }

    public static boolean isPreserveManualWbOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_PRESERVE_MANUAL_WB);
    }

    public static boolean isEnhancedProcessionOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_ENHANCED_PROCESSING);
    }

    public static boolean isHdrxNrOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_HDRX_NR);
    }

    public static int isSaveRaw() {
        int v = preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_SAVE_RAW);
        // One-time migration for installs that stored the old 5-option Save
        // values (3 = HEIC, 4 = RAW + HEIC): fold back to the JPEG
        // counterpart and enable the Save HEIC toggle instead.
        if (v == 3 || v == 4) {
            int migrated = (v == 4) ? 1 : 0;
            preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_SAVE_RAW, migrated);
            preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_SAVE_HEIC, true);
            return migrated;
        }
        return v;
    }

    /** True when the Save HEIC toggle is on: HEIC is used instead of JPEG. */
    public static boolean isHeicSave() {
        return getBool(Key.KEY_SAVE_HEIC);
    }

    /** True when the current Save mode writes a DNG (RAW+JPEG, RAW). */
    public static boolean isRawSave() {
        int v = isSaveRaw();
        return v == 1 || v == 2;
    }

    public static boolean isBatterySaverOn(){
        return getBool(PreferenceKeys.Key.KEY_ENERGY_SAVING);
    }
    public static boolean isAspect169On(){
        return getBool(Key.KEY_WIDE169);
    }

    public static boolean isBinningOn(){
        return getBool(Key.KEY_BINNING);
    }

    public static void setBatterySaver(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_ENERGY_SAVING,value);
    }
    public static void setSaveRaw(int value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_SAVE_RAW,value);
    }

    public static void setHeicSave(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_SAVE_HEIC,value);
    }

    public static boolean isRoundEdgeOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_SHOW_ROUND_EDGE);
    }

    /** Viewfinder background modes, stored in {@link Key#KEY_VIEWFINDER_BACKGROUND}. */
    public static final String VIEWFINDER_BACKGROUND_NONE = "none";
    public static final String VIEWFINDER_BACKGROUND_GRADIENT = "gradient";
    public static final String VIEWFINDER_BACKGROUND_BLUR = "blur";

    /** The selected viewfinder background: none, gradient or blurred edges. */
    public static String getViewfinderBackground() {
        return preferenceKeys.settingsManager.getString(
                SCOPE_GLOBAL, Key.KEY_VIEWFINDER_BACKGROUND);
    }

    /**
     * True when the black areas above and below the preview should show the
     * unflipped, blurred strips of the live viewfinder edges.
     */
    public static boolean isBlurViewfinderEdgesOn() {
        return VIEWFINDER_BACKGROUND_BLUR.equals(getViewfinderBackground());
    }

    /** True when the camera background should use the themed gradient. */
    public static boolean isShowGradientOn() {
        return VIEWFINDER_BACKGROUND_GRADIENT.equals(getViewfinderBackground());
    }

    public static int getGridValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_SHOW_GRID);
    }

    public static void setGridValue(int value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_SHOW_GRID, value);
    }

    /**
     * Position of the multi-lens pill: {@code "right"}, {@code "center"} or
     * {@code "left"}. Right/left render the pill vertically docked to that edge,
     * center keeps the horizontal centered pill.
     */
    public static String getLensBarPosition() {
        return preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_LENS_BAR_POSITION, "right");
    }

    /**
     * True when the lens pill should label lenses with their 35mm-equivalent
     * focal length instead of the relative zoom factor.
     */
    public static boolean isLensMmEquivalentOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_LENS_MM_EQUIVALENT, false);
    }

    public static boolean isCameraSoundsOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_CAMERA_SOUNDS);
    }

    public static boolean isHapticsOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_HAPTICS, true);
    }

    public static void setHaptics(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_HAPTICS, value);
    }

    /** True when saved photos/videos should be geotagged with the current fix. */
    public static boolean isSaveLocationOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_SAVE_LOCATION, false);
    }

    public static void setSaveLocation(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_SAVE_LOCATION, value);
    }

    public static int getChromaNrValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_CHROMA_NR_SEEKBAR);
    }

    public static int getLumaNrValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_LUMA_NR_SEEKBAR);
    }

    public static int getFrameCountValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_FRAME_COUNT);
    }

    public static float getSharpnessValue() {
        return preferenceKeys.settingsManager.getFloat(SCOPE_GLOBAL, Key.KEY_SHARPNESS_SEEKBAR);
    }

    public static float getCompressorValue() {
        return preferenceKeys.settingsManager.getFloat(SCOPE_GLOBAL, Key.KEY_COMPRESSOR_SEEKBAR);
    }

    public static float getGainValue() {
        return preferenceKeys.settingsManager.getFloat(SCOPE_GLOBAL, Key.KEY_GAIN_SEEKBAR);
    }

    public static float getSaturationValue() {
        return preferenceKeys.settingsManager.getFloat(SCOPE_GLOBAL, Key.KEY_SATURATION_SEEKBAR);
    }

    public static float getContrastValue() {
        return preferenceKeys.settingsManager.getFloat(SCOPE_GLOBAL, Key.KEY_CONTRAST_SEEKBAR);
    }

    public static int getAlignMethodValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_ALIGN_METHOD);
    }

    public static int getColorMethodValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_COLOR_METHOD);
    }

    public static int getFocusPeakValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_FOCUS_PEAK);
    }

    public static int getPreviewFormatValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_PREVIEW_FORMAT);
    }

    public static int getCFAValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_CFA);
    }

    public static int getThemeValue() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_THEME);
    }

    /**
     * Helper functions for other keys such as viewfinder buttons, etc.
     */
    public static boolean isHdrXOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_HDRX);
    }

    public static void setHdrX(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_HDRX, value);
    }

    public static boolean isEisPhotoOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_EIS_PHOTO);
    }

    public static void setEisPhoto(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_EIS_PHOTO, value);
    }

    /** True when zoom is locked to the current lens (no auto lens-switch on zoom). */
    public static boolean isZoomLockOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_ZOOM_LOCK);
    }

    public static void setZoomLock(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_ZOOM_LOCK, value);
    }

    /** Master switch for auto lens-switch on zoom (separate from the pill lock). */
    public static boolean isAutoZoomSwitchOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_AUTO_ZOOM_SWITCH);
    }

    public static void setAutoZoomSwitch(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_AUTO_ZOOM_SWITCH, value);
    }

    public static int getFpsMode() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_FPS_PREVIEW);
    }

    public static void setFpsMode(int value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_FPS_PREVIEW, value);
    }

    /**
     * Photo-mode frame rate for a specific lens and Quad Bayer state. Falls
     * back to the legacy global value, which seeds every lens/quad
     * combination on installs that predate the per-lens keys.
     */
    public static int getFpsModeForLens(String cameraId, boolean quadBayer) {
        int fallback = getFpsMode();
        if (cameraId == null || cameraId.isEmpty()) {
            return fallback;
        }
        return getLensFpsValue(fpsLensKey(cameraId, quadBayer), fallback);
    }

    /**
     * Reads a per-lens frame-rate value with the given fallback. Direct read
     * (no prefs-map copy): this runs on the session/capture path. Tolerates
     * values restored as native numbers, not just strings.
     */
    private static int getLensFpsValue(String key, int fallback) {
        String value;
        try {
            value = preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, key, null);
        } catch (ClassCastException e) {
            Integer intValue = SettingsManagerExtensions.getInteger(
                    preferenceKeys.settingsManager, SCOPE_GLOBAL, key, null);
            value = intValue != null ? String.valueOf(intValue) : null;
        }
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void setFpsModeForLens(String cameraId, boolean quadBayer, int value) {
        if (cameraId == null || cameraId.isEmpty()) {
            return;
        }
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, fpsLensKey(cameraId, quadBayer),
                String.valueOf(value));
    }

    /** Photo-mode frame rate for the active lens + Quad Bayer state. */
    public static int getCurrentLensFpsMode() {
        return getFpsModeForLens(getCameraID(), isQuadBayerOn());
    }

    public static void setCurrentLensFpsMode(int value) {
        setFpsModeForLens(getCameraID(), isQuadBayerOn(), value);
    }

    private static String fpsLensKey(String cameraId, boolean quadBayer) {
        return FPS_LENS_KEY_PREFIX + cameraId + (quadBayer ? FPS_LENS_QUAD_SUFFIX : "");
    }

    /** Legacy global frame-rate selection for video and RAW video (seed). */
    public static int getVideoFpsMode() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_VIDEO_FPS);
    }

    public static void setVideoFpsMode(int value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_FPS, value);
    }

    /**
     * Video/RAW-video frame rate for a specific lens. Falls back to the legacy
     * global video value, which seeds every lens on installs that predate the
     * per-lens keys. Kept separate from the photo-mode keys.
     */
    public static int getVideoFpsModeForLens(String cameraId) {
        int fallback = getVideoFpsMode();
        if (cameraId == null || cameraId.isEmpty()) {
            return fallback;
        }
        return getLensFpsValue(VIDEO_FPS_LENS_KEY_PREFIX + cameraId, fallback);
    }

    public static void setVideoFpsModeForLens(String cameraId, int value) {
        if (cameraId == null || cameraId.isEmpty()) {
            return;
        }
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, VIDEO_FPS_LENS_KEY_PREFIX + cameraId,
                String.valueOf(value));
    }

    /** Video/RAW-video frame rate for the active lens. */
    public static int getCurrentLensVideoFpsMode() {
        return getVideoFpsModeForLens(getCameraID());
    }

    public static void setCurrentLensVideoFpsMode(int value) {
        setVideoFpsModeForLens(getCameraID(), value);
    }

    /**
     * Frame-rate selection for the mode's group: video and RAW video share the
     * per-lens video setting, while photo modes use the active lens + Quad
     * Bayer combination.
     */
    public static int getFpsModeForMode(CameraMode mode) {
        return (mode == CameraMode.VIDEO || mode == CameraMode.RAWVIDEO)
                ? getCurrentLensVideoFpsMode() : getCurrentLensFpsMode();
    }

    public static void setFpsModeForMode(CameraMode mode, int value) {
        if (mode == CameraMode.VIDEO || mode == CameraMode.RAWVIDEO) {
            setCurrentLensVideoFpsMode(value);
        } else {
            setCurrentLensFpsMode(value);
        }
    }

    /**
     * Effective Quad Bayer state: always off on ISZ virtual lenses. The stored
     * value is left untouched, so switching back to a non-ISZ lens restores it.
     */
    public static boolean isQuadBayerOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_QUAD_BAYER)
                && !IszLensUtil.isIszVirtual(getCameraID());
    }
    public static boolean isUltraHdrOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_ULTRAHDR);
    }

    public static void setQuadBayer(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_QUAD_BAYER, value);
    }

    public static String getCameraID() {
        return preferenceKeys.settingsManager.getString(Key.CAMERAS_PREFERENCE_FILE_NAME.mValue, Key.CAMERA_ID);
    }

    public static int getCountdownTimerIndex() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_COUNTDOWN_TIMER);
    }

    public static void setCountdownTimerIndex(int valueMS) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_COUNTDOWN_TIMER, valueMS);
    }
    
    public static int getBracketingMode() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_BRACKETING_MODE);
    }
    
    public static void setBracketingMode(int value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_BRACKETING_MODE, value);
    }

    public static int getAeMeteringStd() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_AE_METERING_STD);
    }

    public static void setAeMeteringStd(int value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_AE_METERING_STD, value);
    }

    public static void setCameraID(String value) {
        preferenceKeys.settingsManager.set(Key.CAMERAS_PREFERENCE_FILE_NAME.mValue, Key.CAMERA_ID, value);
    }

    public static int getAfMode() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_AF_MODE);
    }

    public static int getAeMode() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.KEY_AE_MODE);
    }

    public static void setAeMode(int value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_AE_MODE, value);
    }

    public static int getCameraModeOrdinal() {
        return preferenceKeys.settingsManager.getInteger(SCOPE_GLOBAL, Key.CAMERA_MODE);
    }

    public static void setCameraModeOrdinal(int value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.CAMERA_MODE, value);
    }

    /**
     * Ordinals (as strings) of camera modes the user chose to hide from the
     * mode selector. Checked = hidden. Empty by default (all modes shown).
     */
    public static Set<String> getHiddenModes() {
        try {
            Set<String> raw = getStringSet(Key.KEY_HIDE_MODES);
            if (raw == null) {
                return new HashSet<>(0);
            }
            Set<String> cleaned = new HashSet<>();
            int modeCount = CameraMode.values().length;
            for (String value : raw) {
                try {
                    int ordinal = Integer.parseInt(value);
                    if (ordinal >= 0 && ordinal < modeCount) {
                        cleaned.add(String.valueOf(ordinal));
                    }
                } catch (NumberFormatException ignored) {
                    // Drop corrupt entries.
                }
            }
            return cleaned;
        } catch (Exception e) {
            return new HashSet<>(0);
        }
    }

    public static void setHiddenModes(Set<String> hidden) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_HIDE_MODES,
                hidden != null ? new HashSet<>(hidden) : new HashSet<>(0));
    }

    public static boolean isModeHidden(CameraMode mode) {
        return mode != null && getHiddenModes().contains(String.valueOf(mode.ordinal()));
    }

    /**
     * User-defined display order of all modes. Stored as comma-separated
     * ordinals; absent or corrupt data falls back to enum order.
     */
    public static List<CameraMode> getModeOrder() {
        try {
            return CameraMode.parseOrder(
                    preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_MODE_ORDER, ""));
        } catch (Exception e) {
            return CameraMode.parseOrder(null);
        }
    }

    public static void setModeOrder(List<CameraMode> order) {
        StringBuilder builder = new StringBuilder();
        if (order != null) {
            for (CameraMode mode : order) {
                if (mode == null) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(mode.ordinal());
            }
        }
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_MODE_ORDER, builder.toString());
    }

    /**
     * Modes in the user's display order minus hidden ones. Never empty: if
     * everything is hidden (e.g. via a restored backup), the full order is
     * returned so the selector never ends up with zero items.
     */
    public static List<CameraMode> getVisibleModes() {
        return CameraMode.filterVisible(getModeOrder(), getHiddenModes());
    }

    /**
     * Next visible mode after {@code current} in the user's order, wrapping
     * around. Used when the currently selected mode becomes hidden.
     */
    public static CameraMode getFallbackMode(CameraMode current) {
        return CameraMode.findFallback(getModeOrder(), getHiddenModes(), current);
    }

    public static String getToneMap() {
        return preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.TONEMAP);
    }

    public static String getPref(Key key) {
        return preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, key);
    }

    public static Set<String> getStringSet(Key key) {
        return preferenceKeys.settingsManager.getStringSet(SCOPE_GLOBAL, key,new HashSet<>(0));
    }

    public static boolean getBool(Key key) {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, key);
    }

    public static float getFloat(Key key) {
        return preferenceKeys.settingsManager.getFloat(SCOPE_GLOBAL, key);
    }

    public static String getVideoResolution() {
        return preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_VIDEO_RESOLUTION, "1920x1080");
    }

    public static void setVideoResolution(String value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_RESOLUTION, value);
    }

    /** Video resolution used when the front (selfie) camera records. */
    public static String getSelfieVideoResolution() {
        return preferenceKeys.settingsManager.getString(
                SCOPE_GLOBAL, Key.KEY_VIDEO_RESOLUTION_SELFIE, "1920x1080");
    }

    public static void setSelfieVideoResolution(String value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_RESOLUTION_SELFIE, value);
    }

    public static int getVideoBitrateMbps() {
        try {
            return Integer.parseInt(preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_VIDEO_BITRATE, "30"));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    public static void setVideoBitrateMbps(int mbps) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_BITRATE, String.valueOf(mbps));
    }

    public static boolean isVideoHevc() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_VIDEO_HEVC);
    }

    public static void setVideoHevc(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_HEVC, value);
    }

    public static boolean isVideoHdr() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_VIDEO_HDR);
    }

    public static void setVideoHdr(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_HDR, value);
    }

    public static String getVideoHdrTransfer() {
        return preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_VIDEO_HDR_TRANSFER, "hlg");
    }

    public static void setVideoHdrTransfer(String value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_HDR_TRANSFER, value);
    }

    /**
     * HDR video color range: "limited" (default, spec-correct for HLG/PQ) or
     * "full". Full rewrites the encoded stream's range metadata on stop.
     */
    public static String getVideoColorRange() {
        return preferenceKeys.settingsManager.getString(
                SCOPE_GLOBAL, Key.KEY_VIDEO_COLOR_RANGE, "limited");
    }

    public static void setVideoColorRange(String value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_COLOR_RANGE, value);
    }

    /** Video OIS master switch: keeps OIS on even when EIS is active. */
    public static boolean isVideoOisOn() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_VIDEO_OIS, true);
    }

    public static void setVideoOisOn(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_OIS, value);
    }

    public static String getVideoHdrSessionType() {
        String value = preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_VIDEO_HDR_SESSION_TYPE, "");
        return value != null ? value.trim() : "";
    }

    public static void setVideoHdrSessionType(String value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_HDR_SESSION_TYPE, value);
    }

    public static String getVideoSdrSessionType() {
        String value = preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_VIDEO_SDR_SESSION_TYPE, "");
        return value != null ? value.trim() : "";
    }

    public static void setVideoSdrSessionType(String value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_SDR_SESSION_TYPE, value);
    }

    public static boolean isVideoUseLogicalId() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_VIDEO_USE_LOGICAL_ID);
    }

    public static void setVideoUseLogicalId(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_USE_LOGICAL_ID, value);
    }

    public static String getVideoLogicalId() {
        String id = preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_VIDEO_LOGICAL_ID, "0");
        return id != null ? id.trim() : "0";
    }

    public static void setVideoLogicalId(String value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_LOGICAL_ID, value);
    }

    public static String getVideoLogicalLenses() {
        String value = preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_VIDEO_LOGICAL_LENSES, "");
        return value != null ? value.trim() : "";
    }

    public static void setVideoLogicalLenses(String value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_VIDEO_LOGICAL_LENSES, value);
    }

    public static int getAudioBitrateKbps() {
        try {
            return Integer.parseInt(preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_AUDIO_BITRATE, "128"));
        } catch (NumberFormatException e) {
            return 128;
        }
    }

    public static void setAudioBitrateKbps(int kbps) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_AUDIO_BITRATE, String.valueOf(kbps));
    }

    public static boolean isAudioStereo() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_AUDIO_STEREO);
    }

    public static void setAudioStereo(boolean value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_AUDIO_STEREO, value);
    }

    public static String getAudioSource() {
        return preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_AUDIO_SOURCE, "camcorder");
    }

    public static void setAudioSource(String value) {
        preferenceKeys.settingsManager.set(SCOPE_GLOBAL, Key.KEY_AUDIO_SOURCE, value);
    }

    public static boolean isRawVideoDownscale4x() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_RAWVIDEO_DOWNSCALE_4X);
    }

    /** Raw video container selector values. */
    public static final String CONTAINER_DNG = "dng";
    public static final String CONTAINER_ZIP = "zip";
    public static final String CONTAINER_MCRAW = "mcraw";

    public static String getRawVideoContainer() {
        String value = preferenceKeys.settingsManager.getString(SCOPE_GLOBAL, Key.KEY_RAWVIDEO_CONTAINER, CONTAINER_ZIP);
        if (CONTAINER_MCRAW.equals(value) || CONTAINER_ZIP.equals(value) || CONTAINER_DNG.equals(value)) {
            return value;
        }
        return CONTAINER_ZIP;
    }

    public static boolean isRawVideoWriteZip() {
        return CONTAINER_ZIP.equals(getRawVideoContainer());
    }

    public static boolean isRawVideoCrop169() {
        return preferenceKeys.settingsManager.getBoolean(SCOPE_GLOBAL, Key.KEY_RAWVIDEO_CROP_169);
    }

    public enum Key {
        KEY_PREF_VERSION(R.string._pref_version),

        KEY_ENABLE_SYSTEM_NR(R.string.pref_enable_system_nr_key),
        KEY_SAVE_PER_LENS_SETTINGS(R.string.pref_save_per_lens_settings),
        KEY_PRESERVE_MANUAL_WB(R.string.pref_preserve_manual_wb_key),
        KEY_DISABLE_ALIGNINIG(R.string.pref_disable_aligning_key),
        KEY_SHOW_WATERMARK(R.string.pref_show_watermark_key),
        KEY_ENERGY_SAVING(R.string.pref_energy_safe_key),
        KEY_WIDE169(R.string.pref_wide169_key),
        KEY_BINNING(R.string.pref_binning_key),
        KEY_ENHANCED_PROCESSING(R.string.pref_enhanced_processing_key),
        KEY_HDRX_NR(R.string.pref_hdrx_nr_key),
        KEY_SHOW_ROUND_EDGE(R.string.pref_show_roundedge_key),
        KEY_SHOW_GRID(R.string.pref_show_grid_key),
        KEY_LENS_BAR_POSITION(R.string.pref_lens_bar_position_key),
        KEY_LENS_MM_EQUIVALENT(R.string.pref_lens_mm_equivalent_key),
        KEY_CAMERA_SOUNDS(R.string.pref_camera_sounds_key),
        KEY_HAPTICS(R.string.pref_haptics_key),
        KEY_CHROMA_NR_SEEKBAR(R.string.pref_chroma_nr_seekbar_key),
        KEY_LUMA_NR_SEEKBAR(R.string.pref_luma_nr_seekbar_key),
        KEY_COMPRESSOR_SEEKBAR(R.string.pref_compressor_seekbar_key),
        KEY_NOISESTR_SEEKBAR(R.string.pref_noise_seekbar_key),
        KEY_MERGE_SEEKBAR(R.string.pref_merge_seekbar_key),
        KEY_GAIN_SEEKBAR(R.string.pref_gain_seekbar_key),
        KEY_SHADOWS_SEEKBAR(R.string.pref_shadows_seekbar_key),
        KEY_FRAME_COUNT(R.string.pref_frame_count_key),
        KEY_CONTRAST_SEEKBAR(R.string.pref_contrast_seekbar_key),
        KEY_SHARPNESS_SEEKBAR(R.string.pref_sharpness_seekbar_key),
        KEY_EXPOCOMPENSATE_SEEKBAR(R.string.pref_expocompensation_seekbar_key),
        KEY_SATURATION_SEEKBAR(R.string.pref_saturation_seekbar_key),
        KEY_ALIGN_METHOD(R.string.pref_align_method_key),
        KEY_COLOR_METHOD(R.string.pref_color_method_key),
        KEY_FOCUS_PEAK(R.string.pref_peak_method_key),
        KEY_PREVIEW_FORMAT(R.string.pref_preview_format_key),
        KEY_TELEGRAM(R.string.pref_telegram_channel_key),
        KEY_CONTRIBUTORS(R.string.pref_contributors_key),
        KEY_THEME(R.string.pref_theme_key),
        KEY_THEME_ACCENT(R.string.pref_theme_accent_key),
        KEY_VIEWFINDER_BACKGROUND(R.string.pref_viewfinder_background_key),
        KEY_HIDE_GALLERY_ICON(R.string.pref_hide_gallery_icon_key),
        KEY_HIDE_MODES(R.string.pref_hide_modes_key),
        KEY_MODE_ORDER(R.string.pref_mode_order_key),
        KEY_SAVE_LOCATION(R.string.pref_save_location_key),
        KEY_AF_MODE(R.string.pref_af_mode_key),
        KEY_AE_MODE(R.string.pref_ae_mode_key),
        KEY_AE_METERING_STD(R.string.pref_ae_metering_std_key),
        KEY_BRACKETING_MODE(R.string.pref_bracketing_key),
        KEY_COUNTDOWN_TIMER(R.string.pref_countdown_timer_key),
        /**
         * Enhanced settings keys
         */
        KEY_PREVIEW_RESOLUTION(R.string.pref_preview_resolution_key),////TODO add preview resolution selector
        KEY_VIDEO_RESOLUTION(R.string.pref_video_resolution_key),
        KEY_VIDEO_RESOLUTION_SELFIE(R.string.pref_video_resolution_selfie_key),
        KEY_VIDEO_BITRATE(R.string.pref_video_bitrate_key),
        KEY_VIDEO_HEVC(R.string.pref_video_hevc_key),
        KEY_VIDEO_HDR(R.string.pref_video_hdr_key),
        KEY_VIDEO_HDR_TRANSFER(R.string.pref_video_hdr_transfer_key),
        KEY_VIDEO_COLOR_RANGE(R.string.pref_video_color_range_key),
        KEY_VIDEO_OIS(R.string.pref_video_ois_key),
        KEY_VIDEO_HDR_SESSION_TYPE(R.string.pref_video_hdr_session_type_key),
        KEY_VIDEO_SDR_SESSION_TYPE(R.string.pref_video_sdr_session_type_key),
        KEY_VIDEO_USE_LOGICAL_ID(R.string.pref_video_use_logical_id_key),
        KEY_VIDEO_LOGICAL_ID(R.string.pref_video_logical_id_key),
        KEY_VIDEO_LOGICAL_LENSES(R.string.pref_video_logical_lenses_key),
        KEY_AUDIO_BITRATE(R.string.pref_audio_bitrate_key),
        KEY_AUDIO_STEREO(R.string.pref_audio_stereo_key),
        KEY_AUDIO_SOURCE(R.string.pref_audio_source_key),
        KEY_RAWVIDEO_DOWNSCALE_4X(R.string.pref_rawvideo_downscale_4x_key),
        KEY_RAWVIDEO_WRITE_ZIP(R.string.pref_rawvideo_write_zip_key),
        KEY_RAWVIDEO_CONTAINER(R.string.pref_rawvideo_container_key),
        KEY_RAWVIDEO_CROP_169(R.string.pref_rawvideo_crop_169_key),
        KEY_SHOW_AF_DATA(R.string.pref_show_afdata_key),
        KEY_SHOW_HORIZON(R.string.pref_horizon),
        KEY_SAVE_RAW(R.string.pref_save_raw_key),
        KEY_SAVE_HEIC(R.string.pref_save_heic_key),
        KEY_CFA(R.string.pref_cfa_key),
        KEY_REMOSAIC(R.string.pref_remosaic_key),////TODO

        /**
         * Other Keys
         */
        KEY_HDRX(R.string.pref_hdrx_key),
        KEY_EIS_PHOTO(R.string.pref_eis_photo_key),
        KEY_ZOOM_LOCK(R.string.pref_zoom_lock_key),
        KEY_AUTO_ZOOM_SWITCH(R.string.pref_auto_zoom_switch_key),
        KEY_QUAD_BAYER(R.string.pref_quad_bayer_key),
        KEY_FPS_PREVIEW(R.string.pref_fps_preview_key),
        KEY_VIDEO_FPS(R.string.pref_video_fps_key),
        KEY_ULTRAHDR(R.string.pref_ultrahdr_key),
        CAMERA_ID(R.string.camera_id),
        TONEMAP(R.string.tonemap_key),
        GAMMA(R.string.gamma_key),
        CAMERA_MODE(R.string.pref_camera_mode_key),

        /* CameraManager 2 keys */
        CAMERAS_PREFERENCE_FILE_NAME(R.string._cameras),
        ALL_CAMERA_IDS_KEY(R.string.all_camera_ids),
        FRONT_IDS_KEY(R.string.front_camera_ids),
        BACK_IDS_KEY(R.string.back_camera_ids),
        ALL_CAMERA_LENS_KEY(R.string.all_camera_lens),
        CAMERA_COUNT_KEY(R.string.all_camera_count),

        /* SupportedDevice keys */
        DEVICES_PREFERENCE_FILE_NAME(R.string._devices),
        ALL_DEVICES_NAMES_KEY(R.string.all_devices_names),
        /**
         * Per Lens File
         */
        PER_LENS_FILE_NAME(R.string._per_lens),
        FOLDERS_LIST(R.string.pref_folders_list);
        public final String mValue;

        Key(@StringRes int stringId) {
            mValue = PhotonCamera.getStringStatic(stringId);
        }
    }
}
