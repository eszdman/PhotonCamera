package com.manual.model;

import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Range;
import android.util.Rational;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.parameters.ExposureIndex;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;

public class ShutterModel extends ManualModel<Long> {

    //public static final String[] EXPOSURE_TIME_CANDIDATES = {"Min", "1/30000", "1/25000", "1/20000", "1/18000", "1/16000", "1/14000", "1/12000", "1/10000", "1/8000", "1/6400", "1/5000", "1/4000", "1/3200", "1/2500", "1/2000", "1/1600", "1/1250", "1/1000", "1/800", "1/640", "1/500", "1/400", "1/320", "1/250", "1/200", "1/160", "1/125", "1/100", "1/80", "1/60", "1/50", "1/40", "1/30", "1/25", "1/20", "1/15", "1/13", "1/10", "1/8", "1/6", "1/5", "1/4", "1/3", "0.4", "0.5", "0.6", "0.75", "1", "1.3", "1.6", "2", "2.5", "3", "4", "5", "6", "8", "10", "13", "15", "20", "25", "30", "31", "32", "33", "34", "Max"};


    public ShutterModel(Range range, ValueChangedEvent valueChangedEvent) {
        super(range, valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {

        long exposureTimeValue;
        Range<Long> range = super.range;
        if (range == null || (range.getLower() == 0 && range.getUpper() == 0)) {
            return;
        }

        KnobItemInfo auto = getNewAutoItem(0.0d, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;

        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<Long> values = new ArrayList<>();

        long minexp = (long)range.getLower();
        minexp+=5000 - minexp%5000;
        long maxexp = (long)range.getUpper();
        Log.v("ExpModel","Max exp:"+maxexp);
        Log.v("ExpModel","Min exp:"+minexp);
        double maxcnt = Math.log10((double)maxexp)/Math.log10(2);
        Log.v("ExpModel","Max exp cnt:"+maxcnt);
        for(double expCnt = (Math.log10(minexp)/Math.log10(2)); expCnt<=maxcnt;){
            if(expCnt < maxcnt*0.7) expCnt+=1.0/4.0;
            else expCnt+=1.0/8.0;
            long val = (long)(Math.pow(2.0,expCnt));
            String out = ExposureIndex.sec2string(ExposureIndex.time2sec(val));
            candidates.add(out);
            values.add(val);
        }
        /*for (String exposureTimeCandidate : EXPOSURE_TIME_CANDIDATES) {
            if (exposureTimeCandidate.equalsIgnoreCase("Min"))
                exposureTimeValue = range.getLower();
            else if (exposureTimeCandidate.equalsIgnoreCase("Max"))
                exposureTimeValue = range.getUpper();
            else if (exposureTimeCandidate.contains("/")) {
                exposureTimeValue = (long) (Rational.parseRational(exposureTimeCandidate).doubleValue() * 1000.0d * 1000.0d * 1000.0d);
            } else {
                exposureTimeValue = (long) (Double.parseDouble(exposureTimeCandidate) * 1000.0d * 1000.0d * 1000.0d);
            }
            if (exposureTimeValue >= range.getLower() && exposureTimeValue <= range.getUpper() && !values.contains(exposureTimeValue)) {
                candidates.add(exposureTimeCandidate);
                values.add(exposureTimeValue);
            }
        }*/
        int indicatorCount = 0;
        int preferredIntervalCount = findPreferredIntervalCount(candidates.size());
        int tick = 0;
        while (tick < candidates.size()) {
            boolean isLastItem = tick == candidates.size() - 1;
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(PhotonCamera.getCameraActivity(), R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(PhotonCamera.getCameraActivity(), R.style.ManualModeKnobTextSelected);
            if (tick % preferredIntervalCount == 0 || isLastItem) {
                String text = candidates.get(tick);
                drawable.setText(text);
                drawableSelected.setText(text);
                indicatorCount++;
            }
            StateListDrawable stateDrawable = new StateListDrawable();
            stateDrawable.addState(new int[]{-16842913}, drawable);
            stateDrawable.addState(new int[]{-16842913}, drawableSelected);
//            getKnobInfoList().add(new KnobItemInfo(stateDrawable, candidates.get(tick), tick - candidates.size(), (double) values.get(tick)));
            getKnobInfoList().add(new KnobItemInfo(stateDrawable, candidates.get(tick), tick + 1, (double) values.get(tick)));
            tick++;
        }
        int angle = findPreferredKnobViewAngle(indicatorCount);
        int angleMax = PhotonCamera.getCameraActivity().getResources().getInteger(R.integer.manual_exposure_knob_view_angle_half);
        if (angle > angleMax) {
            angle = angleMax;
        }
        knobInfo = new KnobInfo(0, angle, 0, candidates.size(), PhotonCamera.getCameraActivity().getResources().getInteger(R.integer.manual_exposure_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo) {
        currentInfo = knobItemInfo;
        CaptureRequest.Builder builder = PhotonCamera.getCameraFragment().mPreviewRequestBuilder;
        if (knobItemInfo.equals(autoModel)) {
            if (PhotonCamera.getManualMode().getCurrentISOValue() == 0)//check if ISO is Auto
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            long out = (long)knobItemInfo.value;
            if(out > ExposureIndex.sec/5) out = ExposureIndex.sec/5;
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, out);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, PhotonCamera.getCameraFragment().mPreviewIso);
        }
        PhotonCamera.getCameraFragment().rebuildPreviewBuilder();
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
        return (indicatorCount - 1) * 30;
    }
}
