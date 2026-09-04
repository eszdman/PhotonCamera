package com.particlesdevs.photoncamera.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the logarithmic slider &lt;-&gt; zoom mapping used by the
 * expanding lens pill ({@code lens_zoom_bar}).
 */
public class ZoomSliderMapperTest {

    private static final int MAX = 1000;
    private static final float MIN = 0.6f;
    private static final float MAX_ZOOM = 9.3f;

    @Test
    public void endpointsMapExactly() {
        assertEquals(MIN, ZoomSliderMapper.progressToZoom(0, MAX, MIN, MAX_ZOOM), 1e-4f);
        assertEquals(MAX_ZOOM, ZoomSliderMapper.progressToZoom(MAX, MAX, MIN, MAX_ZOOM), 1e-4f);
        assertEquals(0, ZoomSliderMapper.zoomToProgress(MIN, MAX, MIN, MAX_ZOOM));
        assertEquals(MAX, ZoomSliderMapper.zoomToProgress(MAX_ZOOM, MAX, MIN, MAX_ZOOM));
    }

    @Test
    public void midpointIsGeometricMean() {
        float mid = ZoomSliderMapper.progressToZoom(MAX / 2, MAX, MIN, MAX_ZOOM);
        assertEquals((float) Math.sqrt(MIN * MAX_ZOOM), mid, 1e-3f);
    }

    @Test
    public void roundTripIsStable() {
        float[] zooms = {0.6f, 1.0f, 1.5f, 3.1f, 5.0f, 9.3f};
        for (float zoom : zooms) {
            int progress = ZoomSliderMapper.zoomToProgress(zoom, MAX, MIN, MAX_ZOOM);
            float back = ZoomSliderMapper.progressToZoom(progress, MAX, MIN, MAX_ZOOM);
            // One slider step at the top of the range is the worst case.
            assertEquals(zoom, back, MAX_ZOOM * 0.01f);
        }
    }

    @Test
    public void progressIsMonotonicInZoom() {
        int last = -1;
        for (float zoom = MIN; zoom <= MAX_ZOOM; zoom *= 1.1f) {
            int progress = ZoomSliderMapper.zoomToProgress(zoom, MAX, MIN, MAX_ZOOM);
            assertTrue(progress >= last);
            last = progress;
        }
    }

    @Test
    public void inputsAreClamped() {
        assertEquals(MIN, ZoomSliderMapper.progressToZoom(-50, MAX, MIN, MAX_ZOOM), 1e-4f);
        assertEquals(MAX_ZOOM, ZoomSliderMapper.progressToZoom(MAX + 50, MAX, MIN, MAX_ZOOM), 1e-4f);
        assertEquals(0, ZoomSliderMapper.zoomToProgress(0.1f, MAX, MIN, MAX_ZOOM));
        assertEquals(MAX, ZoomSliderMapper.zoomToProgress(100f, MAX, MIN, MAX_ZOOM));
    }

    @Test
    public void degenerateRangeIsSafe() {
        assertEquals(1.0f, ZoomSliderMapper.progressToZoom(500, MAX, 1.0f, 1.0f), 1e-4f);
        assertEquals(0, ZoomSliderMapper.zoomToProgress(2.0f, MAX, 1.0f, 1.0f));
        assertEquals(MIN, ZoomSliderMapper.progressToZoom(500, 0, MIN, MAX_ZOOM), 1e-4f);
    }
}
