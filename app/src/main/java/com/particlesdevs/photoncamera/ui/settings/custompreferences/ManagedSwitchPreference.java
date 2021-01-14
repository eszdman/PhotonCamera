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
package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.SwitchPreferenceCompat;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.settings.SettingsManager;

/**
 * This class allows Settings UIs to display and set boolean values controlled
 * via the {@link SettingsManager}. The Default {@link SwitchPreferenceCompat} uses
 * {@link android.content.SharedPreferences} as a backing store; since the
 * {@link SettingsManager} stores all settings as Strings we need to ensure we
 * get and set boolean settings through the manager.
 */
public class ManagedSwitchPreference extends SwitchPreferenceCompat {
    private boolean fallback_value;

    public ManagedSwitchPreference(Context context) {
        super(context);
    }

    public ManagedSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ManagedSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean getPersistedBoolean(boolean defaultReturnValue) {
        if (PhotonCamera.getSettingsManager() != null)
            return PhotonCamera.getSettingsManager().getBoolean(SettingsManager.SCOPE_GLOBAL,getKey(), defaultReturnValue);
        else
            return defaultReturnValue;

    }

    @Override
    public boolean persistBoolean(boolean value) {
        if (PhotonCamera.getSettingsManager() != null) {
            PhotonCamera.getSettingsManager().set(SettingsManager.SCOPE_GLOBAL, getKey(), value);
            return true;
        } else
            return false;

    }

    private void set(boolean value) {
        setChecked(value);
        persistBoolean(value);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        if (defaultValue == null) {
            defaultValue = fallback_value;
        }
        set(getPersistedBoolean((Boolean) defaultValue));
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        fallback_value = a.getBoolean(index, false);
        return a.getBoolean(index, false);
    }
}