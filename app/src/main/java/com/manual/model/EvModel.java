package com.manual.model;

import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Range;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.ui.camera.CameraFragment;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;
import java.util.Locale;

public class EvModel extends ManualModel<Float> {

    private final String TAG = EvModel.class.getSimpleName();
    private float evStep;

    public EvModel(Range range, ValueChangedEvent valueChangedEvent) {
        super(range, valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {
        Range<Float> evRange = range;
        if (evRange == null || (evRange.getLower() == 0.0f && evRange.getUpper() == 0.0f)) {
            Log.d(TAG, "onSetupIcons() - evRange is not valid.");
            return;
        }
        KnobItemInfo auto = getNewAutoItem(0, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;
        int positiveValueCount = 0;
        int negtiveValueCount = 0;
        evStep = (CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue());
        float step = 0.25f;
        ArrayList<Float> values = new ArrayList<>();
        for (float fValue = evRange.getUpper(); fValue >= evRange.getLower(); fValue -= step) {
            float roundedValue = ((float) Math.round(10000.0f * fValue)) / 10000.0f;
            if (!isZero(fValue)) {
                if (fValue > 0.0f) {
                    positiveValueCount++;
                } else {
                    negtiveValueCount++;
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
                drawable.setTextAppearance(PhotonCamera.getCameraActivity(), R.style.ManualModeKnobText);
                ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
                drawableSelected.setTextAppearance(PhotonCamera.getCameraActivity(), R.style.ManualModeKnobTextSelected);
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
                String text = String.format(Locale.ROOT, "%.2f", value);
                if (value > 0.0f) {
                    getKnobInfoList().add(new KnobItemInfo(stateDrawable, text, positiveValueCount - tick, value));
                } else {
                    getKnobInfoList().add(new KnobItemInfo(stateDrawable, text, negtiveValueCount - tick, value));
                }
            }
        }
        int angle = PhotonCamera.getCameraActivity().getResources().getInteger(R.integer.manual_ev_knob_view_angle_half);
        knobInfo = new KnobInfo(-angle, angle, -negtiveValueCount, positiveValueCount, PhotonCamera.getCameraActivity().getResources().getInteger(R.integer.manual_ev_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo) {
        currentInfo = knobItemInfo;
        CaptureRequest.Builder builder = PhotonCamera.getCameraFragment().mPreviewRequestBuilder;
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (int) (knobItemInfo.value  / evStep));
        PhotonCamera.getCameraFragment().rebuildPreviewBuilder();
        //fireValueChangedEvent(knobItemInfo.text);
    }

    private boolean isZero(float value) {
        return ((double) Math.abs(value)) <= 0.001d;
    }

    private boolean isInteger(float value) {
        int checkNumber = ((int) (Math.abs(value) * 10000.0f)) % 10000;
        return checkNumber == 0 || checkNumber == 9999;
    }
}
