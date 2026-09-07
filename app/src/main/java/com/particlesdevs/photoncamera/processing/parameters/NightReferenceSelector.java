package com.particlesdevs.photoncamera.processing.parameters;

/** Deterministic reference selection; inputs are in capture-time order. */
public final class NightReferenceSelector {
    private NightReferenceSelector() {}

    public static int select(double[] exposures, float[] shake) {
        if (exposures.length == 0 || exposures.length != shake.length) {
            throw new IllegalArgumentException("Expected matching, nonempty frame metrics");
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (double exposure : exposures) {
            if (!Double.isFinite(exposure) || exposure <= 0) {
                throw new IllegalArgumentException("Invalid frame exposure");
            }
            minimum = Math.min(minimum, exposure);
        }
        int best = -1;
        float bestShake = Float.POSITIVE_INFINITY;
        for (int i = 0; i < exposures.length; i++) {
            // The merge normalizes to the minimum exposure, so keep that exact
            // reference class. A long frame must not win just for being steadier.
            if (exposures[i] != minimum) continue;
            float value = Float.isFinite(shake[i]) && shake[i] >= 0
                    ? shake[i] : Float.POSITIVE_INFINITY;
            if (best < 0 || value < bestShake) {
                best = i;
                bestShake = value;
            }
        }
        // Equal scores (including wholly missing gyro) keep the earliest frame.
        return best;
    }
}
