package com.particlesdevs.photoncamera.capture;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the lens-aware zoom state machine in {@link ZoomController}.
 *
 * <p>The controller is the single source of truth for (a) which physical lens is
 * active and (b) the digital crop applied to it, which feeds both the preview
 * request and the stored RAW buffer (whose contents become the JPEG and DNG).
 * The geometry and the effective = native * digital invariant must be exact.</p>
 */
public class ZoomControllerTest {

    /** Simulates a 3-lens device: 0.6x ultra-wide, 1x main, 3.1x tele. */
    private static final List<ZoomController.LensEntry> LENS_3X =
            Arrays.asList(
                    // UW must be able to crop up to ~1x to bridge to the main lens.
                    new ZoomController.LensEntry("uw", 0.6f, 3.0f),
                    new ZoomController.LensEntry("main", 1.0f, 8.0f),
                    new ZoomController.LensEntry("tele", 3.1f, 3.0f));

    private ZoomController zoom;
    private String switchedTo;

    @Before
    public void setUp() {
        zoom = new ZoomController();
        zoom.setLenses(LENS_3X);
        zoom.setLensSwitchListener(id -> switchedTo = id);
        // Mimic configureZoomLenses(): the camera reopens on the main lens.
        zoom.setActiveLens("main");
    }

    // --- Lens selection & threshold crossing ---

    @Test
    public void defaultStateIsUnzoomedOnMainLens() {
        assertEquals(1.0f, zoom.getZoomRatio(), 1e-4f);
        assertFalse(zoom.isZoomed());
        assertNull(switchedTo);
    }

    @Test
    public void zoomingInPastTeleCrossesLens() {
        zoom.setTargetZoom(1.5f, 0.5f, 0.5f);
        assertNull(switchedTo); // still on main lens (digital)
        assertEquals("main", zoom.getActiveLensId());
        assertEquals(1.5f, zoom.getDigitalZoom(), 1e-4f);
    }

    @Test
    public void zoomingToTeleNativeSwitchesLens() {
        zoom.setTargetZoom(3.1f, 0.5f, 0.5f);
        assertEquals("tele", switchedTo);
        assertEquals("tele", zoom.getActiveLensId());
        assertEquals(1.0f, zoom.getDigitalZoom(), 1e-4f); // native, no crop
    }

    @Test
    public void zoomingOutBelowMainSwitchesToUltraWide() {
        // 0.8 is between 0.6 (UW) and 1.0 (main).
        zoom.setTargetZoom(0.8f, 0.5f, 0.5f);
        assertEquals("uw", switchedTo);
        assertEquals("uw", zoom.getActiveLensId());
        // The switch defers the crop; once the camera reopens on the UW lens the
        // target (0.8) is re-expressed as a digital crop (0.8 / 0.6 = 1.33).
        zoom.setActiveLens("uw");
        assertEquals(0.8f / 0.6f, zoom.getDigitalZoom(), 1e-4f);
    }

    @Test
    public void switchingBackToMainFromUltraWideReexpresses() {
        zoom.setTargetZoom(0.7f, 0.5f, 0.5f);
        assertEquals("uw", switchedTo);
        // Simulate reopen on the uw lens.
        zoom.setActiveLens("uw");
        zoom.setTargetZoom(1.0f, 0.5f, 0.5f);
        assertEquals("main", switchedTo);
        assertEquals("main", zoom.getActiveLensId());
        assertEquals(1.0f, zoom.getDigitalZoom(), 1e-4f);
    }

    // --- range / clamping ---

    @Test
    public void minZoomIsUltraWideNative() {
        assertEquals(0.6f, zoom.getMinZoom(), 1e-4f);
    }

    @Test
    public void maxZoomIsTeleNativeTimesDigital() {
        // The strongest lens is tele (native 3.1) with max digital 3.0 -> 9.3.
        assertEquals(3.1f * 3.0f, zoom.getMaxZoom(), 1e-4f);
    }

    @Test
    public void zoomIsClampedBelowMin() {
        zoom.setTargetZoom(0.1f, 0.5f, 0.5f);
        assertEquals(0.6f, zoom.getZoomRatio(), 1e-4f);
    }

    @Test
    public void zoomIsClampedAboveMax() {
        zoom.setTargetZoom(100f, 0.5f, 0.5f);
        assertEquals(9.3f, zoom.getZoomRatio(), 1e-4f);
    }

    @Test
    public void digitalZoomRespectsLensMax() {
        // tele native 3.1 with max digital 3.0 -> effective clamped to 9.3.
        zoom.setTargetZoom(3.1f, 0.5f, 0.5f);
        assertEquals("tele", switchedTo);
        zoom.setActiveLens("tele");
        // Digital zoom within the tele lens never exceeds 3.0.
        zoom.setTargetZoom(9.3f, 0.5f, 0.5f);
        assertEquals("tele", zoom.getActiveLensId());
        assertEquals(3.0f, zoom.getDigitalZoom(), 1e-4f);
    }

    // --- detent / stickiness ---

    @Test
    public void detentSnapsNearTeleNative() {
        // 3.06 is within 3% of 3.1 -> snaps to exactly 3.1, tele with no crop.
        zoom.setTargetZoom(3.06f, 0.5f, 0.5f);
        assertEquals("tele", switchedTo);
        assertEquals(3.1f, zoom.getZoomRatio(), 1e-4f);
        assertEquals(1.0f, zoom.getDigitalZoom(), 1e-4f);
    }

    @Test
    public void detentIsSkippedWellOutsideWindow() {
        // 3.5 is >3% away from 3.1 -> no snap.
        zoom.setTargetZoom(3.5f, 0.5f, 0.5f);
        assertEquals("tele", switchedTo);
        assertEquals(3.5f, zoom.getZoomRatio(), 1e-4f);
        // Reopen re-expresses the crop on the tele lens.
        zoom.setActiveLens("tele");
        assertEquals(3.5f / 3.1f, zoom.getDigitalZoom(), 1e-4f);
    }

    @Test
    public void detentSnapsNearUltraWideNative() {
        zoom.setTargetZoom(0.6f, 0.5f, 0.5f);
        assertEquals("uw", switchedTo);
        assertEquals(0.6f, zoom.getZoomRatio(), 1e-4f);
        assertEquals(1.0f, zoom.getDigitalZoom(), 1e-4f);
    }

    // --- reset semantics ---

    @Test
    public void resetToActiveLensNativeKeepsLens() {
        zoom.setTargetZoom(1.5f, 0.5f, 0.5f); // digital on main
        // Simulate the camera still being on main.
        zoom.setActiveLens("main");
        zoom.resetToActiveLensNative();
        assertEquals("main", zoom.getActiveLensId());
        assertEquals(1.0f, zoom.getZoomRatio(), 1e-4f);
        assertEquals(1.0f, zoom.getDigitalZoom(), 1e-4f);
    }

    @Test
    public void plainResetGoesToUnzoomed() {
        zoom.setTargetZoom(0.7f, 0.2f, 0.8f);
        zoom.reset();
        assertEquals(1.0f, zoom.getZoomRatio(), 1e-4f);
        assertFalse(zoom.isZoomed());
        assertEquals(0.5f, zoom.getFocusX(), 1e-4f);
        assertEquals(0.5f, zoom.getFocusY(), 1e-4f);
    }

    @Test
    public void noLensesClampsToSingleZoom() {
        // Without a lens model (before configureZoomLenses runs), the controller
        // has no range and clamps everything to 1.0x.
        ZoomController fresh = new ZoomController();
        fresh.setTargetZoom(4.0f, 0.5f, 0.5f);
        assertEquals(1.0f, fresh.getZoomRatio(), 1e-4f);
        assertFalse(fresh.isZoomed());
    }

    // --- pure crop math (unchanged semantics) ---

    @Test
    public void centeredCropHalvesSizeAtDigitalTwoX() {
        int[] v = ZoomController.computeCropValues(4000, 3000, 2.0f, 0.5f, 0.5f);
        assertEquals(1000, v[0]);
        assertEquals(750, v[1]);
        assertEquals(2000, v[2]);
        assertEquals(1500, v[3]);
    }

    @Test
    public void offsetFocusMovesCropButStaysInBounds() {
        int[] v = ZoomController.computeCropValues(4000, 3000, 2.0f, 0.0f, 0.0f);
        assertEquals(0, v[0]);
        assertEquals(0, v[1]);
        assertEquals(2000, v[2]);
        assertEquals(1500, v[3]);
    }

    @Test
    public void focusAtBottomRightClampsToBounds() {
        int[] v = ZoomController.computeCropValues(4000, 3000, 2.0f, 1.0f, 1.0f);
        assertEquals(2000, v[0]);
        assertEquals(1500, v[1]);
        assertEquals(2000, v[2]);
        assertEquals(1500, v[3]);
    }

    @Test
    public void cropCoordinatesAreEvenAligned() {
        int[] v = ZoomController.computeCropValues(4032, 3024, 1.5f, 0.37f, 0.61f);
        assertEquals(0, v[0] % 2);
        assertEquals(0, v[1] % 2);
        assertEquals(0, v[2] % 2);
        assertEquals(0, v[3] % 2);
    }

    @Test
    public void unzoomedCropIsFullFrame() {
        int[] v = ZoomController.computeCropValues(4000, 3000, 1.0f, 0.5f, 0.5f);
        assertEquals(0, v[0]);
        assertEquals(0, v[1]);
        assertEquals(4000, v[2]);
        assertEquals(3000, v[3]);
    }

    // --- crop region / sensor-relative metadata ---

    @Test
    public void cropRegionMatchesPreviewWhenZoomed() {
        zoom.setTargetZoom(2.0f, 0.5f, 0.5f);
        assertEquals("main", zoom.getActiveLensId());
        assertEquals(2.0f, zoom.getDigitalZoom(), 1e-4f);
        // The buffer crop (primitives) is what fromCrop uses; assert it equals the
        // same region the preview's SCALER_CROP_REGION is built from.
        int[] v = ZoomController.computeCropValues(4000, 3000, zoom.getDigitalZoom(), 0.5f, 0.5f);
        // 2x crop of 4000x3000 centered -> 2000x1500 at (1000,750).
        assertEquals(1000, v[0]);
        assertEquals(750, v[1]);
        assertEquals(2000, v[2]);
        assertEquals(1500, v[3]);
    }

    @Test
    public void cropRegionIsFullFrameWhenUnzoomed() {
        zoom.setTargetZoom(1.0f, 0.5f, 0.5f);
        assertEquals(1.0f, zoom.getDigitalZoom(), 1e-4f);
        int[] v = ZoomController.computeCropValues(4000, 3000, zoom.getDigitalZoom(), 0.5f, 0.5f);
        assertEquals(0, v[0]);
        assertEquals(0, v[1]);
        assertEquals(4000, v[2]);
        assertEquals(3000, v[3]);
    }

    @Test
    public void cropCenterMapsToFullFrameCenter() {
        zoom.setTargetZoom(2.0f, 0.5f, 0.5f);
        int[] v = ZoomController.computeCropValues(4000, 3000, zoom.getDigitalZoom(), 0.5f, 0.5f);
        int centerX = v[0] + v[2] / 2;
        int centerY = v[1] + v[3] / 2;
        assertTrue(Math.abs(centerX - 2000) <= 1);
        assertTrue(Math.abs(centerY - 1500) <= 1);
        assertTrue(v[0] + v[2] <= 4000);
        assertTrue(v[1] + v[3] <= 3000);
    }
}
