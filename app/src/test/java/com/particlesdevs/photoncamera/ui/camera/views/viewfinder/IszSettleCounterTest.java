package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the frame counter that gates the ISZ transition crossfade.
 */
public class IszSettleCounterTest {

    @Test
    public void settlesExactlyAtThreshold() {
        IszSettleCounter counter = new IszSettleCounter(10);
        for (int i = 1; i < 10; i++) {
            assertFalse(counter.onFrame());
            assertFalse(counter.isSettled());
        }
        assertTrue(counter.onFrame());
        assertTrue(counter.isSettled());
        assertEquals(10, counter.getCount());
    }

    @Test
    public void staysSettledPastThreshold() {
        IszSettleCounter counter = new IszSettleCounter(3);
        counter.onFrame();
        counter.onFrame();
        assertTrue(counter.onFrame());
        assertTrue(counter.onFrame());
        assertEquals(3, counter.getCount());
    }

    @Test
    public void resetRearms() {
        IszSettleCounter counter = new IszSettleCounter(2);
        assertFalse(counter.onFrame());
        assertTrue(counter.onFrame());
        counter.reset();
        assertFalse(counter.isSettled());
        assertEquals(0, counter.getCount());
        assertFalse(counter.onFrame());
        assertTrue(counter.onFrame());
    }

    @Test
    public void degenerateThresholdClampsToOne() {
        IszSettleCounter counter = new IszSettleCounter(0);
        assertTrue(counter.onFrame());
    }
}
