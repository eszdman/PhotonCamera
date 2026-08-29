package com.particlesdevs.photoncamera.api;

/**
 * Pure, framework-free helpers for In-Sensor Zoom (ISZ) virtual lenses. Kept out
 * of {@link CameraManager2} (whose static initializers load Android camera types)
 * so the id-composition/routing logic can be unit tested on the JVM.
 */
public final class IszLensUtil {

    /** Marker appended to the last segment of an ISZ virtual lens id. */
    public static final String ISZ_VIRTUAL_SUFFIX = "-v";

    private IszLensUtil() {}

    /** True when a camera id identifies an In-Sensor Zoom (ISZ) virtual lens. */
    public static boolean isIszVirtual(String cameraId) {
        return cameraId != null && cameraId.endsWith(ISZ_VIRTUAL_SUFFIX);
    }

    /**
     * Builds a 3-segment ISZ id that routes to the same logical/physical sensor as
     * the base id while remaining distinct from it. Works for both plain ("3") and
     * composite ("0-3") base ids; every composite-id consumer only reads segments
     * [0] and [1], so the trailing virtual marker is ignored for routing.
     */
    public static String composeIszVirtualId(String baseCameraId) {
        String logical = baseCameraId;
        String physical = baseCameraId;
        int idx = baseCameraId == null ? -1 : baseCameraId.lastIndexOf('-');
        if (idx >= 0) {
            logical = baseCameraId.substring(0, idx);
            physical = baseCameraId.substring(idx + 1);
        }
        return logical + "-" + physical + ISZ_VIRTUAL_SUFFIX;
    }

    /**
     * Physical id from a (possibly composite) camera id. Matches the routing used
     * throughout the codebase: segments are {@code -}-delimited and the physical id
     * is segment [1]. For a plain id ("3") the id itself is the physical id. ISZ
     * virtual ids ("logical-physical-v") have the physical id in segment [1], so
     * this also handles them.
     */
    public static int physicalIdFrom(String cameraId) {
        if (cameraId == null) return -1;
        String physical = cameraId;
        int idx = cameraId.indexOf('-');
        if (idx >= 0) {
            String[] segs = cameraId.split("-");
            if (segs.length >= 2) {
                physical = segs[1];
            }
        }
        try {
            return Integer.parseInt(physical);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
