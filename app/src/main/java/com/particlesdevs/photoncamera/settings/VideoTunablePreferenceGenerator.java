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
 * Builds the preferences for the video-only tunable-key lists (SDR and HDR),
 * scoped per resolution and facing via {@link VideoResolutionScope}. Mirrors the
 * sensor-config tunable section ({@link SensorConfigPreferenceGenerator}) but
 * uses the video ids so the keys apply in video mode only, regardless of sensor.
 */
public final class VideoTunablePreferenceGenerator {
    private static final String TAG = "VideoTunablePrefs";

    /** Config for one tunable list (SDR or HDR). */
    public static final class Config {
        /** True for the HDR list. */
        public final boolean hdr;
        /** Legacy global id, used to seed a fresh scope. */
        public final String legacyTunableId;
        public final String categoryKey;
        public final String addButtonKey;
        public final int titleRes;
        public final int summaryRes;
        public final int addSummaryRes;

        public Config(boolean hdr, String legacyTunableId, String categoryKey, String addButtonKey,
                int titleRes, int summaryRes, int addSummaryRes) {
            this.hdr = hdr;
            this.legacyTunableId = legacyTunableId;
            this.categoryKey = categoryKey;
            this.addButtonKey = addButtonKey;
            this.titleRes = titleRes;
            this.summaryRes = summaryRes;
            this.addSummaryRes = addSummaryRes;
        }
    }

    public static final Config SDR = new Config(
            false,
            TunableKeyManager.VIDEO_TUNABLE_ID,
            "pref_category_video_tunablekeys",
            "pref_video_add_tunablekey",
            R.string.video_tunable_keys_sdr,
            R.string.video_tunable_summary_sdr,
            R.string.video_tunable_add_summary_sdr);

    public static final Config HDR = new Config(
            true,
            TunableKeyManager.VIDEO_HDR_TUNABLE_ID,
            "pref_category_video_hdr_tunablekeys",
            "pref_video_hdr_add_tunablekey",
            R.string.video_tunable_keys_hdr,
            R.string.video_tunable_summary_hdr,
            R.string.video_tunable_add_summary_hdr);

    private VideoTunablePreferenceGenerator() {}

    public static void generatePreferences(Context context, PreferenceScreen screen, Config config) {
        generatePreferences(context, screen, config, false, null);
    }

    /**
     * Generates the list for one scope. The scope's list is seeded from the
     * legacy global list the first time it is shown, so the UI and the capture
     * path agree on the keys that apply until the scope is edited.
     *
     * @param selfie     true for the front (selfie) camera's scope
     * @param resolution the scope's video resolution value
     */
    public static void generatePreferences(Context context, PreferenceScreen screen, Config config,
            boolean selfie, String resolution) {
        try {
            TunableKeyManager.seedVideoScope(context, config.hdr, selfie, resolution);
            String tunableId = VideoResolutionScope.tunableId(config.hdr, selfie, resolution);
            String scope = VideoResolutionScope.label(resolution) + " \u00B7 "
                    + context.getString(selfie ? R.string.video_scope_selfie : R.string.video_scope_back);

            PreferenceCategory category = new PreferenceCategory(context);
            category.setKey(config.categoryKey);
            category.setTitle(context.getString(config.titleRes) + " \u00B7 " + scope);
            category.setSummary(context.getString(config.summaryRes));
            category.setLayoutResource(R.layout.preference_category_layout);
            screen.addPreference(category);
            addTunableKeySection(context, category, config, tunableId, scope);
        } catch (Exception e) {
            Log.w(TAG, "Failed to generate video tunable preferences: " + Log.getStackTraceString(e));
        }
    }

    private static void addTunableKeySection(Context context, PreferenceCategory category,
            Config config, String tunableId, String scope) {
        Runnable refresh = () -> refreshTunableKeys(context, category, config, tunableId, scope);

        List<VendorTagUtils.TunableKey> keys =
                TunableKeyManager.loadKeys(context, tunableId);
        for (int i = 0; i < keys.size(); i++) {
            TunableKeyPreference keyPref = new TunableKeyPreference(
                    context, tunableId, i, refresh);
            keyPref.setOrder(100 + i);
            // TunableKeyPreference loads keys via loadKeys() with the scoped id.
            category.addPreference(keyPref);
        }

        androidx.preference.Preference addButton = new androidx.preference.Preference(context);
        addButton.setKey(config.addButtonKey);
        addButton.setLayoutResource(R.layout.preference_with_margin);
        addButton.setTitle("+ Add Tunable Key");
        addButton.setSummary(context.getString(config.addSummaryRes) + " \u00B7 " + scope);
        addButton.setIcon(R.drawable.ic_add);
        addButton.setOrder(10000);
        addButton.setOnPreferenceClickListener(preference -> {
            TunableKeyDialog.show(context, tunableId, -1, refresh);
            return true;
        });
        category.addPreference(addButton);
    }

    private static void refreshTunableKeys(Context context, PreferenceCategory category,
            Config config, String tunableId, String scope) {
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
        addTunableKeySection(context, category, config, tunableId, scope);
    }
}
