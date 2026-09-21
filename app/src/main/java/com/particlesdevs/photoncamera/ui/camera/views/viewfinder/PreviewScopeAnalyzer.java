package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

public final class PreviewScopeAnalyzer {
    public static final int HISTOGRAM_BINS = 256;

    private PreviewScopeAnalyzer() {
    }

    public static void fillHistogram(byte[] rgba, int width, int height,
                                     int[][] bins, int binCount) {
        for (int channel = 0; channel < 3; channel++) {
            java.util.Arrays.fill(bins[channel], 0);
        }
        if (rgba == null || width <= 0 || height <= 0 || binCount <= 0) {
            return;
        }
        int pixels = Math.min(width * height, rgba.length / 4);
        for (int i = 0; i < pixels; i++) {
            int base = i * 4;
            bins[0][(rgba[base] & 0xFF) * binCount / 256]++;
            bins[1][(rgba[base + 1] & 0xFF) * binCount / 256]++;
            bins[2][(rgba[base + 2] & 0xFF) * binCount / 256]++;
        }
    }

    public static int applySqrtScale(int[][] bins) {
        int max = 1;
        for (int channel = 0; channel < 3; channel++) {
            for (int i = 0; i < bins[channel].length; i++) {
                int scaled = (int) Math.sqrt(bins[channel][i]);
                bins[channel][i] = scaled;
                if (scaled > max) {
                    max = scaled;
                }
            }
        }
        return max;
    }
}
