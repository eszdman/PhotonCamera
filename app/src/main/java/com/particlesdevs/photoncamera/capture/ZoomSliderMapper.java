package com.particlesdevs.photoncamera.capture;

/**
 * Maps a linear {@code SeekBar} progress to an effective zoom ratio (and back)
 * with a logarithmic feel, so dragging from ultra-wide to tele feels even
 * across the whole range (the same convention as the pinch gesture's
 * multiplicative scale factor).
 *
 * <p>Both functions are pure and clamp their inputs, so they are safe to call
 * from the UI thread on every progress change.
 */
public final class ZoomSliderMapper {
    private ZoomSliderMapper() {
    }

    /**
     * @param progress current slider progress in {@code [0, max]}
     * @param max      slider max (must be &gt; 0)
     * @param minZoom  minimum effective zoom of the active lens set
     * @param maxZoom  maximum effective zoom of the active lens set
     * @return the effective zoom for the given progress
     */
    public static float progressToZoom(int progress, int max, float minZoom, float maxZoom) {
        if (max <= 0 || !(maxZoom > minZoom) || minZoom <= 0f || maxZoom <= 0f) {
            return minZoom;
        }
        float t = Math.max(0f, Math.min(1f, (float) progress / max));
        return (float) (minZoom * Math.pow(maxZoom / minZoom, t));
    }

    /**
     * @param zoom    current effective zoom
     * @param max     slider max (must be &gt; 0)
     * @param minZoom minimum effective zoom of the active lens set
     * @param maxZoom maximum effective zoom of the active lens set
     * @return the slider progress in {@code [0, max]} for the given zoom
     */
    public static int zoomToProgress(float zoom, int max, float minZoom, float maxZoom) {
        if (max <= 0 || !(maxZoom > minZoom) || minZoom <= 0f || maxZoom <= 0f) {
            return 0;
        }
        float z = Math.max(minZoom, Math.min(maxZoom, zoom));
        double t = Math.log(z / minZoom) / Math.log(maxZoom / minZoom);
        return Math.round((float) (t * max));
    }
}
