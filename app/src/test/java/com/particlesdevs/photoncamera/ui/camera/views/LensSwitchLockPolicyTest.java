package com.particlesdevs.photoncamera.ui.camera.views;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LensSwitchLockPolicyTest {

    @Test
    public void logicalVideoModeIgnoresAutoSwitchOff() {
        assertFalse(LensSwitchLockPolicy.isLocked(false, false, true));
    }

    @Test
    public void logicalVideoModeIgnoresPillLock() {
        assertFalse(LensSwitchLockPolicy.isLocked(true, true, true));
    }

    @Test
    public void physicalModeLocksWhenAutoSwitchIsOff() {
        assertTrue(LensSwitchLockPolicy.isLocked(false, false, false));
        assertTrue(LensSwitchLockPolicy.isLocked(false, true, false));
    }

    @Test
    public void physicalModeUnlocksOnlyWhenBothPreferencesAllowIt() {
        assertFalse(LensSwitchLockPolicy.isLocked(true, false, false));
        assertTrue(LensSwitchLockPolicy.isLocked(true, true, false));
    }
}
