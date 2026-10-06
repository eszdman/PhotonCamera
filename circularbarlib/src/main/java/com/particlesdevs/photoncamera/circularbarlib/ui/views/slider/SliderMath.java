package com.particlesdevs.photoncamera.circularbarlib.ui.views.slider;

/**
 * Pure-Java snap math for the center-locked slider, kept free of Android types
 * so it can be unit-tested on the JVM.
 */
public final class SliderMath {
    private SliderMath() {
    }

    /** Coarsest lift gain: a whole-range sweep fits a short controlled drag. */
    public static final float MAX_GAIN = 8f;

    public static float maxScroll(int itemCount, float pitchPx) {
        if (itemCount <= 1 || pitchPx <= 0f) {
            return 0f;
        }
        return (itemCount - 1) * pitchPx;
    }

    public static float clampScroll(float scrollPx, int itemCount, float pitchPx) {
        float max = maxScroll(itemCount, pitchPx);
        if (scrollPx < 0f) {
            return 0f;
        }
        if (scrollPx > max) {
            return max;
        }
        return scrollPx;
    }

    public static int snapIndex(float scrollPx, float pitchPx, int itemCount) {
        if (itemCount <= 0 || pitchPx <= 0f) {
            return 0;
        }
        int index = Math.round(scrollPx / pitchPx);
        if (index < 0) {
            return 0;
        }
        if (index >= itemCount) {
            return itemCount - 1;
        }
        return index;
    }

    public static float scrollForIndex(int index, float pitchPx, int itemCount) {
        if (itemCount <= 0 || pitchPx <= 0f) {
            return 0f;
        }
        int clamped = Math.max(0, Math.min(index, itemCount - 1));
        return clamped * pitchPx;
    }

    /**
     * Scrub gain from downward finger press: 1x at/above touch-down, ramping
     * to {@link #MAX_GAIN} at {@code rampPx} with a smoothstep onset, capped
     * beyond. The slider analog of the old wheel's toward-center speedup.
     */
    public static float gainForPress(float pressPx, float rampPx) {
        if (pressPx <= 0f || rampPx <= 0f) {
            return 1f;
        }
        float t = Math.min(1f, pressPx / rampPx);
        float smooth = t * t * (3f - 2f * t);
        return 1f + (MAX_GAIN - 1f) * smooth;
    }
}
