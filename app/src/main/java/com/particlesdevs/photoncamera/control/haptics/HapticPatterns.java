package com.particlesdevs.photoncamera.control.haptics;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class HapticPatterns {
    private static final Map<HapticEvent, HapticPattern> PATTERNS = new EnumMap<>(HapticEvent.class);

    private HapticPatterns() {
    }

    static {
        PATTERNS.put(HapticEvent.TOGGLE_ON, pattern(HapticPredefined.CLICK,
                w(new long[]{0, 8}, new int[]{0, 180}),
                s(HapticPrimitive.CLICK, 0.6f)));
        PATTERNS.put(HapticEvent.TOGGLE_OFF, pattern(HapticPredefined.TICK,
                w(new long[]{0, 8}, new int[]{0, 120}),
                s(HapticPrimitive.TICK, 0.7f)));
        PATTERNS.put(HapticEvent.SLIDER_TICK, pattern(HapticPredefined.TICK,
                w(new long[]{0, 4}, new int[]{0, 90}),
                s(HapticPrimitive.TICK, 0.25f)));
        PATTERNS.put(HapticEvent.MODE_CHANGE, pattern(HapticPredefined.CLICK,
                w(new long[]{0, 6, 15, 8}, new int[]{0, 160, 0, 220}),
                s(HapticPrimitive.TICK, 0.5f),
                s(HapticPrimitive.CLICK, 0.7f, 25)));
        PATTERNS.put(HapticEvent.LONG_PRESS, pattern(HapticPredefined.HEAVY_CLICK,
                w(new long[]{0, 18}, new int[]{0, 255}),
                s(HapticPrimitive.CLICK, 0.9f)));
        PATTERNS.put(HapticEvent.SHUTTER_PRESS, pattern(HapticPredefined.CLICK,
                w(new long[]{0, 10}, new int[]{0, 200}),
                s(HapticPrimitive.CLICK, 0.7f)));
        PATTERNS.put(HapticEvent.CAPTURE_START, pattern(HapticPredefined.HEAVY_CLICK,
                w(new long[]{0, 6, 12, 12}, new int[]{0, 140, 0, 255}),
                s(HapticPrimitive.QUICK_RISE, 0.5f),
                s(HapticPrimitive.THUD, 0.8f, 15)));
        PATTERNS.put(HapticEvent.CAPTURE_COMPLETE, pattern(HapticPredefined.DOUBLE_CLICK,
                w(new long[]{0, 14, 25, 8}, new int[]{0, 255, 0, 160}),
                s(HapticPrimitive.THUD, 1.0f),
                s(HapticPrimitive.TICK, 0.5f, 30)));
        PATTERNS.put(HapticEvent.BURST_FRAME, pattern(HapticPredefined.TICK,
                w(new long[]{0, 5}, new int[]{0, 140}),
                s(HapticPrimitive.TICK, 0.4f)));
        PATTERNS.put(HapticEvent.FOCUS_LOCKED, pattern(HapticPredefined.CLICK,
                w(new long[]{0, 5, 18, 7}, new int[]{0, 140, 0, 200}),
                s(HapticPrimitive.TICK, 0.4f),
                s(HapticPrimitive.CLICK, 0.55f, 20)));
        PATTERNS.put(HapticEvent.FOCUS_FAILED, pattern(HapticPredefined.DOUBLE_CLICK,
                w(new long[]{0, 12, 30, 12}, new int[]{0, 200, 0, 200}),
                s(HapticPrimitive.LOW_TICK, 0.9f)));
        PATTERNS.put(HapticEvent.RECORD_START, pattern(HapticPredefined.HEAVY_CLICK,
                w(new long[]{0, 10, 25, 15}, new int[]{0, 180, 0, 255}),
                s(HapticPrimitive.QUICK_RISE, 0.7f),
                s(HapticPrimitive.THUD, 1.0f, 35)));
        PATTERNS.put(HapticEvent.RECORD_STOP, pattern(HapticPredefined.HEAVY_CLICK,
                w(new long[]{0, 14, 20, 12}, new int[]{0, 255, 0, 180}),
                s(HapticPrimitive.THUD, 1.0f),
                s(HapticPrimitive.LOW_TICK, 0.8f, 25)));
        PATTERNS.put(HapticEvent.COUNTDOWN_TICK, pattern(HapticPredefined.TICK,
                w(new long[]{0, 6}, new int[]{0, 170}),
                s(HapticPrimitive.TICK, 0.5f)));
        PATTERNS.put(HapticEvent.COUNTDOWN_FINAL, pattern(HapticPredefined.HEAVY_CLICK,
                w(new long[]{0, 8, 20, 14}, new int[]{0, 200, 0, 255}),
                s(HapticPrimitive.CLICK, 0.6f),
                s(HapticPrimitive.THUD, 1.0f, 20)));
        PATTERNS.put(HapticEvent.ZOOM_DETENT, pattern(HapticPredefined.TICK,
                w(new long[]{0, 4}, new int[]{0, 110}),
                s(HapticPrimitive.TICK, 0.3f)));
        PATTERNS.put(HapticEvent.LENS_SWITCH, pattern(HapticPredefined.CLICK,
                w(new long[]{0, 8, 18, 6}, new int[]{0, 180, 0, 140}),
                s(HapticPrimitive.CLICK, 0.6f),
                s(HapticPrimitive.TICK, 0.4f, 25)));
        PATTERNS.put(HapticEvent.PAGE_SNAP, pattern(HapticPredefined.TICK,
                w(new long[]{0, 5}, new int[]{0, 120}),
                s(HapticPrimitive.TICK, 0.35f)));
        PATTERNS.put(HapticEvent.CHROME_TOGGLE, pattern(HapticPredefined.TICK,
                w(new long[]{0, 4}, new int[]{0, 110}),
                s(HapticPrimitive.TICK, 0.3f)));
        PATTERNS.put(HapticEvent.SELECT, pattern(HapticPredefined.TICK,
                w(new long[]{0, 6}, new int[]{0, 150}),
                s(HapticPrimitive.TICK, 0.5f)));
        PATTERNS.put(HapticEvent.DESELECT, pattern(HapticPredefined.TICK,
                w(new long[]{0, 6}, new int[]{0, 100}),
                s(HapticPrimitive.LOW_TICK, 0.6f)));
        PATTERNS.put(HapticEvent.CONFIRM, pattern(HapticPredefined.CLICK,
                w(new long[]{0, 8}, new int[]{0, 190}),
                s(HapticPrimitive.CLICK, 0.6f)));
        PATTERNS.put(HapticEvent.REJECT, pattern(HapticPredefined.DOUBLE_CLICK,
                w(new long[]{0, 16, 40, 16}, new int[]{0, 255, 0, 255}),
                s(HapticPrimitive.LOW_TICK, 1.0f),
                s(HapticPrimitive.LOW_TICK, 1.0f, 40)));
        PATTERNS.put(HapticEvent.ERROR, pattern(HapticPredefined.DOUBLE_CLICK,
                w(new long[]{0, 18, 50, 18}, new int[]{0, 255, 0, 255}),
                s(HapticPrimitive.LOW_TICK, 1.0f),
                s(HapticPrimitive.LOW_TICK, 1.0f, 50)));
    }

    public static HapticPattern of(HapticEvent event) {
        return PATTERNS.get(event);
    }

    private static HapticPattern pattern(HapticPredefined predefined,
                                         HapticWaveform waveform, HapticStep... steps) {
        return new HapticPattern(Arrays.asList(steps), predefined, waveform);
    }

    private static HapticStep s(HapticPrimitive primitive, float scale) {
        return HapticStep.of(primitive, scale);
    }

    private static HapticStep s(HapticPrimitive primitive, float scale, int delayMs) {
        return new HapticStep(primitive, scale, delayMs);
    }

    private static HapticWaveform w(long[] timings, int[] amplitudes) {
        return new HapticWaveform(timings, amplitudes);
    }
}
