package com.eszdman.photoncamera.settings;

import android.content.SharedPreferences;

public class MigrationManager {
    private static final PreferenceKeys.Key KEY_PREF_VERSION = PreferenceKeys.Key.KEY_PREF_VERSION;
    private final static int PREFERENCES_VERSION = 2; //this value should be incremented whenever the preferences need to be reset
    public static boolean readAgain = false;

    public static void migrate(SettingsManager settingsManager) {
        checkPreferences(settingsManager);
    }

    private static void checkPreferences(SettingsManager settingsManager) {
        int oldVersion = settingsManager.getInteger(KEY_PREF_VERSION.mValue, KEY_PREF_VERSION, 1);

        if (oldVersion < PREFERENCES_VERSION) {
            SharedPreferences defaultPreferences = settingsManager.getDefaultPreferences();
            SharedPreferences perLensPreferences = settingsManager.openPreferences(PreferenceKeys.Key.PER_LENS_FILE_NAME.mValue);
            SharedPreferences camerasPreference = settingsManager.openPreferences(PreferenceKeys.Key.CAMERAS_PREFERENCE_FILE_NAME.mValue);

            defaultPreferences.edit().clear().apply();
            perLensPreferences.edit().clear().apply();
            camerasPreference.edit().clear().apply();
            settingsManager.set(KEY_PREF_VERSION.mValue, KEY_PREF_VERSION, PREFERENCES_VERSION);
            readAgain = true;
        }
    }
}