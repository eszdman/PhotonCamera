package com.manual;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.ui.CameraFragment;
import com.eszdman.photoncamera.ui.MainActivity;
import com.eszdman.photoncamera.R;

import java.util.ArrayList;

public class EVKnobView extends KnobView {
    private static final float EPSILON_PRECISION = 10000.0f;
    private static final String TAG = EVKnobView.class.getSimpleName();

    public EVKnobView(Context context) {
        this(context, null);
        defaultValue = 0.0d;
    }

    public EVKnobView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void onExposureCompChanged() {
        onSetupIcons();
    }

    protected boolean onSetupIcons() {
        Activity cameraActivity = (Activity) getContext();
        setIconPadding(cameraActivity.getResources().getDimensionPixelSize(R.dimen.manual_knob_icon_padding));
        Range<Float> evRange = range;
        if (evRange == null || (evRange.getLower() == 0.0f && evRange.getUpper() == 0.0f)) {
            Log.d(TAG, "onSetupIcons() - evRange is not valid.");
            return false;
        }
        ArrayList<KnobItemInfo> arrayList = new ArrayList<>();
        status = cameraActivity.findViewById(R.id.ev_option_tv);
        status.setText(cameraActivity.getString(R.string.manual_mode_auto));
        ShadowTextDrawable autoDrawable = new ShadowTextDrawable();
        autoDrawable.setText(auto_sring);
        autoDrawable.setTextAppearance(cameraActivity, R.style.ManualModeKnobText);
        ShadowTextDrawable autoDrawableSelected = new ShadowTextDrawable();
        autoDrawableSelected.setText(auto_sring);
        autoDrawableSelected.setTextAppearance(cameraActivity, R.style.ManualModeKnobTextSelected);
        StateListDrawable autoStateDrawable = new StateListDrawable();
        autoStateDrawable.addState(new int[]{-16842913}, autoDrawable);
        autoStateDrawable.addState(new int[]{-16842913}, autoDrawableSelected);
        arrayList.add(new KnobItemInfo(autoStateDrawable, auto_sring, 0, defaultValue));
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
                drawable.setTextAppearance(cameraActivity, R.style.ManualModeKnobText);
                ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
                drawableSelected.setTextAppearance(cameraActivity, R.style.ManualModeKnobTextSelected);
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
                    arrayList.add(new KnobItemInfo(stateDrawable, text, positiveValueCount - i, value));
                } else {
                    arrayList.add(new KnobItemInfo(stateDrawable, text, negtiveValueCount - i, value));
                }
            }
        }
        int angle = cameraActivity.getResources().getInteger(R.integer.manual_ev_knob_view_angle_half);
        setKnobInfo(new KnobInfo(-angle, angle, -negtiveValueCount, positiveValueCount, cameraActivity.getResources().getInteger(R.integer.manual_ev_knob_view_auto_angle)));
        setKnobItems(arrayList);
        invalidate();
        return true;
    }

    private boolean isInteger(float value) {
        int checkNumber = ((int) (Math.abs(value) * 10000.0f)) % 10000;
        return checkNumber == 0 || checkNumber == 9999;
    }

    private boolean isZero(float value) {
        return ((double) Math.abs(value)) <= 0.001d;
    }

    @Override
    public void doWhatever() {
        super.doWhatever();
        CaptureRequest.Builder builder = Interface.i.camera.mPreviewRequestBuilder;
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (int) getCurrentKnobItem().value);
        ;
        Interface.i.camera.rebuildPreviewBuilder();
    }
}
