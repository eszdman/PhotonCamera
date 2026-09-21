package com.particlesdevs.photoncamera.ui.camera.views;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ZoomHapticGateTest {

    @Test
    public void sameIndicatorStepIsRejected() {
        ZoomHapticGate gate = new ZoomHapticGate();
        assertTrue(gate.accept(2.10f));
        assertFalse(gate.accept(2.12f));
        assertFalse(gate.accept(2.14f));
        assertTrue(gate.accept(2.16f));
        assertFalse(gate.accept(2.16f));
    }

    @Test
    public void everyStepChangeIsAcceptedOnce() {
        ZoomHapticGate gate = new ZoomHapticGate();
        assertTrue(gate.accept(1.0f));
        assertTrue(gate.accept(1.1f));
        assertTrue(gate.accept(1.2f));
        assertFalse(gate.accept(1.24f));
        assertTrue(gate.accept(1.26f));
    }

    @Test
    public void primeSuppressesTheFirstTick() {
        ZoomHapticGate gate = new ZoomHapticGate();
        gate.prime(2.1f);
        assertFalse(gate.accept(2.12f));
        assertTrue(gate.accept(2.18f));
    }

    @Test
    public void resetAllowsTheNextTick() {
        ZoomHapticGate gate = new ZoomHapticGate();
        assertTrue(gate.accept(3.0f));
        assertFalse(gate.accept(3.0f));
        gate.reset();
        assertTrue(gate.accept(3.0f));
    }

    @Test
    public void integerStepsUseOneDecimalLabels() {
        ZoomHapticGate gate = new ZoomHapticGate();
        assertTrue(gate.accept(2.0f));
        assertFalse(gate.accept(2.01f));
        assertTrue(gate.accept(2.1f));
    }
}
