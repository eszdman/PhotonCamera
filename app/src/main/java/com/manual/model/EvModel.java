package com.manual.model;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.capture.CaptureController;
import com.manual.*;

import java.util.ArrayList;
import java.util.Locale;

public class EvModel extends ManualModel<Float> {

    private final String TAG = EvModel.class.getSimpleName();
    private float evStep;

    public EvModel(Context context, Range range, ValueChangedEvent valueChangedEvent) {
        super(context, range, valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {
        Range<Float> evRange = range;
        if (evRange == null || (evRange.getLower() == 0.0f && evRange.getUpper() == 0.0f)) {
            Log.d(TAG, "onSetupIcons() - evRange is not valid.");
            return;
        }
        KnobItemInfo auto = getNewAutoItem(EV_AUTO, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;
        int positiveValueCount = 0;
        int negativeValueCount = 0;
        evStep = (CaptureController.mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue());
        float step = 0.25f;
        ArrayList<Float> values = new ArrayList<>();
        for (float fValue = evRange.getUpper(); fValue >= evRange.getLower(); fValue -= step) {
            float roundedValue = ((float) Math.round(10000.0f * fValue)) / 10000.0f;
            if (!isZero(fValue)) {
                if (fValue > 0.0f) {
                    positiveValueCount++;
                } else {
                    negativeValueCount++;
                }
            }
            values.add(roundedValue);
        }
        if (values.size() > 0) {
            values.set(values.size() - 1, evRange.getLower());
        }
        for (int tick = 0; tick < values.size(); tick++) {
            float value = values.get(tick);
            if (!isZero(value)) {
                ShadowTextDrawable drawable = new ShadowTextDrawable();
                drawable.setTextAppearance(context, R.style.ManualModeKnobText);
                ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
                drawableSelected.setTextAppearance(context, R.style.ManualModeKnobTextSelected);
                if (isInteger(value)) {
                    String valueStr = String.valueOf((int) value);
                    if (value > 0.0f) {
                        valueStr = "+" + valueStr;
                    }
                    drawable.setText(valueStr);
                    drawableSelected.setText(valueStr);
                }
                StateListDrawable stateDrawable = new StateListDrawable();
                stateDrawable.addState(new int[]{-android.R.attr.state_selected}, drawable);
                stateDrawable.addState(new int[]{android.R.attr.state_selected}, drawableSelected);
                String text = String.format(Locale.ROOT, "%.2f", value);
                if (value > 0.0f) {
                    getKnobInfoList().add(new KnobItemInfo(stateDrawable, text, positiveValueCount - tick, value));
                } else {
                    getKnobInfoList().add(new KnobItemInfo(stateDrawable, text, negativeValueCount - tick, value));
                }
            }
        }
        int angle = context.getResources().getInteger(R.integer.manual_ev_knob_view_angle_half);
        knobInfo = new KnobInfo(-angle, angle, -negativeValueCount, positiveValueCount, context.getResources().getInteger(R.integer.manual_ev_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo) {
        currentInfo = knobItemInfo;
        ParamController.setEV((int) (knobItemInfo.value / evStep));
        Log.d(TAG, "onSelectedKnobItemChanged() called with: knobItemInfo = [" + knobItemInfo + "]");
    }

    private boolean isZero(float value) {
        return ((double) Math.abs(value)) <= 0.001d;
    }

    private boolean isInteger(float value) {
        int checkNumber = ((int) (Math.abs(value) * 10000.0f)) % 10000;
        return checkNumber == 0 || checkNumber == 9999;
    }
}
