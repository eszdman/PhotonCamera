package com.particlesdevs.photoncamera.processing.parameters;

/** Capture-gain/shake heuristic, pending preview histogram and subject motion metering. */
public final class NightBracketing {
    public final double ratio;
    private final int spacing;

    public NightBracketing(boolean enabled, int mode, double gain, int shake, boolean tripod) {
        double maximum = mode == 2 ? 8 : mode == 1 ? 4 : 1;
        double stability = tripod ? 1 : shake < 0 ? 0
                : Math.max(0, Math.min(1, (400.0 - shake) / 375.0));
        double demand = Math.max(0, Math.min(1, (gain - 1) / 7.0));
        double proposed = 1 + (maximum - 1) * stability * demand;
        ratio = enabled && proposed >= 2 ? proposed : 1;
        spacing = tripod || shake <= 100 ? 3 : 6;
    }

    public boolean isLong(int slot) {
        // Two normal frames first; never more than three long frames.
        return ratio > 1 && slot >= 2 && (slot - 2) % spacing == 0
                && (slot - 2) / spacing < 3;
    }
}
