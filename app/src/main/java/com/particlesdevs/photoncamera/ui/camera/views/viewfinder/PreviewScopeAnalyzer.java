package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

public final class PreviewScopeAnalyzer {
    public static final int HISTOGRAM_BINS = 256;
    private static final float GLOW_INTENSITY = 0.4f;
    private static final int GLOW_RADIUS = 2;

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

    /**
     * Accumulates the per-column RGB waveform: {@code counts[channel][bin * columns + column]}
     * holds how many pixels of that column fell into that value bin. Returns the
     * largest single-bin count (min 1) for intensity scaling.
     */
    public static int fillWaveform(byte[] rgba, int width, int height,
                                   int[][] counts, int columns, int bins) {
        for (int channel = 0; channel < 3; channel++) {
            java.util.Arrays.fill(counts[channel], 0);
        }
        if (rgba == null || width <= 0 || height <= 0 || columns <= 0 || bins <= 0) {
            return 1;
        }
        int pixels = Math.min(width * height, rgba.length / 4);
        int max = 1;
        for (int i = 0; i < pixels; i++) {
            int base = i * 4;
            int column = (i % width) * columns / width;
            for (int channel = 0; channel < 3; channel++) {
                int value = rgba[base + channel] & 0xFF;
                int index = (value * bins / 256) * columns + column;
                int count = ++counts[channel][index];
                if (count > max) {
                    max = count;
                }
            }
        }
        return max;
    }

    /**
     * Picks the density that maps to full brightness: the given percentile of
     * the nonzero bins, so a few flat-area bins cannot crush the rest of the
     * trace. Never returns below 1, and guards degenerate scenes where a single
     * extreme bin would otherwise clip everything to white.
     */
    public static float percentileNormalizer(int[][] counts, int maxCount, float percentile) {
        if (counts == null || counts.length < 3 || maxCount <= 0) {
            return 1f;
        }
        float p = Math.max(0f, Math.min(1f, percentile));
        int[] frequency = new int[maxCount + 1];
        int nonzero = 0;
        for (int channel = 0; channel < 3; channel++) {
            for (int count : counts[channel]) {
                if (count > 0) {
                    frequency[Math.min(count, maxCount)]++;
                    nonzero++;
                }
            }
        }
        if (nonzero == 0) {
            return 1f;
        }
        int target = Math.max(1, (int) Math.ceil(nonzero * p));
        int cumulative = 0;
        int normalizer = maxCount;
        for (int count = 1; count <= maxCount; count++) {
            cumulative += frequency[count];
            if (cumulative >= target) {
                normalizer = count;
                break;
            }
        }
        return Math.max(1f, Math.max(normalizer, maxCount / 32f));
    }

    /**
     * Paints the waveform at its native display resolution: square-root
     * intensity against {@code normalizer}, additive RGB (overlaps read
     * yellow/cyan/white), high values at the top row. Zero bins stay fully
     * transparent; each lit bin gets a 5x5 dim halo so thin traces stay
     * legible. Intensity values are never floored.
     */
    public static void renderWaveformBitmap(int[][] counts, float normalizer,
                                            int[] outPixels, int width, int height) {
        java.util.Arrays.fill(outPixels, 0);
        if (counts == null || counts.length < 3 || normalizer <= 0f
                || width <= 0 || height <= 0 || outPixels.length < width * height) {
            return;
        }
        for (int column = 0; column < width; column++) {
            for (int bin = 0; bin < height; bin++) {
                int index = bin * width + column;
                int r = intensity(counts[0][index], normalizer);
                int g = intensity(counts[1][index], normalizer);
                int b = intensity(counts[2][index], normalizer);
                if (r == 0 && g == 0 && b == 0) {
                    continue;
                }
                int x = column;
                int y = height - 1 - bin;
                mergePixel(outPixels, width, height, x, y, r, g, b);
                int glowR = Math.round(r * GLOW_INTENSITY);
                int glowG = Math.round(g * GLOW_INTENSITY);
                int glowB = Math.round(b * GLOW_INTENSITY);
                if (glowR == 0 && glowG == 0 && glowB == 0) {
                    continue;
                }
                for (int dy = -GLOW_RADIUS; dy <= GLOW_RADIUS; dy++) {
                    for (int dx = -GLOW_RADIUS; dx <= GLOW_RADIUS; dx++) {
                        if (dx == 0 && dy == 0) {
                            continue;
                        }
                        mergePixel(outPixels, width, height, x + dx, y + dy,
                                glowR, glowG, glowB);
                    }
                }
            }
        }
    }

    private static int intensity(int count, float normalizer) {
        if (count <= 0) {
            return 0;
        }
        float ratio = Math.min(1f, count / normalizer);
        return Math.round(255f * (float) Math.sqrt(ratio));
    }

    private static void mergePixel(int[] pixels, int width, int height,
                                   int x, int y, int r, int g, int b) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int index = y * width + x;
        int existing = pixels[index];
        int mergedR = Math.max((existing >> 16) & 0xFF, r);
        int mergedG = Math.max((existing >> 8) & 0xFF, g);
        int mergedB = Math.max(existing & 0xFF, b);
        pixels[index] = 0xFF000000 | (mergedR << 16) | (mergedG << 8) | mergedB;
    }
}
