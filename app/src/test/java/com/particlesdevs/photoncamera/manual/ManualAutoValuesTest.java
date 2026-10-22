package com.particlesdevs.photoncamera.manual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ManualAutoValuesTest {

    @Test
    public void exposureUsesShutterKnobFormattingWithSecondsSuffix() {
        assertEquals("1/125s", ManualAutoValues.formatExposure(8_000_000L));
        assertEquals("1.0s", ManualAutoValues.formatExposure(1_000_000_000L));
        assertNull(ManualAutoValues.formatExposure(null));
        assertNull(ManualAutoValues.formatExposure(0L));
    }

    @Test
    public void isoIsBareNumber() {
        assertEquals("800", ManualAutoValues.formatIso(800));
        assertEquals("12800", ManualAutoValues.formatIso(12800));
        assertNull(ManualAutoValues.formatIso(null));
        assertNull(ManualAutoValues.formatIso(0));
    }

    @Test
    public void focusIsMetresAndInfinity() {
        assertEquals("Inf", ManualAutoValues.formatFocus(0f));
        assertEquals("Inf", ManualAutoValues.formatFocus(-1f));
        assertEquals("2.0m", ManualAutoValues.formatFocus(0.5f));
        assertEquals("0.5m", ManualAutoValues.formatFocus(2f));
        assertNull(ManualAutoValues.formatFocus(null));
    }

    @Test
    public void wbIsKelvinWithinRange() {
        assertEquals("4500K", ManualAutoValues.formatWb(4500));
        assertEquals("2000K", ManualAutoValues.formatWb(2000));
        assertEquals("10000K", ManualAutoValues.formatWb(10000));
        assertNull(ManualAutoValues.formatWb(null));
        assertNull(ManualAutoValues.formatWb(1500));
        assertNull(ManualAutoValues.formatWb(12000));
    }
}
