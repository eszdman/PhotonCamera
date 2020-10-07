package com.eszdman.photoncamera.ui.settings;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.*;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.settings.SettingsManager;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, PreferenceManager.OnPreferenceTreeClickListener {
    private static final String KEY_MAIN_PARENT_SCREEN = "prefscreen";
    private Activity activity;
    private SettingsManager mSettingsManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
        mSettingsManager = new SettingsManager(getContext());
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        if (PreferenceKeys.isHdrXOn())
            removePreferenceFromScreen("pref_category_jpg", KEY_MAIN_PARENT_SCREEN);
        else
            removePreferenceFromScreen("pref_category_hdrx", KEY_MAIN_PARENT_SCREEN);

        Preference hide = findPreference(PreferenceKeys.KEY_SAVE_PER_LENS_SETTINGS);
        PreferenceCategory category = findPreference("pref_category_general");
        if (category != null && hide != null) {
                category.removePreference(hide);
        }
        setFramesSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        Toolbar toolbar = activity.findViewById(R.id.settings_toolbar);
        toolbar.setTitle(getPreferenceScreen().getTitle());
        Preference myPref = findPreference(PreferenceKeys.KEY_TELEGRAM);
        if (myPref != null)
            myPref.setOnPreferenceClickListener(preference -> {
                Intent browserint = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/photon_camera_channel"));
                startActivity(browserint);
                return true;
            });
        Preference github = findPreference(PreferenceKeys.KEY_CONTRIBUTORS);
        if (github != null)
            github.setOnPreferenceClickListener(preference -> {
                Intent browserint = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/eszdman/PhotonCamera"));
                startActivity(browserint);
                return true;
            });
    }

    private void removePreferenceFromScreen(String preferenceKey, String parentScreenKey) {
        PreferenceScreen parentScreen = findPreference(parentScreenKey);
        if (parentScreen != null)
            if (parentScreen.findPreference(preferenceKey) != null) {
                parentScreen.removePreference(parentScreen.findPreference(preferenceKey));
            }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equalsIgnoreCase(PreferenceKeys.KEY_THEME)) {
            restartActivity();
        }
        if (key.equalsIgnoreCase(PreferenceKeys.KEY_THEME_ACCENT)) {
            restartActivity();
            SettingsActivity.toRestartApp = true;
        }
        if (key.equalsIgnoreCase(PreferenceKeys.KEY_FRAME_COUNT)) {
            setFramesSummary();
        }
    }

    private void setFramesSummary() {
        Preference frameCountPreference = findPreference(PreferenceKeys.KEY_FRAME_COUNT);
        if (frameCountPreference != null && getContext() != null) {
            if (mSettingsManager.getInteger(PreferenceKeys.current_scope, PreferenceKeys.KEY_FRAME_COUNT) == 1) {
                frameCountPreference.setSummary(getString(R.string.unprocessed_raw));
            } else {
                frameCountPreference.setSummary(getString(R.string.frame_count_summary));
            }
        }
    }

    private void restartActivity() {
        if (getActivity() != null) {
            Intent intent = new Intent(getContext(), getActivity().getClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        return true;
    }


}
