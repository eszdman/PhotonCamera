package com.particlesdevs.photoncamera.ui.camera.views;

/**
 * Decides whether the zoom state machine should refuse automatic lens changes.
 *
 * <p>Video logical members are already exposed by the open logical device and
 * do not require the physical-camera reopen that the lock protects. Applying
 * the photo-mode lock there would clamp every member selection into the active
 * member's window and leave the hidden lock pill with no way to release it.
 */
public final class LensSwitchLockPolicy {
    private LensSwitchLockPolicy() {
    }

    /**
     * @param autoZoomSwitchOn whether automatic lens switching is enabled
     * @param zoomLockOn       whether the user lock preference is enabled
     * @param videoLogicalActive whether video logical members are active
     * @return true only when lens changes should remain locked
     */
    public static boolean isLocked(boolean autoZoomSwitchOn,
                                   boolean zoomLockOn,
                                   boolean videoLogicalActive) {
        return !videoLogicalActive && (!autoZoomSwitchOn || zoomLockOn);
    }
}
