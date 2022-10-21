package com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview;


public interface KnobViewChangedListener {
    void onRotationStateChanged(KnobView knobView, KnobView.RotationState rotationState);

    void onSelectedKnobItemChanged(KnobView knobView, KnobItemInfo knobItemInfo, KnobItemInfo knobItemInfo2);
}
