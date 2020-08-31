package com.manual.model;

import android.graphics.drawable.StateListDrawable;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;

import com.eszdman.photoncamera.Control.Manual;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ShadowTextDrawable;

import java.util.ArrayList;

public class IsoModel extends ManualModel<Integer> {

    public static final String[] ISO_CANDIDATES = {"100", "125", "160", "200", "250", "320", "400", "500", "640", "800", "1000", "1250", "1600", "2000", "2500", "3200", "4000", "5000", "6400", "12800"};

    public IsoModel(Range range,ValueChangedEvent valueChangedEvent) {
        super(range,valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {
        KnobItemInfo auto = getAutoItem(-1.0d);
        getKnobInfoList().add(auto);
        currentInfo = auto;

        ArrayList<String> arrayList2 = new ArrayList<>();
        ArrayList<Integer> arrayList3 = new ArrayList<>();
        for (String isoCandidate : ISO_CANDIDATES) {
            int isoValue = Integer.parseInt(isoCandidate);
            if (isoValue >= range.getLower() && isoValue - 50 <= range.getUpper()) {
                arrayList2.add(isoCandidate);
                arrayList3.add(isoValue);
            }
        }
        int i2 = 0;
        while (i2 < arrayList2.size()) {
            boolean isLastItem = i2 == arrayList2.size() + -1;
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setTextAppearance(Interface.i.mainActivity, R.style.ManualModeKnobText);
            ShadowTextDrawable drawableSelected = new ShadowTextDrawable();
            drawableSelected.setTextAppearance(Interface.i.mainActivity, R.style.ManualModeKnobTextSelected);
            if (i2 % 3 == 0 || isLastItem) {
                drawable.setText(arrayList2.get(i2));
                drawableSelected.setText(arrayList2.get(i2));
            }
            StateListDrawable stateDrawable = new StateListDrawable();
            stateDrawable.addState(new int[]{-16842913}, drawable);
            stateDrawable.addState(new int[]{-16842913}, drawableSelected);
            getKnobInfoList().add(new KnobItemInfo(stateDrawable, arrayList2.get(i2), i2 - arrayList2.size(), arrayList3.get(i2)));
            getKnobInfoList().add(new KnobItemInfo(stateDrawable, arrayList2.get(i2), i2 + 1, arrayList3.get(i2)));
            i2++;
        }
        int angle = Interface.i.mainActivity.getResources().getInteger(R.integer.manual_iso_knob_view_angle_half);
        knobInfo = new KnobInfo(-angle, angle, -arrayList2.size(), arrayList2.size(),  Interface.i.mainActivity.getResources().getInteger(R.integer.manual_iso_knob_view_auto_angle));
    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }

    @Override
    public void onSelectedKnobItemChanged(KnobView knobView, KnobItemInfo oldval, KnobItemInfo newval) {
        currentInfo = newval;
        CaptureRequest.Builder builder = Interface.i.camera.mPreviewRequestBuilder;
        if (newval.value == -1) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, (int) newval.value);
        }
        Interface.i.camera.rebuildPreviewBuilder();
        fireValueChangedEvent();
    }
}
