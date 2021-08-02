package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Vibrator;
import android.util.Log;
import android.util.Range;

import com.particlesdevs.photoncamera.circularbarlib.camera.IsoExpoSelector;
import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobInfo;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobItemInfo;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.ShadowTextDrawable;

import java.util.ArrayList;
/**
 * Created by killerink, vibhorSrv, eszdman
 */
public class IsoModel extends ManualModel<Integer> {

    public IsoModel(Context context, CameraCharacteristics cameraCharacteristics, Range<Integer> range,
                    ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent, Vibrator v) {
        super(context,cameraCharacteristics, range, manualParamModel, valueChangedEvent,v);
    }

    @Override
    protected void fillKnobInfoList() {
        KnobItemInfo auto = getNewAutoItem(ManualParamModel.ISO_AUTO, null);
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
        for (double isoCnt = Math.log10(1) / Math.log10(2); isoCnt < Math.log10((double) maxiso / miniso) / Math.log10(2); isoCnt += 1.0 / 4.0) {
            int val = (int) (Math.pow(2.0, isoCnt) * miniso);
            candidates.add(String.valueOf(val));
            values.add((int) (val / IsoExpoSelector.getMPY(cameraCharacteristics)));
        }
        candidates.add(String.valueOf(isohigh));
        values.add((int)isohigh);
        int indicatorCount = 0;
        int tick = 0;
        int preferredIntervalCount = 4;
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
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo) {
        currentInfo = knobItemInfo;
        manualParamModel.setCurrentISOValue(knobItemInfo.value);
    }

    private int findPreferredKnobViewAngle(int indicatorCount) {
        return (indicatorCount - 1) * 30;
    }

}
