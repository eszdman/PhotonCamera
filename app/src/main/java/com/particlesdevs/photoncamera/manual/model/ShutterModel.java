package com.particlesdevs.photoncamera.manual.model;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.util.Range;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.processing.parameters.ExposureIndex;
import com.particlesdevs.photoncamera.manual.*;

import java.util.ArrayList;

public class ShutterModel extends ManualModel<Long> {

    public ShutterModel(Context context, Range range, ValueChangedEvent valueChangedEvent) {
        super(context, range, valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {

        long exposureTimeValue;
        Range<Long> range = super.range;
        if (range == null || (range.getLower() == 0 && range.getUpper() == 0)) {
            return;
        }

        KnobItemInfo auto = getNewAutoItem(SHUTTER_AUTO, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;

        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<Long> values = new ArrayList<>();

        long minexp = (long) range.getLower();
        minexp += 5000 - minexp % 5000;
        long maxexp = (long) range.getUpper();
        Log.v("ExpModel", "Max exp:" + maxexp);
        Log.v("ExpModel", "Min exp:" + minexp);
        double maxcnt = Math.log10((double) maxexp) / Math.log10(2);
        Log.v("ExpModel", "Max exp cnt:" + maxcnt);
        for (double expCnt = (Math.log10(minexp) / Math.log10(2)); expCnt <= maxcnt; ) {
            if (expCnt < maxcnt * 0.7) expCnt += 1.0 / 4.0;
            else expCnt += 1.0 / 8.0;
            long val = (long) (Math.pow(2.0, expCnt));
            String out = ExposureIndex.sec2string(ExposureIndex.time2sec(val));
            candidates.add(out);
            values.add(val);
        }
        int indicatorCount = 0;
        int preferredIntervalCount = findPreferredIntervalCount(candidates.size());
        int tick = 0;
        while (tick < candidates.size()) {
            boolean isLastItem = tick == candidates.size() - 1;
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(context, R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(context, R.style.ManualModeKnobTextSelected);
            if (tick % preferredIntervalCount == 0 || isLastItem) {
                String text = candidates.get(tick);
                drawable.setText(text);
                drawableSelected.setText(text);
                indicatorCount++;
            }
            StateListDrawable stateDrawable = new StateListDrawable();
            stateDrawable.addState(new int[]{-android.R.attr.state_selected}, drawable);
            stateDrawable.addState(new int[]{android.R.attr.state_selected}, drawableSelected);
//            getKnobInfoList().add(new KnobItemInfo(stateDrawable, candidates.get(tick), tick - candidates.size(), (double) values.get(tick)));
            getKnobInfoList().add(new KnobItemInfo(stateDrawable, candidates.get(tick), tick + 1, (double) values.get(tick)));
            tick++;
        }
        int angle = findPreferredKnobViewAngle(indicatorCount);
        Log.d("TAGTAG", "findPreferredKnobViewAngle: " + angle);
        int angleMax = context.getResources().getInteger(R.integer.manual_exposure_knob_view_angle_half);
        if (angle > angleMax) {
            angle = angleMax;
        }
        knobInfo = new KnobInfo(0, angle, 0, candidates.size(), context.getResources().getInteger(R.integer.manual_exposure_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo) {
        currentInfo = knobItemInfo;
        ParamController.setShutter((long) knobItemInfo.value);
        Log.d("Shutter", "onSelectedKnobItemChanged() called with: knobItemInfo = [" + knobItemInfo + "]");
    }

    private int findPreferredIntervalCount(int totalCount) {
        int result = 12;
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
