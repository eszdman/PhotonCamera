package com.particlesdevs.photoncamera.control;

import org.junit.Test;
import static org.junit.Assert.*;

public class GyroHistoryTest {
    private static final long MS = 1_000_000L;

    private GyroHistory constant(int samples, int capacity, float z) {
        GyroHistory history = new GyroHistory(capacity);
        for (int i = 0; i < samples; i++) history.add(i*10*MS, 0, 0, z);
        return history;
    }

    @Test public void usesExposureStartAndSkewMidpointWithClippedIntervals() {
        GyroBurst burst = constant(10, 20, 2).snapshot(15*MS, 20*MS, 10*MS, true);
        assertTrue(burst.orientationValid);
        assertEquals(30*MS, burst.orientationTimestamp);
        assertEquals(4, burst.samples);
        assertEquals(20*MS, burst.timestampss[0]);
        assertEquals(45*MS, burst.timestampss[3]);
        assertEquals(0.06, burst.shakiness, 1e-7);
        assertArrayEquals(GyroMotion.delta(0,0,0.06), burst.orientation, 1e-10);
    }

    @Test public void preservesRotationBetweenExposuresAndFinalFrame() {
        GyroHistory h = constant(11, 20, 1);
        GyroBurst first = h.snapshot(10*MS, 10*MS, 0, true);
        GyroBurst last = h.snapshot(90*MS, 10*MS, 0, true);
        assertTrue(last.orientationValid);
        double firstAngle = 2*Math.atan2(first.orientation[3],first.orientation[0]);
        double lastAngle = 2*Math.atan2(last.orientation[3],last.orientation[0]);
        assertEquals(0.08, lastAngle-firstAngle, 1e-10);
        assertEquals(100*MS, last.timestampss[last.samples-1]);
    }

    @Test public void interpolatesAngularVelocityAtMidpoint() {
        GyroHistory h = new GyroHistory(8);
        h.add(0,0,0,0);
        h.add(100*MS,0,0,2);
        GyroBurst b = h.snapshot(0,100*MS,0,true);
        assertEquals(0.025, 2*Math.atan2(b.orientation[3],b.orientation[0]),1e-10);
        assertEquals(0.1,b.shakiness,1e-7);
    }

    @Test public void chronologicalWindowSurvivesRingWrap() {
        GyroHistory h = constant(10, 4, 1);
        GyroBurst b = h.snapshot(65*MS,20*MS,0,true);
        assertTrue(b.orientationValid);
        assertEquals(70*MS,b.timestampss[0]);
        assertEquals(85*MS,b.timestampss[b.samples-1]);
        assertFalse(h.snapshot(10*MS,10*MS,0,true).orientationValid);
    }

    @Test public void rejectsUnknownClockUncoveredWindowsAndGaps() {
        GyroHistory h = constant(10,20,1);
        assertFalse(h.snapshot(20*MS,10*MS,0,false).orientationValid);
        assertFalse(h.snapshot(85*MS,20*MS,0,true).orientationValid);
        GyroBurst before = h.snapshot(20*MS,10*MS,0,true);
        h.add(500*MS,0,0,1);
        h.add(510*MS,0,0,1);
        assertFalse(h.snapshot(80*MS,430*MS,0,true).orientationValid);
        GyroBurst after = h.snapshot(500*MS,10*MS,0,true);
        assertTrue(after.orientationValid);
        assertNotEquals(before.orientationSegment,after.orientationSegment);
    }

    @Test public void snapshotsAndClonesOwnTheirArrays() {
        GyroHistory h = constant(10,20,1);
        GyroBurst first = h.snapshot(10*MS,20*MS,0,true);
        GyroBurst copy = first.clone();
        copy.movementss[2][0] = 42;
        copy.timestampss[0] = 42;
        copy.orientation[0] = 42;
        h.add(100*MS,0,0,3);
        assertEquals(0.01,first.movementss[2][0],1e-7);
        assertEquals(20*MS,first.timestampss[0]);
        assertNotEquals(42,first.orientation[0],0);
    }
}
