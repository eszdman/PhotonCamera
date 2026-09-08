package com.particlesdevs.photoncamera.processing.parameters;

/** Deterministic reference selection; inputs are in capture-time order. */
public final class NightReferenceSelector {
    private NightReferenceSelector() {}

    /** Immutable request tag; elapsed realtime only, never compared to an unknown sensor clock. */
    public static final class CaptureIntent {
        public final long elapsedRealtimeNs;
        public CaptureIntent(long timestamp) { elapsedRealtimeNs = timestamp; }
    }

    public static final class Quality {
        public double sharpness = Double.NaN, clipping = Double.NaN, gyroBlur = Double.NaN;
        public double subjectChange = Double.NaN, alignment = Double.NaN, timeDistanceSeconds = Double.NaN;
    }

    public static int select(double[] exposures, float[] shake, Quality[] quality) {
        int fallback = select(exposures, shake);
        if (quality == null || quality.length != exposures.length) return fallback;
        double minExposure = exposures[fallback], bestSharp = 0, minClip = Double.POSITIVE_INFINITY;
        for (int i = 0; i < quality.length; i++) if (exposures[i] == minExposure && quality[i] != null) {
            if (valid(quality[i].sharpness)) bestSharp = Math.max(bestSharp, quality[i].sharpness);
            if (valid(quality[i].clipping)) minClip = Math.min(minClip, quality[i].clipping);
        }
        if (!Double.isFinite(minClip)) return fallback;
        int best = fallback;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int i = 0; i < quality.length; i++) {
            Quality q = quality[i];
            if (exposures[i] != minExposure || q == null || !valid(q.clipping) || !valid(q.sharpness)) continue;
            // Prefer the least-clipped short class; never promote a long exposure.
            if (q.clipping > minClip + .005) continue;
            double sharpPenalty = bestSharp > 1e-6 ? 1 - q.sharpness / bestSharp : 0;
            double blurPenalty = valid(q.gyroBlur) ? q.gyroBlur / (q.gyroBlur + .01)
                    : Float.isFinite(shake[i]) && shake[i] >= 0 ? shake[i] / (shake[i] + 100.0) : .5;
            double cost = 2 * sharpPenalty + 4 * q.clipping + .6 * blurPenalty
                    + (valid(q.subjectChange) ? q.subjectChange : .25)
                    + (valid(q.alignment) ? 1 - q.alignment : .25)
                    + .1 * (valid(q.timeDistanceSeconds) ? Math.min(q.timeDistanceSeconds, 5) / 5 : 0);
            if (cost < bestCost) { bestCost = cost; best = i; }
        }
        return best;
    }

    private static boolean valid(double value) { return Double.isFinite(value) && value >= 0; }

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
