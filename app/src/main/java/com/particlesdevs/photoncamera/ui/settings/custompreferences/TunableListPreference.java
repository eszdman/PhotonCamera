package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.MaterialColors;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.Log;

/**
 * List preference for string tunables that provide entries and entryValues.
 * Stores the selected entryValue as a string.
 * Features:
 * - Green color indicator when value differs from default
 * - Long press to reset to default
 */
public class TunableListPreference extends ListPreference {
    private static final String TAG = "TunableListPref";
    private String mDefaultValue = null;
    private TextView mTitleView = null;
    private boolean isUserInteraction = false;

    public TunableListPreference(Context context) {
        super(context);
        init();
    }

    public TunableListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TunableListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setIconSpaceReserved(false); // Don't reserve icon space
    }

    /**
     * Set the default entryValue
     */
    public void setDefaultValue(String defaultValue) {
        this.mDefaultValue = defaultValue;
        super.setDefaultValue(defaultValue);
        Log.d(TAG, "Set default value: " + defaultValue);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        // Get reference to title view for color indication
        mTitleView = (TextView) holder.findViewById(android.R.id.title);

        updateTitleColor();

        // Add long press listener to reset to default
        holder.itemView.setOnLongClickListener(v -> {
            resetToDefault();
            return true; // Consume the event
        });

        // After binding is complete, any changes are user interactions
        isUserInteraction = true;
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        // First time - use mDefaultValue but DON'T persist it yet.
        // This allows default changes in @Tunable annotations to take effect.
        // Value will only be persisted when user actually changes it.
        String value = getSafePersistedValue();
        setValue(value);
        Log.d(TAG, "onSetInitialValue - set value: " + value + " (default: " + mDefaultValue + ")");
    }

    @Override
    protected boolean persistString(String value) {
        // Only modify persistence during user interactions, not during initialization
        if (!isUserInteraction) {
            // During initialization - don't change persistence
            Log.d(TAG, "Init - not modifying persistence for " + getKey());
            updateTitleColor();
            return true;
        }

        // User is actively changing the value
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (TextUtils.equals(value, mDefaultValue)) {
            // User explicitly set it to match current default - remove persistence
            // This makes it white (not customized)
            if (prefs != null && prefs.contains(getKey())) {
                prefs.edit().remove(getKey()).apply();
                Log.d(TAG, "User set to default - removed persistence: " + value + " for " + getKey());
            }
            updateTitleColor();
            return true;
        }

        // User set it to differ from current default - persist it
        // This makes it green (customized) and it will stay green even if default changes later
        boolean result = super.persistString(value);
        updateTitleColor();
        Log.d(TAG, "User set to non-default - persisted: " + value + " (default: " + mDefaultValue + ") for " + getKey());
        return result;
    }

    /**
     * Safely reads the persisted value.
     * Catches ClassCastException when corrupted/legacy non-string values exist in SharedPreferences,
     * removes them and falls back to the default.
     */
    private String getSafePersistedValue() {
        SharedPreferences prefs = getPreferenceManager() != null ? getPreferenceManager().getSharedPreferences() : null;
        if (prefs == null || !prefs.contains(getKey())) {
            return mDefaultValue;
        }
        try {
            String value = prefs.getString(getKey(), mDefaultValue);
            return (value != null && !value.trim().isEmpty()) ? value : mDefaultValue;
        } catch (ClassCastException e) {
            Log.w(TAG, "Type mismatch for " + getKey() + ", healing corrupted preference: " + e.getMessage());
            prefs.edit().remove(getKey()).apply();
            return mDefaultValue;
        }
    }

    /**
     * Update title color based on whether user has customized this value.
     * Green = user explicitly set a non-default value (persisted)
     * White = never touched OR user explicitly set to match default (not persisted)
     *
     * Once green, stays green even if default later changes to match, unless user
     * explicitly changes it again or resets it.
     */
    private void updateTitleColor() {
        if (mTitleView == null) return;

        // Check if there's a persisted value (user set it to non-default at some point)
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        boolean hasPersisted = prefs != null && prefs.contains(getKey());
        int color = MaterialColors.getColor(getContext(), android.R.attr.textColorPrimary, 0xFFFFFF);
        // Green = persisted (user customized), White = not persisted (default)
        if (hasPersisted) {
            mTitleView.setTextColor(Color.parseColor("#4CAF50")); // Material Green for customized
        } else {
            mTitleView.setTextColor(color); // White for default
        }
    }

    /**
     * Reset the preference to its default value by removing the persisted value.
     * This allows the annotation default to be used.
     */
    private void resetToDefault() {
        // Remove the persisted value
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs != null) {
            prefs.edit().remove(getKey()).apply();
        }

        // Update UI to match default
        setValue(mDefaultValue);
        updateTitleColor();
        notifyChanged();

        // Show feedback
        PhotonCamera.showToast("Reset to default: " + getDefaultEntryLabel());
        Log.d(TAG, "Reset to default (removed persisted value): " + mDefaultValue + " for " + getKey());
    }

    private String getDefaultEntryLabel() {
        CharSequence[] entryValues = getEntryValues();
        CharSequence[] entries = getEntries();
        if (entryValues == null || entries == null) {
            return String.valueOf(mDefaultValue);
        }
        for (int i = 0; i < entryValues.length && i < entries.length; i++) {
            if (TextUtils.equals(entryValues[i].toString(), mDefaultValue)) {
                return entries[i].toString();
            }
        }
        return String.valueOf(mDefaultValue);
    }
}
