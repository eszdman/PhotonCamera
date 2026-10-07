package com.particlesdevs.photoncamera.control.haptics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

public class HapticsResolverTest {

    @Test
    public void noVibratorResolvesToNone() {
        HapticsResolver.Resolved resolved =
                HapticsResolver.resolve(HapticEvent.CAPTURE_COMPLETE, HapticCapabilities.none());
        assertEquals(HapticsResolver.Backend.NONE, resolved.backend);
        assertTrue(resolved.steps.isEmpty());
    }

    @Test
    public void composedCapabilitiesUseComposition() {
        HapticsResolver.Resolved resolved =
                HapticsResolver.resolve(HapticEvent.CAPTURE_COMPLETE, HapticCapabilities.composed());
        assertEquals(HapticsResolver.Backend.COMPOSITION, resolved.backend);
        assertFalse(resolved.steps.isEmpty());
        assertEquals(HapticPredefined.NONE, resolved.predefined);
    }

    @Test
    public void predefinedFallbackWhenNoPrimitivesSupported() {
        HapticsResolver.Resolved resolved =
                HapticsResolver.resolve(HapticEvent.CAPTURE_COMPLETE, HapticCapabilities.predefined());
        assertEquals(HapticsResolver.Backend.PREDEFINED, resolved.backend);
        assertEquals(HapticPredefined.DOUBLE_CLICK, resolved.predefined);
    }

    @Test
    public void legacyCapabilitiesUseWaveform() {
        HapticsResolver.Resolved resolved =
                HapticsResolver.resolve(HapticEvent.CAPTURE_COMPLETE, HapticCapabilities.legacy());
        assertEquals(HapticsResolver.Backend.WAVEFORM, resolved.backend);
        assertNotNull(resolved.waveform);
    }

    @Test
    public void substitutesUnsupportedPrimitives() {
        HapticCapabilities caps = new HapticCapabilities(true, true, true,
                EnumSet.of(HapticPrimitive.TICK, HapticPrimitive.CLICK));
        HapticsResolver.Resolved resolved = HapticsResolver.resolve(HapticEvent.CAPTURE_COMPLETE, caps);
        assertEquals(HapticsResolver.Backend.COMPOSITION, resolved.backend);
        assertEquals(2, resolved.steps.size());
        for (HapticStep step : resolved.steps) {
            assertEquals(HapticPrimitive.TICK, step.primitive);
        }
    }

    @Test
    public void substitutionPreservesScaleAndDelay() {
        HapticCapabilities caps = new HapticCapabilities(true, true, true,
                EnumSet.of(HapticPrimitive.TICK));
        HapticsResolver.Resolved resolved = HapticsResolver.resolve(HapticEvent.CAPTURE_COMPLETE, caps);
        List<HapticStep> steps = resolved.steps;
        assertEquals(2, steps.size());
        assertEquals(1.0f, steps.get(0).scale, 1e-4f);
        assertEquals(0, steps.get(0).delayMs);
        assertEquals(0.5f, steps.get(1).scale, 1e-4f);
        assertEquals(30, steps.get(1).delayMs);
    }

    @Test
    public void dropsCompositionAndFallsBackToPredefinedWhenNothingMatches() {
        HapticCapabilities caps = new HapticCapabilities(true, true, true,
                EnumSet.of(HapticPrimitive.QUICK_FALL));
        HapticsResolver.Resolved resolved = HapticsResolver.resolve(HapticEvent.CAPTURE_COMPLETE, caps);
        assertEquals(HapticsResolver.Backend.PREDEFINED, resolved.backend);
        assertEquals(HapticPredefined.DOUBLE_CLICK, resolved.predefined);
    }

    @Test
    public void everyEventHasStepsPredefinedAndWaveformFallbacks() {
        for (HapticEvent event : HapticEvent.values()) {
            HapticPattern pattern = HapticPatterns.of(event);
            assertNotNull("missing pattern for " + event, pattern);
            assertFalse("no composed steps for " + event, pattern.steps.isEmpty());
            assertTrue("no predefined fallback for " + event,
                    pattern.predefined != HapticPredefined.NONE);
            assertNotNull("no waveform fallback for " + event, pattern.waveform);

            for (HapticCapabilities caps : new HapticCapabilities[]{
                    HapticCapabilities.composed(),
                    HapticCapabilities.predefined(),
                    HapticCapabilities.legacy()}) {
                HapticsResolver.Resolved resolved = HapticsResolver.resolve(event, caps);
                assertTrue("no resolved backend for " + event,
                        resolved.backend != HapticsResolver.Backend.NONE);
            }
            assertEquals(HapticsResolver.Backend.NONE,
                    HapticsResolver.resolve(event, HapticCapabilities.none()).backend);
        }
    }

    @Test
    public void waveformTimingsAreWellFormed() {
        for (HapticEvent event : HapticEvent.values()) {
            HapticWaveform waveform = HapticPatterns.of(event).waveform;
            assertEquals(0, waveform.timingsMs.length % 2);
            assertEquals(waveform.timingsMs.length, waveform.amplitudes.length);
            assertEquals(0, waveform.timingsMs[0]);
        }
    }
}
