package com.particlesdevs.photoncamera.ui.camera.data;

import java.util.Locale;

public final class LensLabelFormatter {

    private LensLabelFormatter() {
    }

    public static String format(CameraLensData lens, boolean mmEquivalent) {
        if (lens == null) {
            return "";
        }
        if (mmEquivalent) {
            float equivalent35mm = lens.getCamera35mmFocalLength();
            if (isUsable(equivalent35mm)) {
                return focalLabel(equivalent35mm);
            }
        }
        return zoomLabel(lens.getZoomFactor());
    }

    public static String focalLabel(float equivalent35mm) {
        return Math.round(equivalent35mm) + "mm";
    }

    public static String zoomLabel(float zoomFactor) {
        if (!isUsable(zoomFactor)) {
            return "";
        }
        String label = String.format(Locale.US, "%.1fx", zoomFactor);
        if (label.endsWith(".0x")) {
            return label.substring(0, label.length() - 3) + "x";
        }
        return label;
    }

    private static boolean isUsable(float value) {
        return value > 0f && !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
