package com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview;


public interface KnobViewChangedListener {
    void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState);

    void onSelectedKnobItemChanged(KnobView knobView, KnobItemInfo knobItemInfo, KnobItemInfo knobItemInfo2);
}
