package com.particlesdevs.photoncamera.circularbarlib.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AutoValueComposerTest {
    private static final String AUTO = "A";

    @Test
    public void appendsValueToAutoLabel() {
        assertEquals("A 800", AutoValueComposer.compose(AUTO, "800", AUTO));
        assertEquals("A 1/125", AutoValueComposer.compose(AUTO, "1/125", AUTO));
        assertEquals("A 4500K", AutoValueComposer.compose(AUTO, "4500K", AUTO));
    }

    @Test
    public void manualSelectionKeepsItsOwnText() {
        assertEquals("4500K", AutoValueComposer.compose("4500K", "5000K", AUTO));
        assertEquals("1/125", AutoValueComposer.compose("1/125", "1/60", AUTO));
    }

    @Test
    public void nonAutoLabelIsNeverSuffixed() {
        assertEquals("Fixed", AutoValueComposer.compose("Fixed", "Inf", AUTO));
    }

    @Test
    public void missingValueKeepsPlainAuto() {
        assertEquals(AUTO, AutoValueComposer.compose(AUTO, null, AUTO));
        assertEquals(AUTO, AutoValueComposer.compose(AUTO, "", AUTO));
    }

    @Test
    public void nullInputsAreSafe() {
        assertEquals("", AutoValueComposer.compose(null, "800", AUTO));
        assertEquals("A", AutoValueComposer.compose(AUTO, "800", null));
    }
}
