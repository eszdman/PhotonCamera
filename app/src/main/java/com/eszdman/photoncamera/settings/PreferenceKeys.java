package com.eszdman.photoncamera.settings;

import android.content.res.Resources;
import android.util.Log;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;

/**
 * Created by Vibhor 06/09/2020
 */
public class PreferenceKeys {
    /**
     * Keys visible in Settings
     */
    private static final String TAG = "PreferenceKeys";
    public static final String SCOPE_GLOBAL = "default_scope";
    public static final String KEY_SHOW_AF_DATA = "pref_show_afdata_key";
    public static final String KEY_ENABLE_SYSTEM_NR = "pref_enable_system_nr_key";
    public static final String KEY_SAVE_PER_LENS_SETTINGS = "pref_save_per_lens_settings";//TODO
    public static final String KEY_DISABLE_ALIGNINIG = "pref_disable_aligning_key";
    public static final String KEY_SHOW_WATERMARK = "pref_show_watermark_key";
    public static final String KEY_ENHANCED_PROCESSING = "pref_enhanced_processing_key";
    public static final String KEY_HDRX_NR = "pref_hdrx_nr_key";
    public static final String KEY_SAVE_RAW = "pref_save_raw_key";
    public static final String KEY_SHOW_ROUND_EDGE = "pref_show_roundedge_key";
    public static final String KEY_SHOW_GRID = "pref_show_grid_key";
    public static final String KEY_CAMERA_SOUNDS = "pref_camera_sounds_key";
    public static final String KEY_CHROMA_NR_SEEKBAR = "pref_chroma_nr_seekbar_key";
    public static final String KEY_LUMA_NR_SEEKBAR = "pref_luma_nr_seekbar_key";
    public static final String KEY_COMPRESSOR_SEEKBAR = "pref_compressor_seekbar_key";
    public static final String KEY_GAIN_SEEKBAR = "pref_gain_seekbar_key";
    public static final String KEY_FRAME_COUNT = "pref_frame_count_key";
    public static final String KEY_CONTRAST_SEEKBAR = "pref_contrast_seekbar_key";
    public static final String KEY_SHARPNESS_SEEKBAR = "pref_sharpness_seekbar_key";
    public static final String KEY_SATURATION_SEEKBAR = "pref_saturation_seekbar_key";
    public static final String KEY_ALIGN_METHOD = "pref_align_method_key";
    public static final String KEY_CFA = "pref_cfa_key";
    public static final String KEY_REMOSAIC = "pref_remosaic_key";////TODO
    public static final String KEY_TELEGRAM = "pref_telegram_channel";
    public static final String KEY_CONTRIBUTORS = "pref_contributors";
    /**
     * Other Keys
     */
    public static final String KEY_HDRX = "pref_hdrx_key";
    public static final String KEY_EIS_PHOTO = "pref_eis_photo_key";
    public static final String KEY_QUAD_BAYER = "pref_quad_bayer_key";
    public static final String KEY_FPS_PREVIEW = "pref_fps_preview_key";
    public static final String CAMERA_ID = "camera_id";
    public static final String TONEMAP = "tonemap";
    private static final String CAMERA_MODE = "pref_camera_mode";
    /**
     * Scope to use SCOPE_GLOBAL => DefaultSharedPreferences
     */
    public static String current_scope = SCOPE_GLOBAL;

    public static void setDefaults() {
        SettingsManager settingsManager = Interface.getSettingsManager();
        Resources resources = Interface.getMainActivity().getResources();
        settingsManager.setDefaults(KEY_HDRX, resources.getBoolean(R.bool.pref_hdrx_mode_default));
        settingsManager.setDefaults(KEY_EIS_PHOTO, resources.getBoolean(R.bool.pref_eis_photo_default));
        settingsManager.setDefaults(KEY_QUAD_BAYER, resources.getBoolean(R.bool.pref_quad_bayer_default));
        settingsManager.setDefaults(KEY_REMOSAIC, resources.getBoolean(R.bool.pref_remosaic_default));
        settingsManager.setDefaults(KEY_FPS_PREVIEW, resources.getBoolean(R.bool.pref_fps_preview_default));
        settingsManager.setDefaults(CAMERA_ID, resources.getString(R.string.camera_id_default), new String[]{""});
        settingsManager.setDefaults(TONEMAP, resources.getString(R.string.tonemap_default), new String[]{resources.getString(R.string.tonemap_default)});
        settingsManager.addListener(new SettingsManager.OnSettingChangedListener() {
            @Override
            public void onSettingChanged(SettingsManager settingsManager, String key) {
                Interface.getSettings().loadCache();
                Log.d(TAG,key+" : changed!");
            }
        });
    }

    /**
     * Helper functions for some keys defined in PreferenceFragment.
     */
    public static boolean isAfDataOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_SHOW_AF_DATA);
    }

    public static int isSystemNrOn() {
        return Interface.getSettingsManager().getInteger(current_scope, KEY_ENABLE_SYSTEM_NR);
    }

    public static boolean isRemosaicOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_REMOSAIC);
    }

    public static boolean isDisableAligningOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_DISABLE_ALIGNINIG);
    }

    public static boolean isShowWatermarkOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_SHOW_WATERMARK);
    }

    public static boolean isPerLensSettingsOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_SAVE_PER_LENS_SETTINGS);
    }

    public static boolean isEnhancedProcessionOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_ENHANCED_PROCESSING);
    }

    public static boolean isHdrxNrOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_HDRX_NR);
    }

    public static boolean isSaveRawOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_SAVE_RAW);
    }

    public static boolean isRoundEdgeOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_SHOW_ROUND_EDGE);
    }

    public static boolean isShowGridOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_SHOW_GRID);
    }

    public static boolean isCameraSoundsOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_CAMERA_SOUNDS);
    }

    public static int getChromaNrValue() {
        return Interface.getSettingsManager().getInteger(current_scope, KEY_CHROMA_NR_SEEKBAR);
    }

    public static int getLumaNrValue() {
        return Interface.getSettingsManager().getInteger(current_scope, KEY_LUMA_NR_SEEKBAR);
    }

    public static int getFrameCountValue() {
        return Interface.getSettingsManager().getInteger(current_scope, KEY_FRAME_COUNT);
    }

    public static float getSharpnessValue() {
        return Interface.getSettingsManager().getFloat(current_scope, KEY_SHARPNESS_SEEKBAR);
    }

    public static float getCompressorValue() {
        return Interface.getSettingsManager().getFloat(current_scope, KEY_COMPRESSOR_SEEKBAR);
    }

    public static float getGainValue() {
        return Interface.getSettingsManager().getFloat(current_scope, KEY_GAIN_SEEKBAR);
    }

    public static float getSaturationValue() {
        return Interface.getSettingsManager().getFloat(current_scope, KEY_SATURATION_SEEKBAR);
    }

    public static float getContrastValue() {
        return Interface.getSettingsManager().getFloat(current_scope, KEY_CONTRAST_SEEKBAR);
    }

    public static int getAlignMethodValue() {
        return Interface.getSettingsManager().getInteger(current_scope, KEY_ALIGN_METHOD);
    }

    public static int getCFAValue() {
        return Interface.getSettingsManager().getInteger(current_scope, KEY_CFA);
    }

    /**
     * Helper functions for other keys such as viewfinder buttons, etc.
     */
    public static boolean isHdrXOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_HDRX);
    }

    public static void setHdrX(boolean value) {
        Interface.getSettingsManager().set(current_scope, KEY_HDRX, value);
    }

    public static boolean isEisPhotoOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_EIS_PHOTO);
    }

    public static void setEisPhoto(boolean value) {
        Interface.getSettingsManager().set(current_scope, KEY_EIS_PHOTO, value);
    }

    public static boolean isFpsPreviewOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_FPS_PREVIEW);
    }

    public static void setFpsPreview(boolean value) {
        Interface.getSettingsManager().set(current_scope, KEY_FPS_PREVIEW, value);
    }

    public static boolean isQuadBayerOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_QUAD_BAYER);
    }

    public static void setQuadBayer(boolean value) {
        Interface.getSettingsManager().set(current_scope, KEY_QUAD_BAYER, value);
    }

    public static String getCameraID() {
        return Interface.getSettingsManager().getString(current_scope, CAMERA_ID);
    }

    public static void setCameraID(String value) {
        Interface.getSettingsManager().set(current_scope, CAMERA_ID, value);
    }

    public static int getCameraMode() {
        return Interface.getSettingsManager().getInteger(current_scope, CAMERA_MODE);
    }

    public static void setCameraMode(int value) {
        Interface.getSettingsManager().set(current_scope, CAMERA_MODE, value);
    }

    public static String getTonemap() {
        return Interface.getSettingsManager().getString(current_scope, TONEMAP);
    }

}
