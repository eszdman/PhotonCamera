package com.manual.model;

import android.util.Range;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.Interface;
import com.manual.KnobInfo;
import com.manual.KnobItemInfo;
import com.manual.KnobView;
import com.manual.ShadowTextDrawable;

public class TestModel extends ManualModel<String> {
    public TestModel(Range range, ValueChangedEvent valueChangedEvent) {
        super(range, valueChangedEvent);
    }

    @Override
    protected void fillKnobInfoList() {
        for (int i = 0; i< 50; i++)
        {
            ShadowTextDrawable drawable = new ShadowTextDrawable();
            drawable.setText(String.valueOf(i));
            drawable.setTextAppearance(Interface.i.mainActivity, R.style.ManualModeKnobText);
            getKnobInfoList().add(getItemInfo(String.valueOf(i),i,i));
        }
        currentInfo = getKnobInfoList().get(0);
        int angle = getKnobInfoList().size()*4;
        knobInfo = new KnobInfo(0, angle, 0, getKnobInfoList().size(), 25);
    }

    @Override
    public void onSelectedKnobItemChanged(KnobItemInfo knobItemInfo2) {

    }

    @Override
    public void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState) {

    }
}
