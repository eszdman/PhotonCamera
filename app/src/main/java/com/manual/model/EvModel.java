package com.manual.model;

import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Range;

import android.widget.TextView;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;

public class EvModel extends ManualModel<Float> {

    private final String TAG = EvModel.class.getSimpleName();

    public EvModel(Range range, ValueChangedEvent valueChangedEvent) {
        super(range,valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {
        Range<Float> evRange = range;
        if (evRange == null || (evRange.getLower() == 0.0f && evRange.getUpper() == 0.0f)) {
            Log.d(TAG, "onSetupIcons() - evRange is not valid.");
            return;
        }
        KnobItemInfo auto = getNewAutoItem(0);
        getKnobInfoList().add(auto);
        currentInfo = auto;
        int positiveValueCount = 0;
        int negtiveValueCount = 0;
        float evStep = (CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue());
        ArrayList<Float> arrayList2 = new ArrayList<>();
        for (float fValue = evRange.getUpper(); fValue >= evRange.getLower(); fValue -= evStep) {
            float roundedValue = ((float) Math.round(10000.0f * fValue)) / 10000.0f;
            if (!isZero(fValue)) {
                if (fValue > 0.0f) {
                    positiveValueCount++;
                } else {
                    negtiveValueCount++;
                }
            }
            arrayList2.add(roundedValue);
        }
        if (arrayList2.size() > 0) {
            arrayList2.set(arrayList2.size() - 1, evRange.getLower());
        }
        for (int i = 0; i < arrayList2.size(); i++) {
            float value = arrayList2.get(i);
            if (!isZero(value)) {
                ShadowTextDrawable drawable = new ShadowTextDrawable();
                drawable.setTextAppearance(Interface.i.mainActivity, R.style.ManualModeKnobText);
                ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
                drawableSelected.setTextAppearance(Interface.i.mainActivity, R.style.ManualModeKnobTextSelected);
                if (isInteger(value)) {
                    String valueStr = String.valueOf((int) value);
                    if (value > 0.0f) {
                        valueStr = "+" + valueStr;
                    }
                    drawable.setText(valueStr);
                    drawableSelected.setText(valueStr);
                }
                StateListDrawable stateDrawable = new StateListDrawable();
                stateDrawable.addState(new int[]{-16842913}, drawable);
                stateDrawable.addState(new int[]{-16842913}, drawableSelected);
                String text = String.format("%.2f", value);
                if (value > 0.0f) {
                    getKnobInfoList().add(new KnobItemInfo(stateDrawable, text, positiveValueCount - i, value));
                } else {
                    getKnobInfoList().add(new KnobItemInfo(stateDrawable, text, negtiveValueCount - i, value));
                }
            }
        }
        int angle = Interface.i.mainActivity.getResources().getInteger(R.integer.manual_ev_knob_view_angle_half);
        knobInfo = new KnobInfo(-angle, angle, -negtiveValueCount, positiveValueCount, Interface.i.mainActivity.getResources().getInteger(R.integer.manual_ev_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo2) {
        currentInfo = knobItemInfo2;
        CaptureRequest.Builder builder = Interface.i.camera.mPreviewRequestBuilder;
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (int) knobItemInfo2.value);
        Interface.i.camera.rebuildPreviewBuilder();
        //fireValueChangedEvent(knobItemInfo2.text);
    }

    private boolean isZero(float value) {
        return ((double) Math.abs(value)) <= 0.001d;
    }

    private boolean isInteger(float value) {
        int checkNumber = ((int) (Math.abs(value) * 10000.0f)) % 10000;
        return checkNumber == 0 || checkNumber == 9999;
    }
}
