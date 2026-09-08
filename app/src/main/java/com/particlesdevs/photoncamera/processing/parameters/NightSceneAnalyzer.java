package com.particlesdevs.photoncamera.processing.parameters;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Bounded, display-referred preview metering. No RAW clipping or noise claims. */
public final class NightSceneAnalyzer {
    public static final int WIDTH = 64, HEIGHT = 48;
    public static final long INTERVAL_NS = 125_000_000L;
    private static final long MAX_AGE_NS = 750_000_000L;
    private static final int BORDER = 5, SEARCH = 4;

    public static final class Snapshot {
        public final long timestampNs;
        public final int frameCount;
        public final boolean motionReliable;
        /** Fractions of the preview, not sensor saturation probabilities. */
        public final double clippedFraction, shadowFraction, shadowP10, highlightP99;
        /** Normalized display contrast, not scene dynamic range in stops. */
        public final double dynamicRangeScore;
        /** Motion in analysis pixels/second; residual fraction is a subject-motion proxy. */
        public final double cameraMotion, subjectMotion;

        private Snapshot(long time, int count, boolean reliable, double clipped,
                         double shadows, double p10, double p99, double camera, double subject) {
            timestampNs = time;
            frameCount = count;
            motionReliable = reliable;
            clippedFraction = clipped;
            shadowFraction = shadows;
            shadowP10 = p10;
            highlightP99 = p99;
            dynamicRangeScore = p99 - p10;
            cameraMotion = reliable ? camera : Double.NaN;
            subjectMotion = reliable ? subject : Double.NaN;
        }
    }

    private int[] previous;
    private long previousTime;
    private final double[] cameraHistory = new double[3], subjectHistory = new double[3];
    private int pairs;
    private Snapshot latest;

    public synchronized void reset() {
        previous = null;
        previousTime = 0;
        pairs = 0;
        latest = null;
    }

    /** Both timestamps use System.nanoTime(), independent of camera timestamp source. */
    public synchronized Snapshot snapshot(long nowNs) {
        return latest != null && nowNs >= latest.timestampNs
                && nowNs - latest.timestampNs <= MAX_AGE_NS ? latest : null;
    }

    public synchronized void accept(ByteBuffer rgba, long nowNs) {
        if (rgba.remaining() < WIDTH * HEIGHT * 4) throw new IllegalArgumentException("Short preview buffer");
        if (previous != null && nowNs <= previousTime) return;
        if (previous != null && nowNs - previousTime < INTERVAL_NS) return;
        if (previous != null && nowNs - previousTime > MAX_AGE_NS) reset();
        int[] current = new int[WIDTH * HEIGHT];
        int[] histogram = new int[256];
        int clipped = 0, shadows = 0, start = rgba.position();
        for (int i = 0; i < current.length; i++) {
            int r = rgba.get(start + 4 * i) & 255;
            int g = rgba.get(start + 4 * i + 1) & 255;
            int b = rgba.get(start + 4 * i + 2) & 255;
            int y = (77 * r + 150 * g + 29 * b + 128) >> 8;
            current[i] = y;
            histogram[y]++;
            if (Math.max(r, Math.max(g, b)) >= 250) clipped++;
            if (y <= 25) shadows++;
        }
        boolean reliable = false;
        if (previous != null) {
            // Median centering tolerates modest global preview AE changes.
            int[] oldHistogram = new int[256];
            for (int value : previous) oldHistogram[value]++;
            double brightnessShift = percentile(histogram, .5) - percentile(oldHistogram, .5);
            double best = Double.POSITIVE_INFINITY, second = best;
            int bestX = 0, bestY = 0;
            for (int dy = -SEARCH; dy <= SEARCH; dy++) {
                for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                    double cost = 0;
                    for (int y = BORDER; y < HEIGHT - BORDER; y += 2) {
                        for (int x = BORDER; x < WIDTH - BORDER; x += 2) {
                            // Trim influence of independently moving foreground objects.
                            cost += Math.min(25, Math.abs(current[y * WIDTH + x]
                                    - previous[(y + dy) * WIDTH + x + dx] - brightnessShift));
                        }
                    }
                    if (cost < best) { second = best; best = cost; bestX = dx; bestY = dy; }
                    else if (cost < second) second = cost;
                }
            }
            int changed = 0, samples = 0;
            double texture = 0;
            for (int y = BORDER; y < HEIGHT - BORDER; y++) {
                for (int x = BORDER; x < WIDTH - BORDER; x++) {
                    int value = current[y * WIDTH + x];
                    double residual = Math.abs(value - previous[(y + bestY) * WIDTH + x + bestX] - brightnessShift);
                    if (residual > 20) changed++;
                    texture += Math.abs(value - current[y * WIDTH + x - 1]);
                    samples++;
                }
            }
            double fraction = changed / (double) samples;
            reliable = Math.abs(brightnessShift) <= 20 && texture / samples >= 3
                    && Math.abs(bestX) < SEARCH && Math.abs(bestY) < SEARCH
                    && second - best > Math.max(20, best * .05) && fraction < .6;
            if (reliable) {
                cameraHistory[pairs % 3] = Math.hypot(bestX, bestY) * 1e9 / (nowNs - previousTime);
                subjectHistory[pairs % 3] = fraction;
                pairs++;
            } else pairs = 0;
        }
        latest = new Snapshot(nowNs, Math.min(4, pairs + 1), reliable,
                clipped / (double) current.length, shadows / (double) current.length,
                percentile(histogram, .1) / 255.0, percentile(histogram, .99) / 255.0,
                median(cameraHistory, Math.min(3, pairs)), median(subjectHistory, Math.min(3, pairs)));
        previous = current;
        previousTime = nowNs;
    }

    private static int percentile(int[] histogram, double fraction) {
        int target = (int) Math.ceil(WIDTH * HEIGHT * fraction), total = 0;
        for (int i = 0; i < histogram.length; i++) { total += histogram[i]; if (total >= target) return i; }
        return 255;
    }

    private static double median(double[] values, int count) {
        if (count == 0) return Double.NaN;
        double[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        return sorted[count / 2];
    }
}
