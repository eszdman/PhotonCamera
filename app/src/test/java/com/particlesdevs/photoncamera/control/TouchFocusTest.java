package com.particlesdevs.photoncamera.control;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the tap → sensor-crop geometry used by touch focus:
 * a typical back camera (sensorOrientation=90) held upright in portrait,
 * the same camera rotated to landscape, a mirrored front camera
 * (sensorOrientation=270), and boundary clamping.
 */
public class TouchFocusTest {
    private static final int VIEW_W = 1000;
    private static final int VIEW_H = 2000;
    private static final int CROP_W = 4000;
    private static final int CROP_H = 3000;
    // side = round(2 * 0.06125 * min(4000, 3000)) = 368
    private static final int SIDE = 368;

    private static int[] tap(float x, float y, int sensorOrientation, int gravityRotation, boolean mirrored) {
        return TouchFocus.mapTapToCrop(x, y, VIEW_W, VIEW_H, 0, 0, CROP_W, CROP_H,
                sensorOrientation, gravityRotation, mirrored);
    }

    @Test
    public void regionWeightIsTunedDown() {
        assertEquals(122, TouchFocus.REGION_WEIGHT);
    }

    @Test
    public void centerTapPortraitBackCameraMapsToCropCenter() {
        int[] r = tap(VIEW_W / 2f, VIEW_H / 2f, 90, 90, false);
        assertArrayEquals(new int[]{CROP_W / 2 - SIDE / 2, CROP_H / 2 - SIDE / 2, SIDE}, r);
    }

    @Test
    public void topCenterTapPortraitBackCameraMapsToCropLeftEdge() {
        // Upright portrait back camera: screen-up is sensor-left (long axis).
        int[] r = tap(VIEW_W / 2f, 0, 90, 90, false);
        assertArrayEquals(new int[]{0, CROP_H / 2 - SIDE / 2, SIDE}, r);
    }

    @Test
    public void landscapeBackCameraIsIdentity() {
        // Phone rotated so gravity reads 0: preview is upright without rotation.
        int[] topLeft = tap(0, 0, 90, 0, false);
        assertArrayEquals(new int[]{0, 0, SIDE}, topLeft);
        int[] bottomRight = tap(VIEW_W, VIEW_H, 90, 0, false);
        assertArrayEquals(new int[]{CROP_W - SIDE, CROP_H - SIDE, SIDE}, bottomRight);
    }

    @Test
    public void frontCameraPreviewIsUnmirroredBeforeRotation() {
        // Portrait front camera (sensorOrientation=270, mirrored preview):
        // a tap left of screen center lands right of crop center on the y axis.
        int[] r = tap(VIEW_W / 4f, VIEW_H / 2f, 270, 90, true);
        assertArrayEquals(new int[]{CROP_W / 2 - SIDE / 2, 2250 - SIDE / 2, SIDE}, r);
    }

    @Test
    public void regionStaysClampedInsideCrop() {
        // Portrait top-left tap lands at the sensor's bottom-left corner; clamped in-bounds.
        int[] r = tap(-500, -500, 90, 90, false);
        assertArrayEquals(new int[]{0, CROP_H - SIDE, SIDE}, r);
        r = tap(VIEW_W + 500, VIEW_H + 500, 90, 0, false);
        assertArrayEquals(new int[]{CROP_W - SIDE, CROP_H - SIDE, SIDE}, r);
    }

    @Test
    public void cropOffsetIsHonored() {
        // Zoomed crop: 1000x750 at offset (300, 200) inside the active array.
        int[] r = TouchFocus.mapTapToCrop(VIEW_W / 2f, VIEW_H / 2f, VIEW_W, VIEW_H,
                300, 200, 1000, 750, 90, 90, false);
        assertNotNull(r);
        assertEquals(300 + 1000 / 2 - r[2] / 2, r[0]);
        assertEquals(200 + 750 / 2 - r[2] / 2, r[1]);
    }

    @Test
    public void invalidDimensionsReturnNull() {
        assertNull(TouchFocus.mapTapToCrop(500, 1000, 0, VIEW_H, 0, 0, CROP_W, CROP_H, 90, 90, false));
        assertNull(TouchFocus.mapTapToCrop(500, 1000, VIEW_W, VIEW_H, 0, 0, 0, CROP_H, 90, 90, false));
    }

    // ------------------------------------------------------------------ watcher semantics (TOUCH_FOCUS_CORRECTED.md)

    private static boolean contains(int[] set, int value) {
        for (int v : set) if (v == value) return true;
        return false;
    }

    private static TouchFocus.StateWatcher armedWatcher(int[] acceptedStates) {
        TouchFocus.StateWatcher watcher = new TouchFocus.StateWatcher("test");
        watcher.arm(acceptedStates, Long.MAX_VALUE);
        return watcher;
    }

    @Test
    public void startAckSetExcludesStaleAndFailedStates() {
        // Stale INACTIVE resolves before the lens scans; NOT_FOCUSED_LOCKED must ride to the deadline.
        assertTrue(contains(TouchFocus.ACK_AFTER_START, android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN));
        assertTrue(contains(TouchFocus.ACK_AFTER_START, android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED));
        assertFalse(contains(TouchFocus.ACK_AFTER_START, android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_INACTIVE));
        assertFalse(contains(TouchFocus.ACK_AFTER_START, android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED));
    }

    @Test
    public void nullStateNeverResolvesOnlyConsumesFrames() {
        TouchFocus.StateWatcher watcher = armedWatcher(TouchFocus.ACK_AFTER_START);
        for (int i = 0; i < TouchFocus.STATE_MAX_FRAMES - 1; i++)
            assertFalse("null frame " + i + " must not resolve", watcher.onFrame(null, true));
        assertTrue(watcher.onFrame(null, true));
    }

    @Test
    public void acceptedStateResolvesOnFirstMatchingFrame() {
        TouchFocus.StateWatcher watcher = armedWatcher(TouchFocus.ACK_AFTER_START);
        assertTrue(watcher.onFrame(android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN, true));
    }

    @Test
    public void notFocusedLockedRidesToDeadlineAfterStart() {
        TouchFocus.StateWatcher watcher = armedWatcher(TouchFocus.ACK_AFTER_START);
        int failedLock = android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
        for (int i = 0; i < TouchFocus.STATE_MAX_FRAMES - 1; i++)
            assertFalse("failed lock must not fast-fail", watcher.onFrame(failedLock, true));
        assertTrue(watcher.onFrame(failedLock, true));
    }

    @Test
    public void staleRegionFrameNeverAcknowledges() {
        // A stale PASSIVE_FOCUSED/FOCUSED_LOCKED from before the region change must not
        // ack the wait (START would then lock in place without scanning).
        TouchFocus.StateWatcher watcher = armedWatcher(TouchFocus.ACK_AFTER_CANCEL_CONTINUOUS);
        int staleFocused = android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED;
        for (int i = 0; i < TouchFocus.STATE_MAX_FRAMES - 1; i++)
            assertFalse("stale-region frame must not ack", watcher.onFrame(staleFocused, false));
        assertTrue(watcher.onFrame(staleFocused, false)); // deadline only
    }

    @Test
    public void liveRegionFrameAcknowledges() {
        TouchFocus.StateWatcher watcher = armedWatcher(TouchFocus.ACK_AFTER_CANCEL_CONTINUOUS);
        int inactive = android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_INACTIVE;
        assertFalse(watcher.onFrame(inactive, false)); // stale frame: same state, no ack
        assertTrue(watcher.onFrame(inactive, true));   // region-live frame: ack
    }

    @Test
    public void resolvedStateRecordsMatchAndDeadlineDifferently() {
        // The re-arm decision keys off this: a match records the state (null ≠ INACTIVE
        // means re-arm), a deadline records null (unknown → re-arm).
        TouchFocus.StateWatcher watcher = armedWatcher(TouchFocus.ACK_AFTER_CANCEL_CONTINUOUS);
        int passiveFocused = android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED;
        assertTrue(watcher.onFrame(passiveFocused, true));
        assertEquals(Integer.valueOf(passiveFocused), watcher.resolvedState());

        watcher = armedWatcher(TouchFocus.ACK_AFTER_START);
        for (int i = 0; i < TouchFocus.STATE_MAX_FRAMES; i++)
            watcher.onFrame(android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN, true);
        assertNull(watcher.resolvedState()); // PASSIVE_SCAN never matches → deadline
    }

    @Test
    public void cancelAckSetsDifferPerMode() {
        int passiveFocused = android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED;
        assertTrue(contains(TouchFocus.ACK_AFTER_CANCEL_CONTINUOUS, passiveFocused));
        assertFalse(contains(TouchFocus.ACK_AFTER_CANCEL_AUTO, passiveFocused));
        // PASSIVE_UNFOCUSED is in BOTH sets: the reference lists it for AUTO, and
        // HALs that cannot settle report it indefinitely after a CANCEL in continuous.
        int passiveUnfocused = android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_PASSIVE_UNFOCUSED;
        assertTrue(contains(TouchFocus.ACK_AFTER_CANCEL_CONTINUOUS, passiveUnfocused));
        assertTrue(contains(TouchFocus.ACK_AFTER_CANCEL_AUTO, passiveUnfocused));
        assertTrue(contains(TouchFocus.ACK_AFTER_CANCEL_CONTINUOUS,
                android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED));
        assertTrue(contains(TouchFocus.ACK_AFTER_CANCEL_AUTO,
                android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_INACTIVE));
    }

    // ------------------------------------------------------------------ passive phase (scan-cycle divergence)

    private static final int PASSIVE_SCAN = android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN;
    private static final int PASSIVE_FOCUSED = android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED;

    @Test
    public void passivePhaseWaitsForObservedScanToSettle() {
        // Scan observed → the lock waits for it to leave PASSIVE_SCAN.
        assertFalse(TouchFocus.passivePhaseComplete(PASSIVE_SCAN, true, true, false));
        assertTrue(TouchFocus.passivePhaseComplete(PASSIVE_FOCUSED, true, false, false));
    }

    @Test
    public void passivePhaseNeverLocksMidScanEvenPastDwell() {
        // No scan seen yet, dwell expired, but a scan just started: let it run.
        assertFalse(TouchFocus.passivePhaseComplete(PASSIVE_SCAN, false, true, false));
        // Dwell fallback applies only once settled (or state unreported).
        assertTrue(TouchFocus.passivePhaseComplete(PASSIVE_FOCUSED, false, true, false));
        assertTrue(TouchFocus.passivePhaseComplete(null, false, true, false));
    }

    @Test
    public void passivePhaseBeforeDwellOrBudgetDoesNotStart() {
        assertFalse(TouchFocus.passivePhaseComplete(PASSIVE_FOCUSED, false, false, false));
        assertFalse(TouchFocus.passivePhaseComplete(null, true, false, false));
        assertTrue(TouchFocus.passivePhaseComplete(PASSIVE_SCAN, false, false, true)); // budget always wins
    }

    @Test
    public void autoFallbackEngagesOnlyAtThresholdWithSupport() {
        assertFalse(TouchFocus.shouldEngageAutoFallback(0, true));
        assertFalse(TouchFocus.shouldEngageAutoFallback(TouchFocus.AUTO_FALLBACK_TAPS - 1, true));
        assertTrue(TouchFocus.shouldEngageAutoFallback(TouchFocus.AUTO_FALLBACK_TAPS, true));
        assertTrue(TouchFocus.shouldEngageAutoFallback(100, true));
        // AUTO mode unsupported on the HAL: never engage.
        assertFalse(TouchFocus.shouldEngageAutoFallback(100, false));
    }
}
