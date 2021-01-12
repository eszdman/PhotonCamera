package com.eszdman.photoncamera.settings;

import android.content.SharedPreferences;

public class MigrationManager {
    private static final PreferenceKeys.Key KEY_PREFERENCES_VERSION = PreferenceKeys.Key.KEY_PREFERENCES_VERSION;
    private final static int PREFERENCES_VERSION = 2; //this value should be incremented whenever the preferences need to be reset
    public static boolean readAgain = false;

    public static void migrate(SettingsManager settingsManager) {
        checkPreferences(settingsManager);
    }

    private static void checkPreferences(SettingsManager settingsManager) {
        SharedPreferences defaultPreferences = settingsManager.getDefaultPreferences();
        SharedPreferences perLensPreferences = settingsManager.openPreferences(PreferenceKeys.Key.PER_LENS_FILE_NAME.mValue);

        int oldVersion = settingsManager.getInteger(SettingsManager.SCOPE_GLOBAL, KEY_PREFERENCES_VERSION, 1);

        if (oldVersion < PREFERENCES_VERSION) {
            defaultPreferences
                    .edit()
                    .clear()
                    .putString(KEY_PREFERENCES_VERSION.mValue, String.valueOf(PREFERENCES_VERSION))
                    .apply();
            perLensPreferences
                    .edit()
                    .clear()
                    .apply();
            readAgain = true;
        }
    }
}