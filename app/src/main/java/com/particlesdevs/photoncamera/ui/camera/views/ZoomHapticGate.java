package com.particlesdevs.photoncamera.ui.camera.views;

import java.util.Locale;

/**
 * Gates zoom haptics on the displayed zoom-indicator step. Fallback haptics
 * (predefined/waveform) fire once per rendered label change ("2.1x" -> "2.2x")
 * instead of on every slider/pinch movement.
 */
public final class ZoomHapticGate {
    private String lastLabel;

    public boolean accept(float zoomRatio) {
        String label = format(zoomRatio);
        if (label.equals(lastLabel)) {
            return false;
        }
        lastLabel = label;
        return true;
    }

    public void prime(float zoomRatio) {
        lastLabel = format(zoomRatio);
    }

    public void reset() {
        lastLabel = null;
    }

    private static String format(float zoomRatio) {
        return String.format(Locale.US, "%.1fx", zoomRatio);
    }
}
