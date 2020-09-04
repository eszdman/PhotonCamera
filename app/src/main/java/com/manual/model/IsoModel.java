package com.manual.model;

import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;

public class IsoModel extends ManualModel<Integer> {

    public static final String[] ISO_CANDIDATES = {"100", "125", "160", "200", "250", "320", "400", "500", "640", "800", "1000", "1250", "1600", "2000", "2500", "3200", "4000", "5000", "6400", "12800"};

    public IsoModel(Range range, ValueChangedEvent valueChangedEvent) {
        super(range, valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {
        KnobItemInfo auto = getNewAutoItem(-1.0d, null);
        getKnobInfoList().add(auto);
        currentInfo = auto;

        ArrayList<String> candidates = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();
        for (String isoCandidate : ISO_CANDIDATES) {
            int isoValue = Integer.parseInt(isoCandidate);
            if (isoValue >= range.getLower() && isoValue - 50 <= range.getUpper()) {
                candidates.add(isoCandidate);
                values.add(isoValue);
            }
        }
        int tick = 0;
        while (tick < candidates.size()) {
            boolean isLastItem = tick == candidates.size() + -1;
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(Interface.getMainActivity(), R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(Interface.getMainActivity(), R.style.ManualModeKnobTextSelected);
            if (tick % 3 == 0 || isLastItem) {
                drawable.setText(candidates.get(tick));
                drawableSelected.setText(candidates.get(tick));
            }
            StateListDrawable stateDrawable = new StateListDrawable();
            stateDrawable.addState(new int[]{-16842913}, drawable);
            stateDrawable.addState(new int[]{-16842913}, drawableSelected);
            getKnobInfoList().add(new KnobItemInfo(stateDrawable, candidates.get(tick), tick - candidates.size(), values.get(tick)));
            getKnobInfoList().add(new KnobItemInfo(stateDrawable, candidates.get(tick), tick + 1, values.get(tick)));
            tick++;
        }
        int angle = Interface.getMainActivity().getResources().getInteger(R.integer.manual_iso_knob_view_angle_half);
        knobInfo = new KnobInfo(-angle, angle, -candidates.size(), candidates.size(), Interface.getMainActivity().getResources().getInteger(R.integer.manual_iso_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo newval) {
        currentInfo = newval;
        CaptureRequest.Builder builder = Interface.getCameraFragment().mPreviewRequestBuilder;
        if (newval.equals(autoModel)) {
            if(Interface.getManualMode().getCurrentExposureValue() == -1) //check if Exposure is Auto
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, (int) newval.value);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Interface.getCameraFragment().mPreviewExposuretime);
        }
        Interface.getCameraFragment().rebuildPreviewBuilder();
        //fireValueChangedEvent(newval.text);
    }
}
