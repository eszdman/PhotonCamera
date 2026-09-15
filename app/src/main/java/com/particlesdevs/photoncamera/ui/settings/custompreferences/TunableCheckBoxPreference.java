package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.color.MaterialColors;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.Log;

/**
 * Checkbox preference for tunable parameters with min=0, max=1, step=1.
 * Stores values as int (0 or 1) to match the tunable system's numeric storage pattern.
 * Features:
 * - Green color indicator when value differs from default
 * - Long press to reset to default
 */
public class TunableCheckBoxPreference extends SwitchPreferenceCompat {
    private static final String TAG = "TunableCheckBoxPref";
    private int mDefaultValue = 0;
    private TextView mTitleView = null;
    private boolean isUserInteraction = false;

    public TunableCheckBoxPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.preference_tunable_checkbox); // Use custom layout with reduced margin
        setIconSpaceReserved(false); // Don't reserve icon space
    }

    public TunableCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_tunable_checkbox); // Use custom layout with reduced margin
        setIconSpaceReserved(false); // Don't reserve icon space
    }

    public TunableCheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_tunable_checkbox); // Use custom layout with reduced margin
        setIconSpaceReserved(false); // Don't reserve icon space
    }

    /**
     * Set the default value (0 or 1)
     */
    public void setDefaultValue(int defaultValue) {
        this.mDefaultValue = defaultValue;
        // Also set the parent's boolean default value
        super.setDefaultValue(defaultValue != 0);
        Log.d(TAG, "Set default value: " + defaultValue + " (boolean: " + (defaultValue != 0) + ")");
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        
        // Get reference to title view for color indication
        mTitleView = (TextView) holder.findViewById(android.R.id.title);
        
        // Ensure checkbox state is correct based on persisted int value
        int currentValue = getPersistedInt(mDefaultValue);
        setChecked(currentValue != 0);
        
        // Update color based on whether value is default or customized
        updateTitleColor(currentValue);
        
        // Add long press listener to reset to default
        holder.itemView.setOnLongClickListener(v -> {
            resetToDefault();
            return true; // Consume the event
        });
        
        // After binding is complete, any changes are user interactions
        isUserInteraction = true;
        
        Log.d(TAG, "onBindViewHolder - set checked: " + (currentValue != 0) + " (value: " + currentValue + ", default: " + mDefaultValue + ")");
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        // Check if value already persisted
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        boolean hasPersisted = prefs != null && prefs.contains(getKey());
        
        int currentValue;
        if (!hasPersisted) {
            // First time - use mDefaultValue but DON'T persist it yet
            // This allows default changes in @Tunable annotations to take effect
            // Value will only be persisted when user actually changes it
            if (defaultValue != null) {
                // defaultValue might be Boolean from parent class
                if (defaultValue instanceof Boolean) {
                    currentValue = ((Boolean) defaultValue) ? 1 : 0;
                } else if (defaultValue instanceof Integer) {
                    currentValue = (Integer) defaultValue;
                } else {
                    currentValue = mDefaultValue;
                }
            } else {
                currentValue = mDefaultValue;
            }
            Log.d(TAG, "First init - using default (NOT persisting yet): " + currentValue + " (from mDefaultValue: " + mDefaultValue + ")");
        } else {
            // Load existing persisted value
            currentValue = getPersistedInt(mDefaultValue);
            Log.d(TAG, "Loading persisted: " + currentValue);
        }
        
        // Update UI without re-persisting
        setChecked(currentValue != 0);
        Log.d(TAG, "onSetInitialValue - set checked: " + (currentValue != 0) + " (value: " + currentValue + ", default: " + mDefaultValue + ")");
    }

    @Override
    protected boolean persistBoolean(boolean value) {
        // Convert boolean to int (0 or 1)
        int intValue = value ? 1 : 0;
        
        // Only modify persistence during user interactions, not during initialization
        if (!isUserInteraction) {
            // During initialization - don't change persistence
            Log.d(TAG, "Init - not modifying persistence for " + getKey());
            updateTitleColor(intValue);
            return true;
        }
        
        // User is actively changing the value
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        boolean result = true;
        
        if (intValue == mDefaultValue) {
            // User explicitly set it to match current default - remove persistence
            // This makes it white (not customized)
            if (prefs != null && prefs.contains(getKey())) {
                prefs.edit().remove(getKey()).apply();
                Log.d(TAG, "User set to default - removed persistence: " + intValue + " for " + getKey());
            }
        } else {
            // User set it to differ from current default - persist it
            // This makes it green (customized) and it will stay green even if default changes later
            result = persistInt(intValue);
            Log.d(TAG, "User set to non-default - persisted: " + intValue + " (default: " + mDefaultValue + ") for " + getKey());
        }
        
        // Update color after value change
        updateTitleColor(intValue);
        
        return result;
    }

    @Override
    protected boolean getPersistedBoolean(boolean defaultReturnValue) {
        // Get persisted int value and convert to boolean
        int intDefault = defaultReturnValue ? 1 : 0;
        int persistedValue = getPersistedInt(intDefault);
        return persistedValue != 0;
    }

    /**
     * Get the current value as int (0 or 1)
     */
    public int getIntValue() {
        return getPersistedInt(mDefaultValue);
    }
    
    /**
     * Update title color based on whether user has customized this value.
     * Green = user explicitly set a non-default value (persisted)
     * White = never touched OR user explicitly set to match default (not persisted)
     * 
     * Once green, stays green even if default later changes to match, unless user
     * explicitly changes it again or resets it.
     */
    private void updateTitleColor(int currentValue) {
        if (mTitleView == null) return;
        
        // Check if there's a persisted value (user set it to non-default at some point)
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        boolean hasPersisted = prefs != null && prefs.contains(getKey());
        int color = MaterialColors.getColor(getContext(), android.R.attr.textColorPrimary, 0xFFFFFF);
        // Accent = persisted (user customized), on-surface = not persisted (default)
        if (hasPersisted) {
            mTitleView.setTextColor(MaterialColors.getColor(mTitleView, R.attr.colorPrimary, color));
        } else {
            mTitleView.setTextColor(color);
        }

        Log.d(TAG, "Color: current=" + currentValue + ", default=" + mDefaultValue +
            ", persisted=" + hasPersisted + ", color=" + (hasPersisted ? "ACCENT" : "DEFAULT"));
    }
    
    /**
     * Reset the preference to its default value by removing the persisted value.
     * This allows the annotation default to be used.
     */
    private void resetToDefault() {
        // Temporarily disable user interaction flag to prevent re-persistence during setChecked
        boolean wasUserInteraction = isUserInteraction;
        isUserInteraction = false;
        
        // Remove the persisted value
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs != null) {
            prefs.edit().remove(getKey()).apply();
        }
        
        // Update UI to match default
        setChecked(mDefaultValue != 0);
        updateTitleColor(mDefaultValue);
        
        // Restore user interaction flag
        isUserInteraction = wasUserInteraction;
        
        // Show feedback
        PhotonCamera.showToast("Reset to default: " + (mDefaultValue != 0 ? "enabled" : "disabled"));
        Log.d(TAG, "Reset to default (removed persisted value): " + mDefaultValue + " for " + getKey());
    }
}

