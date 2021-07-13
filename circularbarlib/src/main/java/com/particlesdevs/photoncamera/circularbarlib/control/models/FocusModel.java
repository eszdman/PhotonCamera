package com.particlesdevs.photoncamera.circularbarlib.control.models;

import android.content.Context;
import android.graphics.drawable.Drawable;
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
import java.util.Locale;

/**
 * Created by killerink, vibhorSrv, eszdman
 */
public class FocusModel extends ManualModel<Float> {

    public FocusModel(Context context, CameraCharacteristics cameraCharacteristics, Range<Float> range,
                      ManualParamModel manualParamModel, ValueChangedEvent valueChangedEvent, Vibrator v) {
        super(context, cameraCharacteristics, range, manualParamModel, valueChangedEvent,v);
    }

    @Override
    protected void fillKnobInfoList() {
        Drawable drawable;
        KnobItemInfo auto;
        if (range == null) {
            auto = getNewAutoItem(-1.0d, context.getString(R.string.manual_mode_fixed));
            getKnobInfoList().add(auto);
            currentInfo = auto;
            return;
        }
        auto = getNewAutoItem(ManualParamModel.FOCUS_AUTO, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;
        float focusStep = (range.getUpper() - range.getLower()) / 20;
        ArrayList<Float> values = new ArrayList<>();
        for (float fValue = range.getUpper(); fValue >= range.getLower(); fValue -= focusStep) {
            values.add(fValue);
        }
        if (values.size() > 0) {
            values.set(values.size() - 1, range.getLower());
        }
        for (int tick = 0; tick < values.size(); tick++) {
            if (tick == 0) {
                drawable = context.getDrawable(R.drawable.manual_icon_focus_near);
            } else if (tick == values.size() - 1) {
                drawable = context.getDrawable(R.drawable.manual_icon_focus_far);
            } else {
                drawable = new ShadowTextDrawable();
            }
            String text = String.format(Locale.ROOT, "%.2f", values.get(tick));
//            getKnobInfoList().add(new KnobItemInfo(drawable, text, tick - values.size(), (double) values.get(tick)));
            getKnobInfoList().add(new KnobItemInfo(drawable, text, tick + 1, (double) values.get(tick)));
        }
        int angle = context.getResources().getInteger(R.integer.manual_focus_knob_view_angle_half);
        knobInfo = new KnobInfo(0, angle, 0, values.size(), context.getResources().getInteger(R.integer.manual_focus_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo) {
        currentInfo = knobItemInfo;
        manualParamModel.setCurrentFocusValue(knobItemInfo.value);
    }
}
