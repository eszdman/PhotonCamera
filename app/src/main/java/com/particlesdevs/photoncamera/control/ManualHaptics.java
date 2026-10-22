package com.particlesdevs.photoncamera.control;

import com.particlesdevs.photoncamera.circularbarlib.api.HapticPerformer;

public final class ManualHaptics implements HapticPerformer {
    private final Vibration vibration;

    public ManualHaptics(Vibration vibration) {
        this.vibration = vibration;
    }

    @Override
    public void tick() {
        if (vibration != null) vibration.sliderTick();
    }

    @Override
    public void click() {
        if (vibration != null) vibration.confirm();
    }

    @Override
    public void longPress() {
        if (vibration != null) vibration.longPress();
    }
}
