package com.manual.model;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Range;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;

public class IsoModel extends ManualModel<Integer> {

    //public static final String[] ISO_CANDIDATES = {"100", "125", "160", "200", "250", "320", "400", "500", "640", "800", "1000", "1250", "1300", "1400", "1500", "1600", "1700", "1800", "1900", "2000", "2100", "2200", "2300", "2400", "2500", "3200", "4000", "5000", "6400", "12800", "25600"};

    public IsoModel(Context context, Range range, ValueChangedEvent valueChangedEvent) {
        super(context, range, valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {
        KnobItemInfo auto = getNewAutoItem(-0.0d, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;

        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();
        Object isolow = range.getLower();
        Object isohigh = range.getUpper();
        int miniso = (int) isolow;
        int maxiso = (int) isohigh;
        Log.v("IsoModel", "Max iso:" + maxiso);
        Log.v("IsoModel", "Max iso cnt:" + Math.log10((double) maxiso / miniso) / Math.log10(2));
        for (double isoCnt = Math.log10(1) / Math.log10(2); isoCnt <= Math.log10((double) maxiso / miniso) / Math.log10(2); isoCnt += 1.0 / 4.0) {
            int val = (int) (Math.pow(2.0, isoCnt) * miniso);
            candidates.add(String.valueOf(val));
            values.add((int) (val / IsoExpoSelector.getMPY()));
        }
        /*for (String isoCandidate : ISO_CANDIDATES) {
            int isoValue = Integer.parseInt(isoCandidate);
            if (isoValue >= range.getLower() && isoValue - 50 <= range.getUpper()) {
                candidates.add(isoCandidate);
                values.add(isoValue);
            }
        }*/
        int indicatorCount = 0;
        int tick = 0;
        int preferredIntervalCount = findPreferredIntervalCount(candidates.size());
        while (tick < candidates.size()) {
            boolean isLastItem = tick == candidates.size() + -1;
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(context, R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(context, R.style.ManualModeKnobTextSelected);
            if (tick % preferredIntervalCount == 0 || isLastItem) {
                drawable.setText(candidates.get(tick));
                drawableSelected.setText(candidates.get(tick));
                indicatorCount++;
            }
            StateListDrawable stateDrawable = new StateListDrawable();
            stateDrawable.addState(new int[]{-android.R.attr.state_selected}, drawable);
            stateDrawable.addState(new int[]{android.R.attr.state_selected}, drawableSelected);
//            getKnobInfoList().add(new KnobItemInfo(stateDrawable, candidates.get(tick), tick - candidates.size(), values.get(tick)));
            getKnobInfoList().add(new KnobItemInfo(stateDrawable, candidates.get(tick), tick + 1, values.get(tick)));
            tick++;
        }
        int angle = findPreferredKnobViewAngle(indicatorCount);
        int angleMax = context.getResources().getInteger(R.integer.manual_iso_knob_view_angle_half);
        if (angle > angleMax) {
            angle = angleMax;
        }
        knobInfo = new KnobInfo(0, angle, 0, candidates.size(), context.getResources().getInteger(R.integer.manual_iso_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo newval) {
        currentInfo = newval;
        CaptureRequest.Builder builder = PhotonCamera.getCaptureController().mPreviewRequestBuilder;
        if (newval.equals(autoModel)) {
            if (PhotonCamera.getManualMode().getCurrentExposureValue() == 0) //check if Exposure is Auto
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, (int) (newval.value));
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, PhotonCamera.getCaptureController().mPreviewExposureTime);
        }
        PhotonCamera.getCaptureController().rebuildPreviewBuilder();
        //fireValueChangedEvent(newval.text);
    }

    private int findPreferredKnobViewAngle(int indicatorCount) {
        return (indicatorCount - 1) * 30;
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
}
