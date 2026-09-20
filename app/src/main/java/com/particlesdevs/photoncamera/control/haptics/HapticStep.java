package com.particlesdevs.photoncamera.control.haptics;

public final class HapticStep {
    public final HapticPrimitive primitive;
    public final float scale;
    public final int delayMs;

    public HapticStep(HapticPrimitive primitive, float scale, int delayMs) {
        this.primitive = primitive;
        this.scale = Math.max(0f, Math.min(1f, scale));
        this.delayMs = Math.max(0, delayMs);
    }

    public static HapticStep of(HapticPrimitive primitive, float scale) {
        return new HapticStep(primitive, scale, 0);
    }
}
