package com.particlesdevs.photoncamera.control;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocationFreshnessTest {

    private static final long MAX_AGE = 5 * 60 * 1000L;

    @Test
    public void freshFixAccepted() {
        assertTrue(LocationProvider.isFresh(1_000L, 1_000L + 60_000L, MAX_AGE));
    }

    @Test
    public void fixAtExactAgeAccepted() {
        assertTrue(LocationProvider.isFresh(1_000L, 1_000L + MAX_AGE, MAX_AGE));
    }

    @Test
    public void staleFixRejected() {
        assertFalse(LocationProvider.isFresh(1_000L, 1_000L + MAX_AGE + 1, MAX_AGE));
    }

    @Test
    public void zeroOrNegativeTimestampRejected() {
        assertFalse(LocationProvider.isFresh(0L, 1_000L, MAX_AGE));
        assertFalse(LocationProvider.isFresh(-5L, 1_000L, MAX_AGE));
    }

    @Test
    public void smallClockSkewAccepted() {
        assertTrue(LocationProvider.isFresh(2_000L, 1_000L, MAX_AGE));
    }

    @Test
    public void absurdFutureFixRejected() {
        assertFalse(LocationProvider.isFresh(1_000L + MAX_AGE + 1, 1_000L, MAX_AGE));
    }
}
