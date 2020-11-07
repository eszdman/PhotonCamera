/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.eszdman.photoncamera.settings;

import java.util.HashMap;

/**
 * A class for storing default values and possible values of
 * SharedPreferences settings.  It is optional to store defaults
 * and possible values for a setting.  If a default is not specified,
 * the SettingsManager API chooses a default based on the type
 * requested:
 *
 * <ul>getString: default is null</ul>
 * <ul>getInteger: default is 0</ul>
 * <ul>getBoolean: default is false</ul>
 * <p>
 * If possible values aren't specified for a
 * SharedPreferences key, then calling getIndexOfCurrentValue
 * and setValueByIndex will throw an IllegalArgumentException.
 */
class DefaultsStore {
    /**
     * A class for storing a default value and set of possible
     * values.  Since all settings values are saved as Strings in
     * SharedPreferences, the default and possible values are
     * Strings.  This simplifies default values management.
     */
    private static class Defaults {
        private final String mDefaultValue;
        private final String[] mPossibleValues;

        public Defaults(String defaultValue, String[] possibleValues) {
            mDefaultValue = defaultValue;
            mPossibleValues = possibleValues;
        }

        public String getDefaultValue() {
            return mDefaultValue;
        }

        public String[] getPossibleValues() {
            return mPossibleValues;
        }
    }

    /**
     * Map of Defaults for SharedPreferences keys.
     */
    private static final HashMap<String, Defaults> mDefaultsInternalStore = new HashMap<>();

    /**
     * Store a default value and a set of possible values
     * for a SharedPreferences key.
     */
    public void storeDefaults(String key, String defaultValue, String[] possibleValues) {
        Defaults defaults = new Defaults(defaultValue, possibleValues);
        mDefaultsInternalStore.put(key, defaults);
    }

    /**
     * Get the default value for a SharedPreferences key,
     * if one has been stored.
     */
    public String getDefaultValue(String key) {
        Defaults defaults = mDefaultsInternalStore.get(key);
        if (defaults == null) {
            return null;
        }
        return defaults.getDefaultValue();
    }

    /**
     * Get the set of possible values for a SharedPreferences key,
     * if a set has been stored.
     */
    public String[] getPossibleValues(String key) {
        Defaults defaults = mDefaultsInternalStore.get(key);
        if (defaults == null) {
            return null;
        }
        return defaults.getPossibleValues();
    }
}