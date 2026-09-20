package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;
import android.util.Range;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.camera.ExposureIndex;
import com.particlesdevs.photoncamera.circularbarlib.camera.IsoExpoSelector;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobInfo;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobItemInfo;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.ShadowTextDrawable;

import java.util.ArrayList;

/**
 * Created by killerink, vibhorSrv, eszdman
 */
public class ShutterModel extends ManualModel<Long> {

    public ShutterModel(Context context, CameraCharacteristics cameraCharacteristics, Range<Long> range,
                        ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent) {
        super(context, cameraCharacteristics, range, manualParamModel, valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {

        long exposureTimeValue;
        Range<Long> range = super.range;
        if (range == null || (range.getLower() == 0 && range.getUpper() == 0)) {
            return;
        }

        KnobItemInfo auto = getNewAutoItem(ManualParamModel.EXPOSURE_AUTO, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;

        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<Long> values = new ArrayList<>();

        long minexp = range.getLower();
        if (minexp < 1000) minexp = 1000;
        //minexp += 5000 - minexp % 5000;
        long maxexp = range.getUpper();
        Log.v("ExpModel", "Max exp:" + maxexp);
        Log.v("ExpModel", "Min exp:" + minexp);
        double maxcnt = Math.log10((double) maxexp) / Math.log10(2);
        double mincnt = Math.log10((double) minexp) / Math.log10(2);
        Log.v("ExpModel", "Max exp cnt:" + maxcnt);
        // split to negative and positive log list
        ArrayList<String> candidatesPos = new ArrayList<>();
        ArrayList<Long> valuesPos = new ArrayList<>();
        double shortExp = Math.log10(ExposureIndex.sec) / Math.log10(2);
        if (shortExp > maxcnt) shortExp = Math.log10(ExposureIndex.sec/4.0) / Math.log10(2);
        for (double expCnt = shortExp; expCnt < maxcnt; expCnt += 1.0 / 4.0) {
            long val = (long) (Math.pow(2.0, expCnt));
            // round val to 1000 from both sides
            if (val % 250000000 != 0) {
                long val1 = val - val % 250000000;
                long val2 = val1 + 250000000;
                if (val - val1 > val2 - val) val = val2;
                else val = val1;
            }
            String out = ExposureIndex.sec2string(ExposureIndex.time2sec(val));
            candidatesPos.add(out);
            valuesPos.add(val);
        }
        candidatesPos.add(ExposureIndex.sec2string(ExposureIndex.time2sec(maxexp)));
        valuesPos.add(maxexp);

        ArrayList<String> candidatesNeg = new ArrayList<>();
        ArrayList<Long> valuesNeg = new ArrayList<>();
        for (double expCnt = shortExp - 1.0 / 4.0; expCnt > mincnt; expCnt -= 1.0 / 4.0) {
            long val = (long) (Math.pow(2.0, expCnt));
            if(val > maxexp) continue;
            String out = ExposureIndex.sec2string(ExposureIndex.time2sec(val));
            candidatesNeg.add(out);
            valuesNeg.add(val);
        }
        candidatesNeg.add(ExposureIndex.sec2string(ExposureIndex.time2sec(minexp)));
        valuesNeg.add(minexp);
        // invert negative list
        for (int i = candidatesNeg.size() - 1; i >= 0; i--) {
            candidates.add(candidatesNeg.get(i));
            values.add(valuesNeg.get(i));
        }
        // add positive list
        for (int i = 0; i < candidatesPos.size(); i++) {
            candidates.add(candidatesPos.get(i));
            values.add(valuesPos.get(i));
        }

        int indicatorCount = 0;
        int preferredIntervalCount = 4;
        int tick = 0;
        int tickShift = candidatesNeg.size()%preferredIntervalCount;
        while (tick < candidates.size()) {
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(context, R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(context, R.style.ManualModeKnobTextSelected);
            int prefMpy = 1;
            if(candidates.get(tick).length() > 5) prefMpy = 2;
            if ((tick-tickShift) % (preferredIntervalCount*prefMpy) == 0) {
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
        manualParamModel.setCurrentExposureValue(knobItemInfo.value);
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
