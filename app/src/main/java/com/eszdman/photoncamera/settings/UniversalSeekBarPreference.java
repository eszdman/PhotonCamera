package com.eszdman.photoncamera.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.eszdman.photoncamera.R;

import java.util.Locale;

/**
 * Created by vibhorSrv on 12/09/2020
 */
public class UniversalSeekBarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {
    private static final String TAG = "UnivSeekBarPref: ";
    private static final boolean isLoggingOn = true;
    private final float mMin, mMax;
    private final boolean isFloat, showSeekBarValue;
    private float mStepPerUnit;
    private int seekBarProgress;
    private TextView seekBarValue;
    private SeekBar seekBar;
    private String fallback_value;

    public UniversalSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.UniversalSeekBarPreference, defStyleAttr, defStyleRes);
        mMax = a.getFloat(R.styleable.UniversalSeekBarPreference_maxValue, 100.0f);
        mMin = a.getFloat(R.styleable.UniversalSeekBarPreference_minValue, 0.0f);
        mStepPerUnit = a.getFloat(R.styleable.UniversalSeekBarPreference_stepPerUnit, 1.0f);
        showSeekBarValue = a.getBoolean(R.styleable.UniversalSeekBarPreference_showSeekBarValue, true);
        isFloat = a.getBoolean(R.styleable.UniversalSeekBarPreference_isFloat, false);
        if (!isFloat && mStepPerUnit > 1)
            mStepPerUnit = 1.0f;
        a.recycle();
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
    public void onBindViewHolder(PreferenceViewHolder holder) {
//        log("onBindViewHolder");
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        seekBar = (SeekBar) holder.findViewById(R.id.seekbar);
        seekBarValue = (TextView) holder.findViewById(R.id.seekbar_value);
        seekBar.setMax((int) ((mMax - mMin) * mStepPerUnit));
        seekBar.setOnSeekBarChangeListener(this);
        set(convertToProgress(fallback_value));
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        set(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
//        log("onSetInitialValue : " + defaultValue);
        if (defaultValue == null) {
            defaultValue = fallback_value;
        }
        set(convertToProgress(defaultValue.toString()));
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        fallback_value = a.getString(index);
        log("onGetDefaultValue : " + fallback_value);
        return a.getString(index);
    }

    private void set(int progress) {
        seekBarProgress = progress;
        String valueToPersist = convertToValue(progress);
        updateLabel(valueToPersist);
        updateSeekbar(progress);
        persistString(valueToPersist);
        log("set : " + valueToPersist);
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
            seekBar.setProgress(progress);
    }

    private int convertToProgress(String defValue) {
        return (int) ((Float.parseFloat(getPersistedString(defValue)) - mMin) * mStepPerUnit);
    }

    private String convertToValue(int progress) {
        if (isFloat)
            return String.format(Locale.ROOT, "%.2f", (float) progress / mStepPerUnit + mMin);
        else
            return String.valueOf((int) ((float) progress / mStepPerUnit + mMin));
    }

    public String getValue() {
        return getPersistedString(fallback_value);
    }

    public int getSeekBarProgress() {
        return seekBarProgress;
    }

    public SeekBar getSeekBar() {
        return seekBar;
    }

}