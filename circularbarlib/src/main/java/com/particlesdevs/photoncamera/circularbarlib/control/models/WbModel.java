package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Vibrator;
import android.util.Range;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobInfo;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobItemInfo;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.ShadowTextDrawable;

import java.util.ArrayList;

/**
 * Model responsible for managing a pure, strictly uniform 50K stepped Kelvin scale (2000K - 10000K).
 * Follows ShutterModel architecture with labeled indicators every 1000K and 4 intermediate 50K ticks.
 */
public class WbModel extends ManualModel<Integer> {

    public WbModel(Context context, CameraCharacteristics cameraCharacteristics, Range<Integer> range,
                   ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent, Vibrator v) {
        super(context, cameraCharacteristics, range, manualParamModel, valueChangedEvent, v);
    }

    @Override
    protected void fillKnobInfoList() {
        KnobItemInfo auto = getNewAutoItem(ManualParamModel.WB_AUTO, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;

        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();

        int minK = 2000;
        int maxK = 10000;
        int stepK = 50;

        // Generate uniform steps from 2000K to 10000K
        for (int k = minK; k <= maxK; k += stepK) {
            candidates.add(k + "K");
            values.add(k);

            // Major text label every 1000K (every 5th tick)
            if (k % 1000 == 0) {
                int thousand = k / 1000;
                labels.add(thousand + "K");
            } else {
                labels.add(null); // Null label instructs KnobView to draw an intermediate tick mark
            }
        }

        int indicatorCount = 0;
        int tick = 0;
        while (tick < candidates.size()) {
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(context, R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(context, R.style.ManualModeKnobTextSelected);

            String text = labels.get(tick);
            if (text != null && !text.isEmpty()) {
                drawable.setText(text);
                drawableSelected.setText(text);
                indicatorCount++;
            }

            StateListDrawable stateDrawable = new StateListDrawable();
            stateDrawable.addState(new int[]{-android.R.attr.state_selected}, drawable);
            stateDrawable.addState(new int[]{android.R.attr.state_selected}, drawableSelected);

            getKnobInfoList().add(new KnobItemInfo(stateDrawable, candidates.get(tick), tick + 1, (double) values.get(tick)));
            tick++;
        }

        int angle = findPreferredKnobViewAngle(indicatorCount);
        int angleMax = context.getResources().getInteger(R.integer.manual_focus_knob_view_angle_half);
        if (angle > angleMax) {
            angle = angleMax;
        }
        knobInfo = new KnobInfo(0, angle, 0, candidates.size(), context.getResources().getInteger(R.integer.manual_focus_knob_view_auto_angle));
    }

    private int findPreferredKnobViewAngle(int indicatorCount) {
        return (indicatorCount - 1) * 30;
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {
    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo) {
        currentInfo = knobItemInfo;
        manualParamModel.setCurrentWbValue(knobItemInfo.value);
    }
}