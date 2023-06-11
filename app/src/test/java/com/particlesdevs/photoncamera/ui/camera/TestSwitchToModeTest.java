package com.particlesdevs.photoncamera.ui.camera;

import com.particlesdevs.photoncamera.api.CameraMode;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test : {@link CameraUIViewImpl in switchToMode(CameraMode)}
 * Purpose : Apply the State pattern and verify whether the mode transition is successful.
 */
public class TestSwitchToModeTest {

    private TestSwitchToMode testSwitchToMode;

    @Before
    public void setup() {
        testSwitchToMode = new TestSwitchToMode();
        /** Expected: "Now setting mode" **/
    }

    /**
     * Purpose: switch the SwitchToMode according to the mode
     * Input:
     *      switchToMode(CameraMode.PHOTO)
     *      currentState = new PhotoMotionModeState()
     *      currentState.reConfigureModeViews(cameraMode)
     *      toggleConstraints(mode);
     * Expected:
     *      "Now setting mode
     *      Photo mode switched"
     */
    @Test
    public void testSwitchToMode_PhotoMode() {
        CameraMode cameraMode = CameraMode.PHOTO;
        testSwitchToMode.switchToMode(cameraMode);
        assertEquals(testSwitchToMode.currentState.getClass(), TestSwitchToMode.PhotoMotionModeState.class);

    }

    /**
     * Purpose: switch the SwitchToMode according to the mode
     * Input:
     *      switchToMode(CameraMode.VIDEO)
     *      currentState = new VideoModeState()
     *      currentState.reConfigureModeViews(cameraMode)
     *      toggleConstraints(mode);
     * Expected:
     *      "Now setting mode
     *      Video mode switched"
     */
    @Test
    public void testSwitchToMode_VideoMode() {
        CameraMode cameraMode = CameraMode.VIDEO;
        testSwitchToMode.switchToMode(cameraMode);
        assertEquals(testSwitchToMode.currentState.getClass(), TestSwitchToMode.VideoModeState.class);

    }

    /**
     * Purpose: switch the SwitchToMode according to the mode
     * Input:
     *      switchToMode(CameraMode.UNLIMITED)
     *      currentState = new UnlimitedModeState()
     *      currentState.reConfigureModeViews(cameraMode)
     *      toggleConstraints(mode);
     * Expected:
     *      "Now setting mode
     *      Unlimited mode switched"
     */
    @Test
    public void testSwitchToMode_UnlimitedMode() {
        CameraMode cameraMode = CameraMode.UNLIMITED;
        testSwitchToMode.switchToMode(cameraMode);
        assertEquals(testSwitchToMode.currentState.getClass(), TestSwitchToMode.UnlimitedModeState.class);

    }

    /**
     * Purpose: switch the SwitchToMode according to the mode
     * Input:
     *      switchToMode(CameraMode.NIGHT)
     *      currentState = new NightModeState()
     *      currentState.reConfigureModeViews(cameraMode)
     *      toggleConstraints(mode);
     * Expected:
     *      "Now setting mode
     *      Night mode switched"
     */
    @Test
    public void testSwitchToMode_NightMode() {
        CameraMode cameraMode = CameraMode.NIGHT;
        testSwitchToMode.switchToMode(cameraMode);
        assertEquals(testSwitchToMode.currentState.getClass(), TestSwitchToMode.NightModeState.class);

    }
}
