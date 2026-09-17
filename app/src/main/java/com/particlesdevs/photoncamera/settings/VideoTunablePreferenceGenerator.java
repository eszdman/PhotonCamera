package com.particlesdevs.photoncamera.settings;

import android.content.Context;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.VendorTagUtils;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunableKeyDialog;
import com.particlesdevs.photoncamera.ui.settings.custompreferences.TunableKeyPreference;
import com.particlesdevs.photoncamera.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the preferences for the global video-only tunable-key lists (SDR
 * and HDR). Mirrors the sensor-config tunable section
 * ({@link SensorConfigPreferenceGenerator}) but uses
 * {@link TunableKeyManager#VIDEO_TUNABLE_ID} /
 * {@link TunableKeyManager#VIDEO_HDR_TUNABLE_ID} so the keys apply in video
 * mode only, regardless of sensor.
 */
public final class VideoTunablePreferenceGenerator {
    private static final String TAG = "VideoTunablePrefs";

    /** Config for one tunable list (SDR or HDR). */
    public static final class Config {
        public final String tunableId;
        public final String categoryKey;
        public final String addButtonKey;
        public final int titleRes;
        public final int summaryRes;
        public final int addSummaryRes;

        public Config(String tunableId, String categoryKey, String addButtonKey,
                int titleRes, int summaryRes, int addSummaryRes) {
            this.tunableId = tunableId;
            this.categoryKey = categoryKey;
            this.addButtonKey = addButtonKey;
            this.titleRes = titleRes;
            this.summaryRes = summaryRes;
            this.addSummaryRes = addSummaryRes;
        }
    }

    public static final Config SDR = new Config(
            TunableKeyManager.VIDEO_TUNABLE_ID,
            "pref_category_video_tunablekeys",
            "pref_video_add_tunablekey",
            R.string.video_tunable_keys_sdr,
            R.string.video_tunable_summary_sdr,
            R.string.video_tunable_add_summary_sdr);

    public static final Config HDR = new Config(
            TunableKeyManager.VIDEO_HDR_TUNABLE_ID,
            "pref_category_video_hdr_tunablekeys",
            "pref_video_hdr_add_tunablekey",
            R.string.video_tunable_keys_hdr,
            R.string.video_tunable_summary_hdr,
            R.string.video_tunable_add_summary_hdr);

    private VideoTunablePreferenceGenerator() {}

    public static void generatePreferences(Context context, PreferenceScreen screen) {
        generatePreferences(context, screen, SDR);
    }

    public static void generatePreferences(Context context, PreferenceScreen screen, Config config) {
        try {
            PreferenceCategory category = new PreferenceCategory(context);
            category.setKey(config.categoryKey);
            category.setTitle(context.getString(config.titleRes));
            category.setSummary(context.getString(config.summaryRes));
            category.setLayoutResource(R.layout.preference_category_layout);
            screen.addPreference(category);
            addTunableKeySection(context, category, config);
        } catch (Exception e) {
            Log.w(TAG, "Failed to generate video tunable preferences: " + Log.getStackTraceString(e));
        }
    }

    private static void addTunableKeySection(Context context, PreferenceCategory category, Config config) {
        Runnable refresh = () -> refreshTunableKeys(context, category, config);

        List<VendorTagUtils.TunableKey> keys =
                TunableKeyManager.loadKeys(context, config.tunableId);
        for (int i = 0; i < keys.size(); i++) {
            TunableKeyPreference keyPref = new TunableKeyPreference(
                    context, config.tunableId, i, refresh);
            keyPref.setOrder(100 + i);
            // TunableKeyPreference loads per-sensor keys via loadKeys(); the
            // video ids resolve to the global JSON keys, so reuse it.
            category.addPreference(keyPref);
        }

        androidx.preference.Preference addButton = new androidx.preference.Preference(context);
        addButton.setKey(config.addButtonKey);
        addButton.setLayoutResource(R.layout.preference_with_margin);
        addButton.setTitle("+ Add Tunable Key");
        addButton.setSummary(context.getString(config.addSummaryRes));
        addButton.setIcon(R.drawable.ic_add);
        addButton.setOrder(10000);
        addButton.setOnPreferenceClickListener(preference -> {
            TunableKeyDialog.show(context, config.tunableId, -1, refresh);
            return true;
        });
        category.addPreference(addButton);
    }

    private static void refreshTunableKeys(Context context, PreferenceCategory category, Config config) {
        List<androidx.preference.Preference> toRemove = new ArrayList<>();
        for (int i = 0; i < category.getPreferenceCount(); i++) {
            androidx.preference.Preference p = category.getPreference(i);
            if (p instanceof TunableKeyPreference
                    || config.addButtonKey.equals(p.getKey())) {
                toRemove.add(p);
            }
        }
        for (androidx.preference.Preference p : toRemove) {
            category.removePreference(p);
        }
        addTunableKeySection(context, category, config);
    }
}
