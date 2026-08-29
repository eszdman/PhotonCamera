package com.particlesdevs.photoncamera.capture;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Single source of truth for the zoom state, combining optical physical-lens
 * switching with digital crop.
 *
 * <p>The key separation is between <em>effective zoom</em> (what the user sees
 * / the pinch gesture drives) and <em>digital zoom</em> (the crop applied to the
 * active lens sensor):
 *
 * <pre>effectiveZoom = lensNativeZoom * digitalZoom</pre>
 *
 * where {@code lensNativeZoom} is the lens's optical {@code zoomFactor} (which
 * can be &lt; 1.0 for an ultra-wide lens) and {@code digitalZoom} is always
 * &ge; 1.0 because a lens can only crop in, never out. Sub-1x field of view is
 * achieved by selecting a wider physical lens, not by downscaling the sensor.
 *
 * <p>The state machine maps an effective zoom target to an owning lens and a
 * digital crop. When the target crosses a lens threshold the {@link LensSwitchListener}
 * is fired so the caller can reopen the camera on the new lens; after the
 * camera is reopened the same target is re-expressed on the new lens.
 * A small "detent" window around every lens's native zoom snaps the target so
 * the lens lands exactly at native (digital = 1.0, no crop), making switches
 * sticky and preventing overshoot.
 */
public class ZoomController {
    private static final String TAG = "ZoomController";

    public static final float MIN_ZOOM = 1.0f;

    /** Default relative detent window (3%) around each lens's native zoom. */
    private static final float DEFAULT_SNAP_WINDOW = 0.03f;

    /** Describes one physical lens available for the active facing. */
    public static class LensEntry {
        public final String cameraId;
        public final float nativeZoom;
        public final float maxDigitalZoom;

        public LensEntry(String cameraId, float nativeZoom, float maxDigitalZoom) {
            this.cameraId = cameraId;
            this.nativeZoom = nativeZoom;
            this.maxDigitalZoom = maxDigitalZoom;
        }
    }

    /** Notified when the target effective zoom requires a physical lens switch. */
    public interface LensSwitchListener {
        void onLensSwitch(String cameraId);
    }

    private final List<LensEntry> lenses = new ArrayList<>();
    private final List<LensEntry> lensesAsc = new ArrayList<>();

    private LensSwitchListener lensSwitchListener;
    private float snapWindow = DEFAULT_SNAP_WINDOW;

    /** Effective zoom seen by the user / gesture; the state-machine input. */
    private float targetZoom = MIN_ZOOM;
    /** Index into {@link #lensesAsc} of the currently active lens. */
    private int activeLensIndex = -1;
    /** Digital crop (>= 1.0) applied to the active lens. */
    private float digitalZoom = MIN_ZOOM;

    /** Normalized pinch focus point in [0,1] within the active array frame. */
    private float focusX = 0.5f;
    private float focusY = 0.5f;

    // Comparator: ascending native zoom (ultra-wide first, tele last).
    private static final Comparator<LensEntry> ASC_BY_NATIVE =
            (a, b) -> Float.compare(a.nativeZoom, b.nativeZoom);

    public ZoomController() {
    }

    public void setLensSwitchListener(LensSwitchListener listener) {
        this.lensSwitchListener = listener;
    }

    public void setSnapWindow(float snapWindow) {
        this.snapWindow = Math.max(0f, snapWindow);
    }

    /** Replaces the lens set for the active facing (already sorted after here). */
    public void setLenses(List<LensEntry> lensList) {
        lenses.clear();
        lensesAsc.clear();
        if (lensList != null) {
            lenses.addAll(lensList);
            lensesAsc.addAll(lensList);
            lensesAsc.sort(ASC_BY_NATIVE);
        }
        // Re-anchor the active lens and clamp the target to the new range.
        targetZoom = clamp(targetZoom, getMinZoom(), getMaxZoom());
        if (activeLensIndex >= lensesAsc.size()) activeLensIndex = lensesAsc.size() - 1;
    }

    /**
     * Marks the lens that the camera is currently open on. Used to re-anchor
     * the active lens index after a restart and to re-express the target zoom.
     */
    public void setActiveLens(String cameraId) {
        activeLensIndex = -1;
        for (int i = 0; i < lensesAsc.size(); i++) {
            if (lensesAsc.get(i).cameraId.equals(cameraId)) {
                activeLensIndex = i;
                break;
            }
        }
        if (activeLensIndex < 0 && !lensesAsc.isEmpty()) {
            // Fall back to the lens nearest the current target.
            activeLensIndex = selectLensIndex(targetZoom);
        }
        recomputeDigitalZoom();
    }

    /**
     * Sets the effective zoom the user is asking for. Applies a detent snap,
     * selects the owning lens, and either (a) fires {@link LensSwitchListener}
     * when the lens must change, or (b) recomputes the digital crop if the lens
     * is already active.
     *
     * @param effectiveZoom effective zoom (may be &lt; 1.0 for an ultra-wide lens)
     * @param focusX        normalized pinch focus X in [0,1]
     * @param focusY        normalized pinch focus Y in [0,1]
     * @return the camera id of the lens to switch to, or {@code null} if no switch is needed
     */
    public String setTargetZoom(float effectiveZoom, float focusX, float focusY) {
        this.focusX = clamp(focusX, 0.0f, 1.0f);
        this.focusY = clamp(focusY, 0.0f, 1.0f);
        float clamped = clamp(effectiveZoom, getMinZoom(), getMaxZoom());
        targetZoom = snapToDetent(clamped);

        int newIndex = selectLensIndex(targetZoom);
        if (newIndex != activeLensIndex) {
            activeLensIndex = newIndex;
            digitalZoom = MIN_ZOOM; // the new lens starts at native (no crop)
            if (lensSwitchListener != null && !lensesAsc.isEmpty()) {
                lensSwitchListener.onLensSwitch(lensesAsc.get(newIndex).cameraId);
            }
            return !lensesAsc.isEmpty() ? lensesAsc.get(newIndex).cameraId : null;
        }
        recomputeDigitalZoom();
        return null;
    }

    // --- Zoom queries used by the crop / preview / indicator ---

    /** Effective zoom (what the indicator and gesture operate on). */
    public float getZoomRatio() {
        return targetZoom;
    }

    /** Digital crop (&ge; 1.0) actually applied to the active lens. */
    public float getDigitalZoom() {
        return digitalZoom;
    }

    /** The camera id of the currently active lens, or {@code null}. */
    public String getActiveLensId() {
        if (activeLensIndex < 0 || activeLensIndex >= lensesAsc.size()) return null;
        return lensesAsc.get(activeLensIndex).cameraId;
    }

    /** Minimum achievable effective zoom (native zoom of the widest lens). */
    public float getMinZoom() {
        if (lensesAsc.isEmpty()) return MIN_ZOOM;
        return lensesAsc.get(0).nativeZoom;
    }

    /** Maximum achievable effective zoom (native * maxDigital of the strongest lens). */
    public float getMaxZoom() {
        if (lensesAsc.isEmpty()) return MIN_ZOOM;
        LensEntry last = lensesAsc.get(lensesAsc.size() - 1);
        return last.nativeZoom * Math.max(1f, last.maxDigitalZoom);
    }

    public boolean isZoomed() {
        return digitalZoom > MIN_ZOOM + 0.0001f;
    }

    public float getFocusX() {
        return focusX;
    }

    public float getFocusY() {
        return focusY;
    }

    public void reset() {
        targetZoom = MIN_ZOOM;
        digitalZoom = MIN_ZOOM;
        focusX = 0.5f;
        focusY = 0.5f;
    }

    /** Resets the target to the active lens's native zoom (used on camera reopen). */
    public void resetToActiveLensNative() {
        if (activeLensIndex < 0 || activeLensIndex >= lensesAsc.size()) {
            reset();
            return;
        }
        targetZoom = lensesAsc.get(activeLensIndex).nativeZoom;
        digitalZoom = MIN_ZOOM;
        focusX = 0.5f;
        focusY = 0.5f;
    }

    // --- Internal helpers ---

    private int selectLensIndex(float effective) {
        if (lensesAsc.isEmpty()) return -1;
        // Pick the lens with the largest nativeZoom <= effective; if none (should
        // not happen since effective >= min), pick the widest lens.
        int index = -1;
        for (int i = 0; i < lensesAsc.size(); i++) {
            if (effective >= lensesAsc.get(i).nativeZoom - 0.0001f) {
                index = i;
            } else {
                break;
            }
        }
        return index < 0 ? 0 : index;
    }

    private void recomputeDigitalZoom() {
        if (activeLensIndex < 0 || activeLensIndex >= lensesAsc.size()) {
            digitalZoom = MIN_ZOOM;
            return;
        }
        LensEntry lens = lensesAsc.get(activeLensIndex);
        float dz = targetZoom / lens.nativeZoom;
        // A lens cannot crop out; clamp to the sensor's max digital zoom.
        digitalZoom = clamp(dz, MIN_ZOOM, Math.max(1f, lens.maxDigitalZoom));
    }

    private float snapToDetent(float effective) {
        if (lensesAsc.isEmpty()) return effective;
        // Snap to the nearest lens native zoom within the relative detent window.
        float best = effective;
        float bestDist = Float.MAX_VALUE;
        for (LensEntry lens : lensesAsc) {
            float dist = Math.abs(effective - lens.nativeZoom);
            if (dist <= snapWindow * lens.nativeZoom && dist < bestDist) {
                bestDist = dist;
                best = lens.nativeZoom;
            }
        }
        return best;
    }

    /**
     * Pure crop math, independent of the platform. Returns
     * {@code {left, top, width, height}} for a crop of a frame with the given
     * nominal width/height at the given digital zoom ratio and focal point.
     */
    public static int[] computeCropValues(int width, int height, float zoomRatio, float focusX, float focusY) {
        if (zoomRatio < MIN_ZOOM + 0.0001f) {
            return new int[]{0, 0, width, height};
        }
        int cropW = evenDown((int) (width / zoomRatio));
        int cropH = evenDown((int) (height / zoomRatio));
        if (cropW < 2) cropW = 2;
        if (cropH < 2) cropH = 2;

        int centerX = (int) (focusX * width);
        int centerY = (int) (focusY * height);
        int left = clamp(centerX - cropW / 2, 0, width - cropW);
        int top = clamp(centerY - cropH / 2, 0, height - cropH);
        // SCALER_CROP_REGION requires even (2-byte) aligned coordinates.
        left = evenDown(left);
        top = evenDown(top);
        return new int[]{left, top, cropW, cropH};
    }

    /**
     * Computes the crop rectangle in a coordinate space of the given nominal
     * width/height from the current digital zoom (used for both the preview's
     * {@code SCALER_CROP_REGION} in active-array coords and the raw buffer crop
     * in pixel coords).
     */
    public Rect computeCrop(int width, int height) {
        int[] v = computeCropValues(width, height, digitalZoom, focusX, focusY);
        return new Rect(v[0], v[1], v[0] + v[2], v[1] + v[3]);
    }

    /**
     * Crops the RAW buffer to the same region shown in the preview, and records
     * the crop's origin in the full-frame so downstream processing can correct
     * sensor-relative metadata. The returned {@link CropRegion} carries the
     * cropped dimensions (same as {@link #computeCrop(int, int)}) plus the
     * offset of the crop within the full frame.
     */
    public CropRegion computeCropRegion(int fullWidth, int fullHeight) {
        Rect crop = computeCrop(fullWidth, fullHeight);
        return new CropRegion(crop.left, crop.top, crop.width(), crop.height(),
                fullWidth, fullHeight);
    }

    /** Metadata describing how a RAW buffer was cropped from the full frame. */
    public static class CropRegion {
        public final int originX;
        public final int originY;
        public final int width;
        public final int height;
        /** Full-frame (uncropped) dimensions, for sensor-relative normalization. */
        public final int fullWidth;
        public final int fullHeight;

        public CropRegion(int originX, int originY, int width, int height,
                          int fullWidth, int fullHeight) {
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
            this.fullWidth = fullWidth;
            this.fullHeight = fullHeight;
        }
    }

    /** Crop rect directly in sensor/active-array coordinates. */
    public Rect computeSensorCrop(Rect activeArray) {
        Rect crop = computeCrop(activeArray.width(), activeArray.height());
        crop.offset(activeArray.left, activeArray.top);
        return crop;
    }

    private static int evenDown(int v) {
        return v & ~1;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
