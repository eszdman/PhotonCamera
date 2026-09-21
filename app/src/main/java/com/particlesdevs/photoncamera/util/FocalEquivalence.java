package com.particlesdevs.photoncamera.util;

public final class FocalEquivalence {
    public static final float FILM_35MM_DIAGONAL_MM = 43.2666f;

    private FocalEquivalence() {
    }

    public static float cropFactor(float physicalWidthMm, float physicalHeightMm,
                                   int activeWidthPx, int activeHeightPx,
                                   int pixelWidthPx, int pixelHeightPx) {
        if (physicalWidthMm <= 0f || physicalHeightMm <= 0f) {
            return 0f;
        }
        float scaleX = 1f;
        float scaleY = 1f;
        if (pixelWidthPx > 0 && activeWidthPx > 0) {
            scaleX = (float) activeWidthPx / pixelWidthPx;
        }
        if (pixelHeightPx > 0 && activeHeightPx > 0) {
            scaleY = (float) activeHeightPx / pixelHeightPx;
        }
        double width = physicalWidthMm * scaleX;
        double height = physicalHeightMm * scaleY;
        double diagonal = Math.hypot(width, height);
        if (diagonal <= 0d || !Double.isFinite(diagonal)) {
            return 0f;
        }
        return (float) (FILM_35MM_DIAGONAL_MM / diagonal);
    }

    public static float equivalent35mm(float focalMm, float physicalWidthMm, float physicalHeightMm,
                                       int activeWidthPx, int activeHeightPx,
                                       int pixelWidthPx, int pixelHeightPx) {
        if (focalMm <= 0f) {
            return 0f;
        }
        float crop = cropFactor(physicalWidthMm, physicalHeightMm,
                activeWidthPx, activeHeightPx, pixelWidthPx, pixelHeightPx);
        if (crop <= 0f) {
            return 0f;
        }
        return focalMm * crop;
    }

    public static float iszEquivalent35mm(float baseEquivalent35mm, float iszZoomRatio) {
        if (baseEquivalent35mm <= 0f || iszZoomRatio <= 0f) {
            return 0f;
        }
        return baseEquivalent35mm * iszZoomRatio;
    }
}
