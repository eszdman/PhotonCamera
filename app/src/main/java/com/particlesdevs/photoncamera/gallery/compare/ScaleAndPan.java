package com.particlesdevs.photoncamera.gallery.compare;

import android.graphics.PointF;

/**
 * Stores the shared zoom and pan state of the comparison panes in a
 * resolution-independent form so it can be exchanged between images of
 * different dimensions:
 * <ul>
 * <li>{@code zoomRatio} is the scale relative to the pane's fit scale
 * ({@code getScale() / getMinScale()}) — 1 means the whole image fits the view.</li>
 * <li>{@code center} is the view center in normalized image coordinates
 * ({@code center.x / sWidth}, {@code center.y / sHeight}), i.e. (0.5, 0.5) is
 * the image middle.</li>
 * </ul>
 * Two panes showing the same scene at different resolutions display the same
 * content at the same on-screen size when these values are equal.
 * <p>
 * Created by Vibhor Srivastava 18 Jan 2021
 */
public class ScaleAndPan {
    private float zoomRatio = 1f;
    private final PointF center = new PointF(0.5f, 0.5f);

    public ScaleAndPan() {
    }

    public void set(float zoomRatio, PointF center) {
        this.zoomRatio = zoomRatio;
        this.center.set(center);
    }

    public float getZoomRatio() {
        return zoomRatio;
    }

    public PointF getCenter() {
        return center;
    }
}
