package com.particlesdevs.photoncamera.control.haptics;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class HapticCapabilities {
    public final boolean hasVibrator;
    public final boolean amplitudeControl;
    public final boolean predefinedEffects;
    public final Set<HapticPrimitive> primitives;

    public HapticCapabilities(boolean hasVibrator, boolean amplitudeControl,
                              boolean predefinedEffects, Set<HapticPrimitive> primitives) {
        this.hasVibrator = hasVibrator;
        this.amplitudeControl = amplitudeControl;
        this.predefinedEffects = predefinedEffects;
        this.primitives = Collections.unmodifiableSet(
                primitives == null || primitives.isEmpty()
                        ? EnumSet.noneOf(HapticPrimitive.class)
                        : EnumSet.copyOf(primitives));
    }

    public boolean supportsPrimitives() {
        return hasVibrator && !primitives.isEmpty();
    }

    public static HapticCapabilities none() {
        return new HapticCapabilities(false, false, false, EnumSet.noneOf(HapticPrimitive.class));
    }

    public static HapticCapabilities composed() {
        return new HapticCapabilities(true, true, true, EnumSet.allOf(HapticPrimitive.class));
    }

    public static HapticCapabilities predefined() {
        return new HapticCapabilities(true, true, true, EnumSet.noneOf(HapticPrimitive.class));
    }

    public static HapticCapabilities legacy() {
        return new HapticCapabilities(true, false, false, EnumSet.noneOf(HapticPrimitive.class));
    }
}
