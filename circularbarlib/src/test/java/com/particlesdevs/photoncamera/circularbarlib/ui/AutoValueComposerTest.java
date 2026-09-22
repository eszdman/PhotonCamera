package com.particlesdevs.photoncamera.circularbarlib.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoValueComposerTest {
    private static final String AUTO = "A";

    @Test
    public void untouchedShowsLiveValueOnly() {
        assertEquals("800", AutoValueComposer.compose(AUTO, "800", AUTO));
        assertEquals("1/125", AutoValueComposer.compose(AUTO, "1/125", AUTO));
        assertEquals("4500K", AutoValueComposer.compose(AUTO, "4500K", AUTO));
    }

    @Test
    public void touchedReturnsPlainManualText() {
        assertEquals("4500K", AutoValueComposer.compose("4500K", "5000K", AUTO));
        assertEquals("1/125", AutoValueComposer.compose("1/125", "1/60", AUTO));
        assertEquals("+0.50", AutoValueComposer.compose("+0.50", "0", AUTO));
    }

    @Test
    public void isTouchedDetectsManualState() {
        assertFalse(AutoValueComposer.isTouched(AUTO, AUTO));
        assertTrue(AutoValueComposer.isTouched("4500K", AUTO));
        assertTrue(AutoValueComposer.isTouched("Fixed", AUTO));
        assertFalse(AutoValueComposer.isTouched(null, AUTO));
        assertFalse(AutoValueComposer.isTouched(AUTO, null));
    }

    @Test
    public void missingLiveValueLeavesCellEmpty() {
        assertEquals("", AutoValueComposer.compose(AUTO, null, AUTO));
        assertEquals("", AutoValueComposer.compose(AUTO, "", AUTO));
    }

    @Test
    public void nullInputsAreSafe() {
        assertEquals("", AutoValueComposer.compose(null, "800", AUTO));
        assertEquals(AUTO, AutoValueComposer.compose(AUTO, "800", null));
    }
}
