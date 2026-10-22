package com.particlesdevs.photoncamera.control.haptics;

import java.util.Collections;
import java.util.List;

public final class HapticPattern {
    public final List<HapticStep> steps;
    public final HapticPredefined predefined;
    public final HapticWaveform waveform;

    public HapticPattern(List<HapticStep> steps, HapticPredefined predefined,
                         HapticWaveform waveform) {
        this.steps = Collections.unmodifiableList(steps);
        this.predefined = predefined != null ? predefined : HapticPredefined.NONE;
        this.waveform = waveform;
    }
}
