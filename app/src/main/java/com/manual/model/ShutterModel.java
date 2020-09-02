package com.manual.model;

import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;
import android.util.Rational;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ManualMode;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;

public class ShutterModel extends ManualModel<Long> {

    public static final String[] EXPOSURE_TIME_CANDIDATES = {"1/30000", "1/25000", "1/20000", "1/18000", "1/16000", "1/14000", "1/12000", "1/10000", "1/8000", "1/6400", "1/5000", "1/4000", "1/3200", "1/2500", "1/2000", "1/1600", "1/1250", "1/1000", "1/800", "1/640", "1/500", "1/400", "1/320", "1/250", "1/200", "1/160", "1/125", "1/100", "1/80", "1/60", "1/50", "1/40", "1/30", "1/25", "1/20", "1/15", "1/13", "1/10", "1/8", "1/6", "1/5", "1/4", "1/3", "0.4", "0.5", "0.6", "0.8", "1", "1.3", "1.6", "2", "2.5", "3", "4", "5", "6", "8", "10", "13", "15", "20", "25", "30"};


    public ShutterModel(Range range,ValueChangedEvent valueChangedEvent) {
        super(range,valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {

        long exposureTimeValue;
        Range<Long> range = super.range;
        if (range == null || (range.getLower() == 0 && range.getUpper() == 0)) {
            return;
        }

        KnobItemInfo auto = getAutoItem(-1.0d);
        getKnobInfoList().add(auto);
        currentInfo = auto;

        ArrayList<String> arrayList2 = new ArrayList<>();
        ArrayList<Long> arrayList3 = new ArrayList<>();
        for (String exposureTimeCandidate : EXPOSURE_TIME_CANDIDATES) {
            if (exposureTimeCandidate.contains("/")) {
                exposureTimeValue = (long) (Rational.parseRational(exposureTimeCandidate).doubleValue() * 1000.0d * 1000.0d * 1000.0d);
            } else {
                exposureTimeValue = (long) (Double.parseDouble(exposureTimeCandidate) * 1000.0d * 1000.0d * 1000.0d);
            }
            if (exposureTimeValue >= range.getLower() && exposureTimeValue <= range.getUpper()) {
                arrayList2.add(exposureTimeCandidate);
                arrayList3.add(exposureTimeValue);
            }
        }
        int indicatorCount = 0;
        int preferredIntervalCount = findPreferredIntervalCount(arrayList2.size());
        int i2 = 0;
        while (i2 < arrayList2.size()) {
            boolean isLastItem = i2 == arrayList2.size() + -1;
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(Interface.i.mainActivity, R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(Interface.i.mainActivity, R.style.ManualModeKnobTextSelected);
            if (i2 % preferredIntervalCount == 0 || isLastItem) {
                String text = arrayList2.get(i2);
                drawable.setText(text);
                drawableSelected.setText(text);
                indicatorCount++;
            }
            StateListDrawable stateDrawable = new StateListDrawable();
            stateDrawable.addState(new int[]{-16842913}, drawable);
            stateDrawable.addState(new int[]{-16842913}, drawableSelected);
            getKnobInfoList().add(new KnobItemInfo(stateDrawable, arrayList2.get(i2), i2 - arrayList2.size(), (double) arrayList3.get(i2)));
            getKnobInfoList().add(new KnobItemInfo(stateDrawable, arrayList2.get(i2), i2 + 1, (double) arrayList3.get(i2)));
            i2++;
        }
        int angle = findPreferredKnobViewAngle(indicatorCount);
        int angleMax = Interface.i.mainActivity.getResources().getInteger(R.integer.manual_exposure_knob_view_angle_half);
        if (angle > angleMax) {
            angle = angleMax;
        }
        knobInfo = new KnobInfo(-angle, angle, -arrayList2.size(), arrayList2.size(), Interface.i.mainActivity.getResources().getInteger(R.integer.manual_exposure_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo2) {
        currentInfo = knobItemInfo2;
        try {
            Interface.i.camera.mCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        CaptureRequest.Builder builder = Interface.i.camera.mPreviewRequestBuilder;
        if (knobItemInfo2.value == -1) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) knobItemInfo2.value);
        }
        Interface.i.camera.rebuildPreviewBuilder();
        //fireValueChangedEvent(knobItemInfo2.text);
    }

    private int findPreferredIntervalCount(int totalCount) {
        int result = 9;
        int minRemainder = Integer.MAX_VALUE;
        int i = 9;
        while (i >= 5 && (((float) (totalCount - 1)) / ((float) i)) + 1.0f <= 7.0f) {
            int remainder = ((totalCount % i) + (i - 1)) % i;
            if (minRemainder > remainder) {
                minRemainder = remainder;
                result = i;
            }
            i--;
        }
        return result;
    }

    private int findPreferredKnobViewAngle(int indicatorCount) {
        return (indicatorCount - 1) * 25;
    }
}
