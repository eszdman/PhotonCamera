package com.particlesdevs.photoncamera.processing.parameters;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Small RAW-domain thumbnail and translation-compensated quality measurements. */
public final class NightRawMetrics {
    public static final int WIDTH = 48, HEIGHT = 32;
    public final double sharpness, clippedFraction;
    private final float[] luminance;
    private final double variance;
    private NightRawMetrics(float[] values, double sharp, double clipped, double noise) {
        luminance = values; sharpness = sharp; clippedFraction = clipped; variance = noise;
    }

    public static NightRawMetrics measure(ByteBuffer raw, int width, int height,
                                         float white, float[] black, double shot, double readout) {
        if (raw == null || width < 8 || height < 8 || black == null || black.length != 4
                || (long) width * height * 2 > raw.remaining() || !Float.isFinite(white)) return null;
        for (float level : black) if (!Float.isFinite(level) || white <= level) return null;
        if (!Double.isFinite(shot) || shot < 0 || !Double.isFinite(readout) || readout < 0) return null;
        ByteBuffer buffer = raw.duplicate().order(ByteOrder.nativeOrder());
        int origin = buffer.position(), clipped = 0;
        float[] values = new float[WIDTH * HEIGHT];
        double noise = 0;
        // Average an 8x8 RAW patch at each grid location. Keep CFA parity intact.
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int sx = Math.min(width - 8, (int) ((x + .5) * width / WIDTH)) & ~1;
            int sy = Math.min(height - 8, (int) ((y + .5) * height / HEIGHT)) & ~1;
            double sum = 0;
            for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++) {
                int value = buffer.getShort(origin + ((sy + dy) * width + sx + dx) * 2) & 65535;
                float level = black[(dy & 1) * 2 + (dx & 1)];
                double normalized = Math.max(0, Math.min(1, (value - level) / (white - level)));
                if (normalized >= .98) clipped++;
                sum += normalized;
            }
            values[y * WIDTH + x] = (float) (sum / 64);
            noise += (shot * sum / 64 + readout) / 64;
        }
        noise /= values.length;
        double detail = 0;
        int count = 0;
        for (int y = 1; y < HEIGHT; y++) for (int x = 1; x < WIDTH; x++) {
            double dx = values[y * WIDTH + x] - values[y * WIDTH + x - 1];
            double dy = values[y * WIDTH + x] - values[(y - 1) * WIDTH + x];
            detail += dx * dx + dy * dy; count += 2;
        }
        return new NightRawMetrics(values, Math.sqrt(Math.max(0, detail / count - 2 * noise)),
                clipped / (double) (values.length * 64), noise);
    }

    /** (subject-change fraction, alignment suitability), or unknown subject change. */
    public double[] compare(NightRawMetrics other) {
        double best = Double.POSITIVE_INFINITY, second = best;
        int bx = 0, by = 0;
        for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
            double cost = 0;
            for (int y = 4; y < HEIGHT - 4; y += 2) for (int x = 4; x < WIDTH - 4; x += 2)
                cost += Math.min(.05, Math.abs(luminance[y * WIDTH + x] - other.luminance[(y + dy) * WIDTH + x + dx]));
            if (cost < best) { second = best; best = cost; bx = dx; by = dy; }
            else if (cost < second) second = cost;
        }
        double sigma = Math.sqrt(Math.max(variance + other.variance, 1e-10));
        if (sharpness < sigma * 2 || other.sharpness < sigma * 2) return new double[]{Double.NaN, Double.NaN};
        if (Math.abs(bx) == 3 || Math.abs(by) == 3 || second - best < Math.max(.01, best * .03))
            return new double[]{Double.NaN, .2};
        int changed = 0, total = 0;
        for (int y = 4; y < HEIGHT - 4; y++) for (int x = 4; x < WIDTH - 4; x++) {
            if (Math.abs(luminance[y * WIDTH + x] - other.luminance[(y + by) * WIDTH + x + bx]) > Math.max(.02, 4 * sigma)) changed++;
            total++;
        }
        double fraction = changed / (double) total;
        double overlap = (1.0 - Math.abs(bx) / (double) WIDTH) * (1.0 - Math.abs(by) / (double) HEIGHT);
        return new double[]{fraction, overlap * (1 - fraction)};
    }
}
