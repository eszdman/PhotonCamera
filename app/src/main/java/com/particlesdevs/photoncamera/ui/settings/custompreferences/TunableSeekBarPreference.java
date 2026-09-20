package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.particlesdevs.photoncamera.util.BlurSupport;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.Vibration;
import com.particlesdevs.photoncamera.util.Log;

import java.util.Locale;

/**
 * Seekbar preference that can be created programmatically.
 * Uses native float/int storage instead of strings for perfect precision.
 */
public class TunableSeekBarPreference extends Preference {
    private static final String TAG = "TunableSeekBarPref";
    private final Vibration vibration;
    private float mMin = 0.0f;
    private float mMax = 100.0f;
    private boolean isFloat = false;
    private float mStepPerUnit = 1.0f;
    private float mDefaultValue = 0.0f;
    private int seekBarProgress;
    private TextView seekBarValue;
    private Slider seekBar;
    private View seekBarReset;
    private boolean isUserInteraction = false;

    public TunableSeekBarPreference(Context context) {
        super(context);
        vibration = PhotonCamera.getVibration();
        setLayoutResource(R.layout.preference_tunable_seekbar); // Use custom wider layout
        setIconSpaceReserved(false); // Don't reserve icon space
    }

    public void setMinValue(float min) {
        this.mMin = min;
    }

    public void setMaxValue(float max) {
        this.mMax = max;
    }

    public void setIsFloat(boolean isFloat) {
        this.isFloat = isFloat;
    }

    public void setStepPerUnit(float stepPerUnit) {
        this.mStepPerUnit = stepPerUnit;
        if (!isFloat && mStepPerUnit > 1)
            mStepPerUnit = 1.0f;
    }
    
    public void setDefaultValue(float defaultValue) {
        this.mDefaultValue = defaultValue;
        Log.d(TAG, "Set default value: " + defaultValue);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        seekBar = (Slider) holder.findViewById(R.id.seekbar);
        seekBarValue = (TextView) holder.findViewById(R.id.seekbar_value);
        seekBarReset = holder.findViewById(R.id.seekbar_reset);

        if (seekBar != null) {
            seekBar.setValueFrom(0f);
            seekBar.setValueTo(progressMax());
            seekBar.setStepSize(1f);
            // Same index-domain pill issue as UniversalSeekBarPreference: map
            // the floating label to the real value shown in the value text.
            seekBar.setLabelFormatter(value -> formatValue(progressToValue(Math.round(value))));
            seekBar.clearOnChangeListeners();
            seekBar.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser && vibration != null) vibration.sliderTick();
                if (fromUser) set(Math.round(value));
            });

            // Get persisted value as appropriate type with auto-healing
            float currentValue = getSafePersistedValue();

            // Update UI only - don't persist again!
            seekBarProgress = valueToProgress(currentValue);
            String displayValue = formatValue(currentValue);
            if (seekBarValue != null) {
                seekBarValue.setText(displayValue);
                updateValueColor(currentValue);
            }
            seekBar.setValue(clampProgress(seekBarProgress));
        }
        
        // Also make the value text clickable
        if (seekBarValue != null) {
            seekBarValue.setOnClickListener(v -> showPreciseValueDialog());
        }

        // Reset button restores the default; visible only when non-default.
        if (seekBarReset != null) {
            seekBarReset.setOnClickListener(v -> resetToDefault());
        }
        updateResetVisibility();

        // After binding is complete, any changes are user interactions
        isUserInteraction = true;
    }
    
    private void showPreciseValueDialog() {
        Context context = getContext();
        if (context == null) return;
        
        // Get current value as native type for full precision
        float currentValue = getFloatValue();
        String currentValueText = isFloat ? 
            String.format(Locale.ROOT, "%.10f", currentValue).replaceAll("0+$", "").replaceAll("\\.$", "") :
            String.valueOf((int) currentValue);
        
        // Create input field
        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setHint("Value");
        final TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER |
            (isFloat ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0) |
            InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(currentValueText);
        input.setSelectAllOnFocus(true);
        inputLayout.addView(input);

        FrameLayout container = new FrameLayout(context);
        int horizontalPadding = (int) (24 * context.getResources().getDisplayMetrics().density);
        container.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        container.addView(inputLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(getTitle())
                .setMessage("Enter precise value (" +
                    String.format(Locale.ROOT, isFloat ? "%.10f" : "%.0f", mMin).replaceAll("0+$", "").replaceAll("\\.$", "") + " - " +
                    String.format(Locale.ROOT, isFloat ? "%.10f" : "%.0f", mMax).replaceAll("0+$", "").replaceAll("\\.$", "") +
                    ")\nDefault: " +
                    String.format(Locale.ROOT, isFloat ? "%.10f" : "%.0f", mDefaultValue).replaceAll("0+$", "").replaceAll("\\.$", ""))
                .setView(container)
                .setPositiveButton("Set", (d, which) -> {
                    try {
                        String valueStr = input.getText().toString();
                        float value = Float.parseFloat(valueStr);

                        // Clamp to min/max
                        if (value < mMin) {
                            value = mMin;
                            PhotonCamera.showToast("Value clamped to minimum: " + mMin);
                        } else if (value > mMax) {
                            value = mMax;
                            PhotonCamera.showToast("Value clamped to maximum: " + mMax);
                        }

                        // Just set the value - it will be persisted as native type
                        int progress = valueToProgress(value);
                        set(progress);

                        Log.d(TAG, "Set precise value: " + value + " for " + getKey());
                    } catch (NumberFormatException e) {
                        PhotonCamera.showToast("Invalid number format");
                        Log.w(TAG, "Invalid input: " + input.getText().toString());
                    }
                })
                .setNeutralButton("Reset", (d, which) -> {
                    // Temporarily disable user interaction flag to prevent re-persistence during UI updates
                    boolean wasUserInteraction = isUserInteraction;
                    isUserInteraction = false;

                    // Remove the persisted value to use annotation default
                    SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
                    if (prefs != null) {
                        prefs.edit().remove(getKey()).apply();
                    }

                    // Update UI to match default
                    seekBarProgress = valueToProgress(mDefaultValue);
                    String displayValue = formatValue(mDefaultValue);
                    if (seekBarValue != null) {
                        seekBarValue.setText(displayValue);
                    }
                    if (seekBar != null) {
                        updateSeekbar(seekBarProgress);
                    }
                    updateValueColor(mDefaultValue);
                    updateResetVisibility();

                    // Restore user interaction flag
                    isUserInteraction = wasUserInteraction;

                    PhotonCamera.showToast("Reset to default: " + mDefaultValue);
                    Log.d(TAG, "Reset to default (removed persisted value): " + mDefaultValue + " for " + getKey());
                })
                .setNegativeButton("Cancel", (d, which) -> d.cancel())
                .create();
        BlurSupport.show(dialog);

        // Request keyboard
        input.requestFocus();
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        // Check if value already persisted
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        boolean hasPersisted = prefs != null && prefs.contains(getKey());
        
        float currentValue;
        if (!hasPersisted) {
            // First time - use default but DON'T persist it yet
            // This allows default changes in @Tunable annotations to take effect
            // Value will only be persisted when user actually changes it
            currentValue = mDefaultValue;
            Log.d(TAG, "First init - using default (NOT persisting yet): " + mDefaultValue);
        } else {
            // Load existing persisted value with auto-healing
            currentValue = getSafePersistedValue();
            Log.d(TAG, "Loading persisted: " + currentValue);
        }
        
        // Update UI without re-persisting
        seekBarProgress = valueToProgress(currentValue);
        if (seekBarValue != null) {
            String displayValue = formatValue(currentValue);
            seekBarValue.setText(displayValue);
            updateValueColor(currentValue);
        }
        if (seekBar != null) {
            updateSeekbar(seekBarProgress);
        }
    }

    private void set(int progress) {
        seekBarProgress = progress;
        float value = progressToValue(progress);
        String displayValue = formatValue(value);
        
        updateLabel(displayValue);
        updateSeekbar(progress);
        
        // Only modify persistence during user interactions, not during initialization
        if (!isUserInteraction) {
            // During initialization - don't change persistence
            Log.d(TAG, "Init - not modifying persistence for " + getKey());
            updateValueColor(value);
            return;
        }
        
        // User is actively changing the value
        boolean matchesDefault = Math.abs(value - mDefaultValue) < 0.0001f;
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        
        if (matchesDefault) {
            // User explicitly set it to match current default - remove persistence
            // This makes it white (not customized)
            if (prefs != null && prefs.contains(getKey())) {
                prefs.edit().remove(getKey()).apply();
                Log.d(TAG, "User set to default - removed persistence: " + value + " for " + getKey());
            }
        } else {
            // User set it to differ from current default - persist it
            // This makes it green (customized) and it will stay green even if default changes later
            if (isFloat) {
                persistFloat(value);
            } else {
                persistInt((int) value);
            }
            Log.d(TAG, "User set to non-default - persisted: " + value + " (default: " + mDefaultValue + ") for " + getKey());
        }
        
        // Update color based on value
        updateValueColor(value);
        updateResetVisibility();
    }

    private void updateLabel(String displayValue) {
        if (seekBarValue != null) {
            seekBarValue.setVisibility(View.VISIBLE);
            seekBarValue.setText(displayValue);
        }
    }
    
    private void updateValueColor(float currentValue) {
        if (seekBarValue == null) return;
        
        // Check if there's a persisted value (user set it to non-default at some point)
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        boolean hasPersisted = prefs != null && prefs.contains(getKey());
        int color = MaterialColors.getColor(getContext(), android.R.attr.textColorPrimary, 0xFFFFFF);
        // Accent = persisted (user customized), on-surface = not persisted (default)
        if (hasPersisted) {
            seekBarValue.setTextColor(MaterialColors.getColor(seekBarValue, R.attr.colorPrimary, color));
        } else {
            seekBarValue.setTextColor(color);
        }

        Log.d(TAG, "Color: current=" + currentValue + ", default=" + mDefaultValue +
            ", persisted=" + hasPersisted + ", color=" + (hasPersisted ? "ACCENT" : "DEFAULT"));
    }

    private void updateSeekbar(int progress) {
        if (seekBar != null)
            seekBar.setValue(clampProgress(progress));
    }

    /** Restores the default value (same as Reset in the precise-input dialog). */
    private void resetToDefault() {
        boolean wasUserInteraction = isUserInteraction;
        isUserInteraction = true;
        try {
            SharedPreferences prefs = getPreferenceManager() != null
                    ? getPreferenceManager().getSharedPreferences() : null;
            if (prefs != null) {
                prefs.edit().remove(getKey()).apply();
            }
            seekBarProgress = valueToProgress(mDefaultValue);
            String displayValue = formatValue(mDefaultValue);
            updateLabel(displayValue);
            updateSeekbar(seekBarProgress);
            updateValueColor(mDefaultValue);
            updateResetVisibility();
        } finally {
            isUserInteraction = wasUserInteraction;
        }
    }

    /** Shows the reset button only when a non-default value is persisted. */
    private void updateResetVisibility() {
        if (seekBarReset == null) return;
        SharedPreferences prefs = getPreferenceManager() != null
                ? getPreferenceManager().getSharedPreferences() : null;
        boolean isDefault = prefs == null || !prefs.contains(getKey());
        seekBarReset.setVisibility(isDefault ? View.GONE : View.VISIBLE);
    }

    private int progressMax() {
        // Rounded, not truncated: float32 arithmetic lands just below exact
        // multiples (e.g. (1.0f - -0.2f) * 20 == 24.00000095 or 0.99999994),
        // and a negative min would otherwise lose a whole step.
        return Math.max(1, (int) Math.round(((double) mMax - (double) mMin) * (double) mStepPerUnit));
    }

    private int clampProgress(int progress) {
        if (progress < 0) return 0;
        return Math.min(progress, progressMax());
    }

    private int valueToProgress(float value) {
        double steps = ((double) value - (double) mMin) * (double) mStepPerUnit;
        return clampProgress((int) Math.round(steps));
    }
    
    private float progressToValue(int progress) {
        return (float) progress / mStepPerUnit + mMin;
    }

    private String formatValue(float value) {
        if (isFloat) {
            // For very small values < 0.01, use ellipsis format for display
            if (Math.abs(value) < 0.01f && Math.abs(value) > 0.0f) {
                String fullValue = String.format(Locale.ROOT, "%.10f", value);
                
                // Find position of first non-zero digit after decimal
                int firstNonZero = -1;
                boolean afterDecimal = false;
                for (int i = 0; i < fullValue.length(); i++) {
                    char c = fullValue.charAt(i);
                    if (c == '.') {
                        afterDecimal = true;
                        continue;
                    }
                    if (afterDecimal && c != '0') {
                        firstNonZero = i;
                        break;
                    }
                }
                
                if (firstNonZero > 4) {
                    // Show as "0.00...digits" format
                    String lastDigits = fullValue.substring(firstNonZero, Math.min(firstNonZero + 2, fullValue.length()));
                    return "0.00..." + lastDigits;
                }
            }
            
            return String.format(Locale.ROOT, "%.2f", value);
        } else {
            return String.valueOf((int) value);
        }
    }

    public float getFloatValue() {
        return getSafePersistedValue();
    }

    /**
     * Safely reads the persisted value with auto-healing capability.
     * Catches ClassCastException when corrupted/legacy string values exist in SharedPreferences,
     * converts them to the correct numeric type, repairs storage, and prevents crashes.
     */
    private float getSafePersistedValue() {
        SharedPreferences prefs = getPreferenceManager() != null ? getPreferenceManager().getSharedPreferences() : null;
        if (prefs == null || !prefs.contains(getKey())) {
            return mDefaultValue;
        }
        try {
            return isFloat ? getPersistedFloat(mDefaultValue) : (float) getPersistedInt((int) mDefaultValue);
        } catch (ClassCastException e) {
            Log.w(TAG, "Type mismatch for " + getKey() + ", healing corrupted preference: " + e.getMessage());
            try {
                String strVal = prefs.getString(getKey(), String.valueOf(mDefaultValue));
                float parsed = Float.parseFloat(strVal);
                // Heal: remove string, persist proper primitive type
                prefs.edit().remove(getKey()).apply();
                if (isFloat) {
                    persistFloat(parsed);
                } else {
                    persistInt((int) parsed);
                }
                return parsed;
            } catch (Exception parseException) {
                Log.e(TAG, "Failed to parse corrupted value for " + getKey() + ", resetting to default", parseException);
                prefs.edit().remove(getKey()).apply();
                return mDefaultValue;
            }
        }
    }
}
