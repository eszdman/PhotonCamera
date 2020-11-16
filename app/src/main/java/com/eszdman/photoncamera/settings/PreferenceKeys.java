package com.eszdman.photoncamera.settings;

import android.app.Activity;
import android.content.res.Resources;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;

import java.lang.reflect.Field;
import java.util.PropertyResourceBundle;

/**
 * Created by Vibhor 06/09/2020
 */
public class PreferenceKeys {
    /**
     * Keys visible in Settings
     */
    public enum Preference {
        KEY_SHOW_AF_DATA("pref_show_afdata_key"),
        KEY_ENABLE_SYSTEM_NR("pref_enable_system_nr_key"),
        KEY_SAVE_PER_LENS_SETTINGS("pref_save_per_lens_settings"),
        KEY_DISABLE_ALIGNINIG("pref_disable_aligning_key"),
        KEY_SHOW_WATERMARK("pref_show_watermark_key"),
        KEY_ENHANCED_PROCESSING("pref_enhanced_processing_key"),
        KEY_HDRX_NR("pref_hdrx_nr_key"),
        KEY_SAVE_RAW("pref_save_raw_key"),
        KEY_SHOW_ROUND_EDGE("pref_show_roundedge_key"),
        KEY_SHOW_GRID("pref_show_grid_key"),
        KEY_CAMERA_SOUNDS("pref_camera_sounds_key"),
        KEY_CHROMA_NR_SEEKBAR("pref_chroma_nr_seekbar_key"),
        KEY_LUMA_NR_SEEKBAR("pref_luma_nr_seekbar_key"),
        KEY_COMPRESSOR_SEEKBAR("pref_compressor_seekbar_key"),
        KEY_NOISESTR_SEEKBAR("pref_noise_seekbar_key"),
        KEY_GAIN_SEEKBAR("pref_gain_seekbar_key"),
        KEY_FRAME_COUNT("pref_frame_count_key"),
        KEY_CONTRAST_SEEKBAR("pref_contrast_seekbar_key"),
        KEY_SHARPNESS_SEEKBAR("pref_sharpness_seekbar_key"),
        KEY_SATURATION_SEEKBAR("pref_saturation_seekbar_key"),
        KEY_ALIGN_METHOD("pref_align_method_key"),
        KEY_CFA("pref_cfa_key"),
        KEY_REMOSAIC("pref_remosaic_key"),////TODO
        KEY_TELEGRAM("pref_telegram_channel"),
        KEY_CONTRIBUTORS("pref_contributors"),
        KEY_THEME("pref_theme_key"),
        KEY_THEME_ACCENT("pref_theme_accent_key"),
        /**
         * Other Keys
         */
        KEY_HDRX("pref_hdrx_key"),
        KEY_EIS_PHOTO("pref_eis_photo_key"),
        KEY_QUAD_BAYER("pref_quad_bayer_key"),
        KEY_FPS_PREVIEW("pref_fps_preview_key"),
        CAMERA_ID("camera_id"),
        TONEMAP("tonemap"),
        GAMMA("gamma"),
        CAMERA_MODE("pref_camera_mode"),

        /* CameraManager 2 keys */
        PREFERENCE_FILE_NAME("_cameras"),
        ALL_CAMERA_IDS_KEY("all_camera_ids"),
        FRONT_IDS_KEY("front_camera_ids"),
        BACK_IDS_KEY("back_camera_ids"),
        FOCAL_IDS_KEY("all_camera_focals"),
        CAMERA_COUNT_KEY("all_camera_count"),
        ;
        public final String mValue;

        Preference(String mName) {
            mValue = mName;
        }
    }
    private static final String TAG = "PreferenceKeys";
    public static final String SCOPE_GLOBAL = "default_scope";
    /**
     * Scope to use SCOPE_GLOBAL => DefaultSharedPreferences
     */
    public static String current_scope = SCOPE_GLOBAL;

    public static void setDefaults() {
        SettingsManager settingsManager = PhotonCamera.getSettingsManager();
        Resources resources = PhotonCamera.getCameraActivity().getResources();
        settingsManager.setDefaults(Preference.KEY_HDRX, resources.getBoolean(R.bool.pref_hdrx_mode_default));
        settingsManager.setDefaults(Preference.KEY_EIS_PHOTO, resources.getBoolean(R.bool.pref_eis_photo_default));
        settingsManager.setDefaults(Preference.KEY_QUAD_BAYER, resources.getBoolean(R.bool.pref_quad_bayer_default));
        settingsManager.setDefaults(Preference.KEY_REMOSAIC, resources.getBoolean(R.bool.pref_remosaic_default));
        settingsManager.setDefaults(Preference.KEY_FPS_PREVIEW, resources.getBoolean(R.bool.pref_fps_preview_default));
        settingsManager.setDefaults(Preference.CAMERA_ID, resources.getString(R.string.camera_id_default), new String[]{""});
        settingsManager.setDefaults(Preference.TONEMAP, resources.getString(R.string.tonemap_default), new String[]{resources.getString(R.string.tonemap_default)});
        settingsManager.setDefaults(Preference.GAMMA, resources.getString(R.string.gamma_default), new String[]{resources.getString(R.string.gamma_default)});
        settingsManager.addListener((settingsManager1, key) -> {
            PhotonCamera.getSettings().loadCache();
            Log.d(TAG, key + " : changed!");
        });
    }

    public static void setActivityTheme(Activity activity) {
        String theme = PreferenceManager.getDefaultSharedPreferences(activity.getApplication()).getString(Preference.KEY_THEME_ACCENT.mValue, activity.getString(R.string.pref_theme_accent_default_value));
        try {
            assert theme != null;
            Field resourceField = R.style.class.getDeclaredField(theme);
            int resourceId = resourceField.getInt(resourceField);
            activity.setTheme(resourceId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper functions for some keys defined in PreferenceFragment.
     */
    public static boolean isAfDataOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_SHOW_AF_DATA);
    }

    public static int isSystemNrOn() {
        return PhotonCamera.getSettingsManager().getInteger(current_scope, Preference.KEY_ENABLE_SYSTEM_NR);
    }

    public static boolean isRemosaicOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_REMOSAIC);
    }

    public static boolean isDisableAligningOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_DISABLE_ALIGNINIG);
    }

    public static boolean isShowWatermarkOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_SHOW_WATERMARK);
    }

    public static boolean isPerLensSettingsOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_SAVE_PER_LENS_SETTINGS);
    }

    public static boolean isEnhancedProcessionOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_ENHANCED_PROCESSING);
    }

    public static boolean isHdrxNrOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_HDRX_NR);
    }

    public static boolean isSaveRawOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_SAVE_RAW);
    }

    public static boolean isRoundEdgeOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_SHOW_ROUND_EDGE);
    }

    public static boolean isShowGridOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_SHOW_GRID);
    }

    public static boolean isCameraSoundsOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_CAMERA_SOUNDS);
    }

    public static int getChromaNrValue() {
        return PhotonCamera.getSettingsManager().getInteger(current_scope, Preference.KEY_CHROMA_NR_SEEKBAR);
    }

    public static int getLumaNrValue() {
        return PhotonCamera.getSettingsManager().getInteger(current_scope, Preference.KEY_LUMA_NR_SEEKBAR);
    }

    public static int getFrameCountValue() {
        return PhotonCamera.getSettingsManager().getInteger(current_scope, Preference.KEY_FRAME_COUNT);
    }

    public static float getSharpnessValue() {
        return PhotonCamera.getSettingsManager().getFloat(current_scope, Preference.KEY_SHARPNESS_SEEKBAR);
    }

    public static float getCompressorValue() {
        return PhotonCamera.getSettingsManager().getFloat(current_scope, Preference.KEY_COMPRESSOR_SEEKBAR);
    }

    public static float getGainValue() {
        return PhotonCamera.getSettingsManager().getFloat(current_scope, Preference.KEY_GAIN_SEEKBAR);
    }

    public static float getSaturationValue() {
        return PhotonCamera.getSettingsManager().getFloat(current_scope, Preference.KEY_SATURATION_SEEKBAR);
    }

    public static float getContrastValue() {
        return PhotonCamera.getSettingsManager().getFloat(current_scope, Preference.KEY_CONTRAST_SEEKBAR);
    }

    public static int getAlignMethodValue() {
        return PhotonCamera.getSettingsManager().getInteger(current_scope, Preference.KEY_ALIGN_METHOD);
    }

    public static int getCFAValue() {
        return PhotonCamera.getSettingsManager().getInteger(current_scope, Preference.KEY_CFA);
    }

    public static int getThemeValue() {
        return PhotonCamera.getSettingsManager().getInteger(current_scope, Preference.KEY_THEME);
    }

    /**
     * Helper functions for other keys such as viewfinder buttons, etc.
     */
    public static boolean isHdrXOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_HDRX);
    }

    public static void setHdrX(boolean value) {
        PhotonCamera.getSettingsManager().set(current_scope, Preference.KEY_HDRX, value);
    }

    public static boolean isEisPhotoOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_EIS_PHOTO);
    }

    public static void setEisPhoto(boolean value) {
        PhotonCamera.getSettingsManager().set(current_scope, Preference.KEY_EIS_PHOTO, value);
    }

    public static boolean isFpsPreviewOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_FPS_PREVIEW);
    }

    public static void setFpsPreview(boolean value) {
        PhotonCamera.getSettingsManager().set(current_scope, Preference.KEY_FPS_PREVIEW, value);
    }

    public static boolean isQuadBayerOn() {
        return PhotonCamera.getSettingsManager().getBoolean(current_scope, Preference.KEY_QUAD_BAYER);
    }

    public static void setQuadBayer(boolean value) {
        PhotonCamera.getSettingsManager().set(current_scope, Preference.KEY_QUAD_BAYER, value);
    }

    public static String getCameraID() {
        return PhotonCamera.getSettingsManager().getString(current_scope, Preference.CAMERA_ID);
    }

    public static void setCameraID(String value) {
        PhotonCamera.getSettingsManager().set(current_scope, Preference.CAMERA_ID, value);
    }

    public static int getCameraMode() {
        return PhotonCamera.getSettingsManager().getInteger(current_scope, Preference.CAMERA_MODE);
    }

    public static void setCameraMode(int value) {
        PhotonCamera.getSettingsManager().set(current_scope, Preference.CAMERA_MODE, value);
    }

    public static String getToneMap() {
        return PhotonCamera.getSettingsManager().getString(current_scope, Preference.TONEMAP);
    }
    public static String getPref(Preference preference) {
        return PhotonCamera.getSettingsManager().getString(current_scope, preference);
    }
    public static float getFloat(Preference preference) {
        return PhotonCamera.getSettingsManager().getFloat(current_scope, preference);
    }
}
