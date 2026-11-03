package com.particlesdevs.photoncamera.ui.settings.custompreferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputType;
import android.util.AttributeSet;
import com.particlesdevs.photoncamera.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.particlesdevs.photoncamera.util.BlurSupport;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.Vibration;

import java.util.Locale;

/**
 * Created by vibhorSrv on 12/09/2020
 *
 * Value <-> progress conversion notes:
 *  - The persisted representation is a String. All arithmetic that maps that String onto a
 *    SeekBar index is done in double precision and closed with Math.round(), never with a
 *    (int) truncation. A float32 truncation loses a whole step whenever the value cannot be
 *    represented exactly, e.g. (-0.15f - -0.2f) * 20 == 0.99999994 -> 0 instead of 1.
 *  - Binding the view never rewrites the stored value. Only explicit user interaction
 *    (dragging the bar, or confirming the precise-input dialog) persists anything, so a value
 *    that sits between two steps survives reopening the settings screen.
 */
public class UniversalSeekBarPreference extends Preference {
    private static final String TAG = "UnivSeekBarPref";
    private static final boolean isLoggingOn = false;
    /** Sentinel used to detect "nothing persisted yet" without touching the store. */
    private static final String NOT_PERSISTED = "\u0000__np__";
    private final Vibration vibration;
    private final float mMin, mMax;
    private final boolean isFloat, showSeekBarValue;
    private float mStepPerUnit;
    private final int mSeekBarMax;
    private int seekBarProgress;
    private TextView seekBarValue;
    private Slider seekBar;
    private android.view.View seekBarReset;
    private String fallback_value;

    public UniversalSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.UniversalSeekBarPreference, defStyleAttr, defStyleRes);
        vibration = PhotonCamera.getVibration();
        mMax = a.getFloat(R.styleable.UniversalSeekBarPreference_maxValue, 100.0f);
        mMin = a.getFloat(R.styleable.UniversalSeekBarPreference_minValue, 0.0f);
        mStepPerUnit = a.getFloat(R.styleable.UniversalSeekBarPreference_stepPerUnit, 1.0f);
        showSeekBarValue = a.getBoolean(R.styleable.UniversalSeekBarPreference_showSeekBarValue, true);
        isFloat = a.getBoolean(R.styleable.UniversalSeekBarPreference_isFloat, false);
        if (!isFloat && mStepPerUnit > 1)
            mStepPerUnit = 1.0f;
        a.recycle();
        // Rounded, not truncated: (1.0f - -0.2f) * 20 evaluates to 24.00000095 in float32,
        // and a range like 0.3f * 10 would truncate to 2 instead of 3.
        mSeekBarMax = Math.max(1, (int) Math.round(((double) mMax - (double) mMin) * (double) mStepPerUnit));
    }

    public UniversalSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public UniversalSeekBarPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UniversalSeekBarPreference(Context context) {
        this(context, null);
    }

    private void log(String msg) {
        if (isLoggingOn)
            Log.d(TAG + getKey(), msg);
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
            seekBar.setValueTo(mSeekBarMax);
            seekBar.setStepSize(1f);
            // The Slider runs in progress-index domain (0..mSeekBarMax), so the
            // default floating pill would show the raw index. Map it to the
            // real value, identical to what dragging persists and labels.
            seekBar.setLabelFormatter(value -> convertToValue(Math.round(value)));
            seekBar.clearOnChangeListeners();
            seekBar.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    vibration.sliderTick();
                    set(Math.round(value));
                }
            });
        }
        // Read-only refresh: must not quantize or rewrite what is already stored.
        showStoredValue();

        // Add click listener for precise value input on the value text
        if (seekBarValue != null) {
            seekBarValue.setOnClickListener(v -> showPreciseValueDialog());
        }

        // Reset button restores the default; visible only when non-default.
        if (seekBarReset != null) {
            seekBarReset.setOnClickListener(v -> resetToDefault());
        }
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        if (defaultValue == null) {
            defaultValue = fallback_value;
        }
        // First run only: seed the store so backups/exports contain the key.
        // Afterwards this is a pure refresh, so an off-grid value is preserved.
        if (NOT_PERSISTED.equals(getPersistedString(NOT_PERSISTED))) {
            float seed = clamp(parseValue(defaultValue.toString(), mMin));
            String seedText = isFloat ? formatGridValue(seed) : formatExactValue(seed);
            seekBarProgress = valueToProgress(seed);
            updateLabel(seedText);
            updateSeekbar(seekBarProgress);
            persistString(seedText);
            log("onSetInitialValue (seed) : " + seedText);
        } else {
            showStoredValue();
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        fallback_value = a.getString(index);
        log("onGetDefaultValue : " + fallback_value);
        return a.getString(index);
    }

    /**
     * Refreshes bar + label from the persisted String without writing anything back.
     * Out-of-range leftovers are the one exception: those are clamped and rewritten.
     */
    private void showStoredValue() {
        String stored = getPersistedString(fallback_value == null ? "0" : fallback_value);
        float raw = parseValue(stored, parseValue(fallback_value, mMin));
        float clamped = clamp(raw);
        seekBarProgress = valueToProgress(clamped);
        updateSeekbar(seekBarProgress);
        if (clamped != raw) {
            String fixed = formatExactValue(clamped);
            updateLabel(fixed);
            persistString(fixed);
            log("showStoredValue (clamped) : " + fixed);
        } else {
            // Show exactly what is stored, so a precise off-grid value stays visible.
            updateLabel(stored);
            log("showStoredValue : " + stored);
        }
        updateResetVisibility();
    }

    /** User dragged the bar: the value snaps to the step grid and is persisted. */
    private void set(int progress) {
        seekBarProgress = clampProgress(progress);
        String valueToPersist = convertToValue(seekBarProgress);
        updateLabel(valueToPersist);
        updateSeekbar(seekBarProgress);
        persistString(valueToPersist);
        updateResetVisibility();
        log("set : " + valueToPersist);
    }

    /** Manual input: keeps the exact value, the bar only shows the nearest step. */
    private void setDirectValue(float value) {
        float clamped = clamp(value);
        String valueToPersist = formatExactValue(clamped);
        seekBarProgress = valueToProgress(clamped);

        updateLabel(valueToPersist);
        updateSeekbar(seekBarProgress);
        persistString(valueToPersist);
        updateResetVisibility();
        log("setDirectValue : " + valueToPersist);
    }

    /** Restores the default value (same as Reset in the precise-input dialog). */
    private void resetToDefault() {
        setDirectValue(clamp(parseValue(fallback_value, mMin)));
    }

    /** Shows the reset button only when the stored value differs from default. */
    private void updateResetVisibility() {
        if (seekBarReset == null) return;
        float def = clamp(parseValue(fallback_value, mMin));
        String stored = getPersistedString(fallback_value == null ? "0" : fallback_value);
        float cur = clamp(parseValue(stored, parseValue(fallback_value, mMin)));
        boolean isDefault = Math.abs(cur - def) < 0.0005f;
        seekBarReset.setVisibility(isDefault ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void updateLabel(String valueToPersist) {
        if (seekBarValue != null) {
            if (showSeekBarValue) {
                seekBarValue.setVisibility(View.VISIBLE);
                seekBarValue.setText(valueToPersist);
            } else
                seekBarValue.setVisibility(View.GONE);
        }
    }

    private void updateSeekbar(int progress) {
        if (seekBar != null)
            seekBar.setValue(clampProgress(progress));
    }

    private float clamp(float value) {
        if (value < mMin) return mMin;
        if (value > mMax) return mMax;
        return value;
    }

    private int clampProgress(int progress) {
        if (progress < 0) return 0;
        return Math.min(progress, mSeekBarMax);
    }

    private float parseValue(String text, float fallback) {
        if (text == null) return fallback;
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            Log.w(TAG, "Unparsable stored value '" + text + "' for " + getKey());
            return fallback;
        }
    }

    /**
     * Maps a value onto the nearest step index. Double precision plus Math.round, because
     * float32 subtraction of mMin lands just below the exact multiple for most decimals.
     */
    private int valueToProgress(float value) {
        double steps = ((double) value - (double) mMin) * (double) mStepPerUnit;
        return clampProgress((int) Math.round(steps));
    }

    private String convertToValue(int progress) {
        double value = (double) progress / (double) mStepPerUnit + (double) mMin;
        if (isFloat)
            return String.format(Locale.ROOT, "%.2f", value);
        else
            return String.valueOf((int) Math.round(value));
    }

    /** Trimmed representation used for off-grid values and for dialog hints. */
    private String formatExactValue(float value) {
        if (!isFloat)
            return String.valueOf(Math.round(value));
        String s = String.format(Locale.ROOT, "%.6f", value);
        if (s.indexOf('.') >= 0) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') end--;
            if (end > 0 && s.charAt(end - 1) == '.') end--;
            s = s.substring(0, end);
        }
        return s.isEmpty() ? "0" : s;
    }

    /** Grid representation, identical to what dragging the bar produces. */
    private String formatGridValue(float value) {
        return isFloat ? String.format(Locale.ROOT, "%.2f", value) : String.valueOf(Math.round(value));
    }

    public String getValue() {
        return getPersistedString(fallback_value);
    }

    public int getSeekBarProgress() {
        return seekBarProgress;
    }

    public Slider getSeekBar() {
        return seekBar;
    }

    private void showPreciseValueDialog() {
        Context context = getContext();
        if (context == null) return;

        float currentValue = clamp(parseValue(getPersistedString(fallback_value), parseValue(fallback_value, mMin)));
        String currentValueText = formatExactValue(currentValue);
        float defaultValue = clamp(parseValue(fallback_value, mMin));

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
                        formatExactValue(mMin) + " - " + formatExactValue(mMax) +
                        ")\nDefault: " + formatExactValue(defaultValue))
                .setView(container)
                .setPositiveButton("Set", (d, which) -> {
                    try {
                        String valueStr = input.getText().toString();
                        float value = Float.parseFloat(valueStr.trim());

                        // Clamp to min/max
                        if (value < mMin) {
                            value = mMin;
                            PhotonCamera.showToast("Value clamped to minimum: " + formatExactValue(mMin));
                        } else if (value > mMax) {
                            value = mMax;
                            PhotonCamera.showToast("Value clamped to maximum: " + formatExactValue(mMax));
                        }

                        // Set the exact value directly - bypasses step quantization
                        setDirectValue(value);

                        Log.d(TAG, "Set precise value: " + value + " for " + getKey());
                    } catch (NumberFormatException e) {
                        PhotonCamera.showToast("Invalid number format");
                        Log.w(TAG, "Invalid input: " + input.getText().toString());
                    }
                })
                .setNeutralButton("Reset", (d, which) -> {
                    // Reset to exact default value - preserves precision
                    setDirectValue(defaultValue);
                    PhotonCamera.showToast("Reset to default: " + formatExactValue(defaultValue));
                    Log.d(TAG, "Reset to default: " + defaultValue + " for " + getKey());
                })
                .setNegativeButton("Cancel", (d, which) -> d.cancel())
                .create();
        BlurSupport.show(dialog);

        // Request keyboard
        input.requestFocus();
    }

}