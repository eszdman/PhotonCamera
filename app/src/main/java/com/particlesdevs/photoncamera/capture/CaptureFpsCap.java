package com.particlesdevs.photoncamera.capture;

/**
 * Pure helpers for the per-sensor "Capture session FPS" cap. Kept free of
 * Android types so the clamp logic is unit-testable.
 */
public final class CaptureFpsCap {

    private CaptureFpsCap() {
    }

    /**
     * Limits a frame-rate range to {@code cap}: the upper bound is capped and
     * the lower bound is clamped to it (so a fixed range above the cap becomes
     * a fixed range at the cap, e.g. [60,60] + cap 30 -&gt; [30,30]).
     *
     * @return two-element array {lower, upper}
     */
    public static int[] clamp(int lower, int upper, int cap) {
        int cappedUpper = Math.min(upper, cap);
        int cappedLower = Math.min(lower, cappedUpper);
        return new int[]{cappedLower, cappedUpper};
    }
}
