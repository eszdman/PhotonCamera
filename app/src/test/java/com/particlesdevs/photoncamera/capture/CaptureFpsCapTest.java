package com.particlesdevs.photoncamera.capture;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class CaptureFpsCapTest {

    @Test
    public void fixedRangeAboveCap_collapsesToCap() {
        assertArrayEquals(new int[]{30, 30}, CaptureFpsCap.clamp(60, 60, 30));
    }

    @Test
    public void fixedRangeBelowCap_unchanged() {
        assertArrayEquals(new int[]{24, 24}, CaptureFpsCap.clamp(24, 24, 30));
    }

    @Test
    public void autoRange_capsUpperBoundOnly() {
        assertArrayEquals(new int[]{14, 24}, CaptureFpsCap.clamp(14, 30, 24));
    }

    @Test
    public void autoRangeWithinCap_unchanged() {
        assertArrayEquals(new int[]{14, 30}, CaptureFpsCap.clamp(14, 30, 30));
    }

    @Test
    public void fixedRangeBelowCap60_unchanged() {
        assertArrayEquals(new int[]{60, 60}, CaptureFpsCap.clamp(60, 60, 60));
    }
}
