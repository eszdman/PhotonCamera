package com.manual;


public interface KnobViewChangedListener {
    void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState);

    void onSelectedKnobItemChanged(KnobView knobView, KnobItemInfo knobItemInfo, KnobItemInfo knobItemInfo2);
}
