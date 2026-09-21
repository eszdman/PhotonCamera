package com.particlesdevs.photoncamera.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FocalEquivalenceTest {
    private static final float EPS = 1e-4f;

    @Test
    public void fullFrameHasUnitCropFactor() {
        float crop = FocalEquivalence.cropFactor(36f, 24f, 6000, 4000, 6000, 4000);
        assertEquals(1.0f, crop, 1e-3f);
        assertEquals(50f,
                FocalEquivalence.equivalent35mm(50f, 36f, 24f, 6000, 4000, 6000, 4000),
                0.05f);
    }

    @Test
    public void fourThirdsFractionSensorUsesDiagonal() {
        float crop = FocalEquivalence.cropFactor(6.4f, 4.8f, 0, 0, 0, 0);
        assertEquals(43.2666f / 8f, crop, 1e-3f);
    }

    @Test
    public void activeArrayScalesPhysicalSize() {
        float crop = FocalEquivalence.cropFactor(6.4f, 4.8f, 3968, 2976, 4000, 3000);
        float width = 6.4f * 3968f / 4000f;
        float height = 4.8f * 2976f / 3000f;
        assertEquals((float) (43.2666f / Math.hypot(width, height)), crop, 1e-3f);
    }

    @Test
    public void equivalentScalesWithFocalLength() {
        float equivalent = FocalEquivalence.equivalent35mm(4.25f, 6.4f, 4.8f, 0, 0, 0, 0);
        assertEquals(4.25f * 43.2666f / 8f, equivalent, 1e-3f);
    }

    @Test
    public void invalidInputsReturnZero() {
        assertEquals(0f, FocalEquivalence.cropFactor(0f, 4.8f, 0, 0, 0, 0), 0f);
        assertEquals(0f, FocalEquivalence.cropFactor(6.4f, -1f, 0, 0, 0, 0), 0f);
        assertEquals(0f, FocalEquivalence.equivalent35mm(0f, 6.4f, 4.8f, 0, 0, 0, 0), 0f);
        assertEquals(0f, FocalEquivalence.iszEquivalent35mm(24f, 0f), 0f);
    }

    @Test
    public void iszMultipliesBaseEquivalent() {
        assertEquals(48f, FocalEquivalence.iszEquivalent35mm(24f, 2f), EPS);
        assertEquals(36f, FocalEquivalence.iszEquivalent35mm(24f, 1.5f), EPS);
    }
}
