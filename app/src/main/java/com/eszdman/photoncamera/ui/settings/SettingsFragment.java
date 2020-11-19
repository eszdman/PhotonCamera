package com.eszdman.photoncamera.ui.settings;


import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.settings.PreferenceKeys;
import com.eszdman.photoncamera.settings.SettingsManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener, PreferenceManager.OnPreferenceTreeClickListener {
    private static final String KEY_MAIN_PARENT_SCREEN = "prefscreen";
    private Activity activity;
    private SettingsManager mSettingsManager;
    private Context mContext;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = getActivity();
        mSettingsManager = new SettingsManager(getContext());
        mContext = getContext();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        if (PreferenceKeys.isHdrXOn())
            removePreferenceFromScreen("pref_category_jpg");
        else
            removePreferenceFromScreen("pref_category_hdrx");
        setFramesSummary();
        setVersionDetails();
    }

    @Override
    public void onResume() {
        super.onResume();
        Toolbar toolbar = activity.findViewById(R.id.settings_toolbar);
        toolbar.setTitle(getPreferenceScreen().getTitle());
        setHdrxTitle();
        Preference myPref = findPreference(PreferenceKeys.Preference.KEY_TELEGRAM.mValue);
        if (myPref != null)
            myPref.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/photon_camera_channel"));
                startActivity(browserIntent);
                return true;
            });
        Preference github = findPreference(PreferenceKeys.Preference.KEY_CONTRIBUTORS.mValue);
        if (github != null)
            github.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/eszdman/PhotonCamera"));
                startActivity(browserIntent);
                return true;
            });
    }

    private void removePreferenceFromScreen(String preferenceKey) {
        PreferenceScreen parentScreen = findPreference(SettingsFragment.KEY_MAIN_PARENT_SCREEN);
        if (parentScreen != null)
            if (parentScreen.findPreference(preferenceKey) != null) {
                parentScreen.removePreference(Objects.requireNonNull(parentScreen.findPreference(preferenceKey)));
            }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PreferenceKeys.Preference.KEY_SAVE_PER_LENS_SETTINGS.mValue)) {
            setHdrxTitle();
            if (PreferenceKeys.isPerLensSettingsOn()) {
                PreferenceKeys.loadSettingsForCamera(PreferenceKeys.getCameraID());
                restartActivity();
            }
        }
        if (key.equalsIgnoreCase(PreferenceKeys.Preference.KEY_THEME.mValue)) {
            restartActivity();
        }
        if (key.equalsIgnoreCase(PreferenceKeys.Preference.KEY_THEME_ACCENT.mValue)) {
            restartActivity();
            SettingsActivity.toRestartApp = true;
        }
        if (key.equalsIgnoreCase(PreferenceKeys.Preference.KEY_FRAME_COUNT.mValue)) {
            setFramesSummary();
        }
    }

    private void setHdrxTitle() {
        Preference p = findPreference("pref_category_hdrx");
        if (p != null && getContext() != null) {
            if (PreferenceKeys.isPerLensSettingsOn()) {
                p.setTitle(getString(R.string.hdrx) + "\t(Lens: " + PreferenceKeys.getCameraID() + ')');
            } else {
                p.setTitle(getString(R.string.hdrx));
            }
        }
    }

    private void setFramesSummary() {
        Preference frameCountPreference = findPreference(PreferenceKeys.Preference.KEY_FRAME_COUNT.mValue);
        if (frameCountPreference != null && getContext() != null) {
            if (mSettingsManager.getInteger(PreferenceKeys.current_scope, PreferenceKeys.Preference.KEY_FRAME_COUNT) == 1) {
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
            startActivity(intent,
                    ActivityOptions.makeCustomAnimation(getContext(), R.anim.fade_in, R.anim.fade_out).toBundle());
        }
    }

    private void setVersionDetails() {
        Preference about = findPreference("pref_version_key");
        if (about != null && mContext != null) {
            try {
                PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
                String versionName = packageInfo.versionName;
                long versionCode = packageInfo.versionCode;

                Date date = new Date(packageInfo.lastUpdateTime);
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss z");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                about.setSummary(getString(R.string.version_summary, versionName + "." + versionCode, sdf.format(date)));

            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        return true;
    }


}
