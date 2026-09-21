package com.particlesdevs.photoncamera.ui.camera.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LensLabelFormatterTest {

    private static CameraLensData lens(float zoomFactor, float equivalent35mm) {
        CameraLensData data = new CameraLensData("0");
        data.setZoomFactor(zoomFactor);
        data.setCamera35mmFocalLength(equivalent35mm);
        return data;
    }

    @Test
    public void zoomLabelsTrimSingleDecimalZero() {
        assertEquals("0.6x", LensLabelFormatter.zoomLabel(0.6f));
        assertEquals("1x", LensLabelFormatter.zoomLabel(1f));
        assertEquals("2x", LensLabelFormatter.zoomLabel(2f));
        assertEquals("3.3x", LensLabelFormatter.zoomLabel(3.3f));
        assertEquals("10x", LensLabelFormatter.zoomLabel(10f));
        assertEquals("12.5x", LensLabelFormatter.zoomLabel(12.5f));
    }

    @Test
    public void focalLabelsRoundToWholeMillimetres() {
        assertEquals("24mm", LensLabelFormatter.focalLabel(24.2f));
        assertEquals("48mm", LensLabelFormatter.focalLabel(47.6f));
        assertEquals("13mm", LensLabelFormatter.focalLabel(13.4f));
    }

    @Test
    public void mmModeUsesEquivalentFocalLength() {
        assertEquals("24mm", LensLabelFormatter.format(lens(1f, 24f), true));
        assertEquals("48mm", LensLabelFormatter.format(lens(2f, 48f), true));
    }

    @Test
    public void zoomModeIgnoresEquivalentFocalLength() {
        assertEquals("2x", LensLabelFormatter.format(lens(2f, 48f), false));
    }

    @Test
    public void mmModeFallsBackToZoomLabelWhenEquivalentMissing() {
        assertEquals("2x", LensLabelFormatter.format(lens(2f, 0f), true));
        assertEquals("2x", LensLabelFormatter.format(lens(2f, Float.NaN), true));
    }
}
