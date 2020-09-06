package com.eszdman.photoncamera.settings;

import android.content.res.Resources;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;

/**
 * Created by Vibhor 06/09/2020
 */
public class PreferenceKeys {
    /**
     * Keys visible in Settings
     */
    public static final String SCOPE_GLOBAL = "default_scope";
    public static final String KEY_SHOW_AF_DATA = "pref_show_afdata_key";
    public static final String KEY_ENABLE_SYSTEM_NR = "pref_enable_system_nr_key";
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
    public static final String KEY_FRAME_COUNT = "pref_frame_count_key";
    public static final String KEY_CONTRAST_SEEKBAR = "pref_contrast_seekbar_key";
    public static final String KEY_SHARPNESS_SEEKBAR = "pref_sharpness_seekbar_key";
    public static final String KEY_SATURATION_SEEKBAR = "pref_saturation_seekbar_key";
    public static final String KEY_ALIGN_METHOD = "pref_align_method_key";
    public static final String KEY_CFA = "pref_cfa_key";
    public static final String KEY_TELEGRAM = "pref_telegram_channel";
    /**
     * Other Keys
     */
    public static final String KEY_HDRX = "pref_hdrx_key";
    public static final String KEY_EIS_PHOTO = "pref_eis_photo_key";
    public static final String KEY_QUAD_BAYER = "pref_quad_bayer_key";
    public static final String KEY_REMOSAIC = "pref_remosaic_key";
    public static final String KEY_FPS_PREVIEW = "pref_fps_preview_key";
    public static final String CAMERA_ID = "camera_id";
    public static final String TONEMAP = "tonemap";
    /**
     * Scope to use SCOPE_GLOBAL => DefaultSharedPreferences
     */
    public static String current_scope = SCOPE_GLOBAL;

    public static void setDefaults() {
        SettingsManager settingsManager = Interface.getSettingsManager();
        Resources resources = Interface.getMainActivity().getResources();
        settingsManager.setDefaults(KEY_HDRX, resources.getBoolean(R.bool.pref_hdrx_mode_default));//
        settingsManager.setDefaults(KEY_EIS_PHOTO, resources.getBoolean(R.bool.pref_eis_photo_default));
        settingsManager.setDefaults(KEY_QUAD_BAYER, resources.getBoolean(R.bool.pref_quad_bayer_default));
        settingsManager.setDefaults(KEY_REMOSAIC, resources.getBoolean(R.bool.pref_remosaic_default));
        settingsManager.setDefaults(KEY_FPS_PREVIEW, resources.getBoolean(R.bool.pref_fps_preview_default));
        settingsManager.setDefaults(CAMERA_ID, resources.getString(R.string.camera_id_default), new String[]{""});
        settingsManager.setDefaults(TONEMAP, resources.getString(R.string.tonemap_default), new String[]{""});
    }

    /**
     * Helper functions for some keys defined in PreferenceFragment.
     */
    public static boolean isAfDataOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_SHOW_AF_DATA);
    }

    public static boolean isSystemNrOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_ENABLE_SYSTEM_NR);
    }

    public static boolean isDisableAligningOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_DISABLE_ALIGNINIG);
    }

    public static boolean isShowWatermarkOn() {
        return Interface.getSettingsManager().getBoolean(current_scope, KEY_SHOW_WATERMARK);
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

    public static int getSharpnessValue() {
        return Interface.getSettingsManager().getInteger(current_scope, KEY_SHARPNESS_SEEKBAR);
    }

    public static int getSaturationValue() {
        return Interface.getSettingsManager().getInteger(current_scope, KEY_SATURATION_SEEKBAR);
    }

    public static int getContrastValue() {
        return Interface.getSettingsManager().getInteger(current_scope, KEY_CONTRAST_SEEKBAR);
    }

    public static String getAlignMethodValue() {
        return Interface.getSettingsManager().getString(current_scope, KEY_ALIGN_METHOD);
    }

    public static String getCFAValue() {
        return Interface.getSettingsManager().getString(current_scope, KEY_CFA);
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

}
