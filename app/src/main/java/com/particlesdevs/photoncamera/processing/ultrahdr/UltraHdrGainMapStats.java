package com.particlesdevs.photoncamera.processing.ultrahdr;

/**
 * Pure statistics for the Ultra HDR gain map: computes the max and min
 * content boost from the per-pixel log2 gain values. Extracted from the GL
 * pipeline so the logic is unit-testable.
 *
 * <p>The gain map is never skipped when data is available. The max content
 * boost is derived from the 99.9th percentile of the positive highlight
 * tail, which approximates the true scene maximum while excluding extreme
 * outliers. The minimum content boost is always 1.0: shadows and neutral
 * regions are intentionally unchanged.</p>
 */
public final class UltraHdrGainMapStats {

    /** Minimum HDR/SDR brightness ratio considered HDR content. */
    public static final double MIN_RATIO = 1.05;
    public static final double LOG2_MIN_RATIO = Math.log(MIN_RATIO) / Math.log(2.0);

    /** Log2 gain values are clamped to [MIN_LOG2_GAIN, MAX_LOG2_GAIN]. */
    public static final float MIN_LOG2_GAIN = 0.0f;
    public static final float MAX_LOG2_GAIN = 4.0f;
    public static final float MAX_CONTENT_BOOST = 16.0f;
    /** Production gain maps never darken the SDR base. */
    public static final float MIN_CONTENT_BOOST = 1.0f;
    /** Shared SDR/HDR offset used by the shader and ISO gain-map metadata. */
    public static final float GAIN_OFFSET = 1.0f / 64.0f;

    /** Fraction of the brightest/darkest pixels excluded as outliers. */
    public static final double HIGHLIGHT_TAIL_EXCLUSION = 0.001;

    /** Number of histogram bins spanning [MIN_LOG2_GAIN, MAX_LOG2_GAIN]. */
    public static final int HISTOGRAM_BINS = 1024;

    private UltraHdrGainMapStats() {
    }

    /**
     * Result of the gain map statistics.
     */
    public static final class Stats {
        public final double p50;
        public final double p999;
        public final double highlightTail;
        public final double shadowTail;
        public final double maxL;
        public final double minL;
        public final int aboveThresholdCount;
        public final int belowThresholdCount;
        public final int total;
        /** 0 only when there is no gain map data at all. */
        public final float maxContentBoost;
        /** Always 1.0 because shadows are intentionally unchanged. */
        public final float minContentBoost;

        private Stats(int total, double p50, double p999, double highlightTail,
                      double shadowTail, double maxL, double minL,
                      int aboveThresholdCount, int belowThresholdCount,
                      float maxContentBoost, float minContentBoost) {
            this.total = total;
            this.p50 = p50;
            this.p999 = p999;
            this.highlightTail = highlightTail;
            this.shadowTail = shadowTail;
            this.maxL = maxL;
            this.minL = minL;
            this.aboveThresholdCount = aboveThresholdCount;
            this.belowThresholdCount = belowThresholdCount;
            this.maxContentBoost = maxContentBoost;
            this.minContentBoost = minContentBoost;
        }
    }

    /**
     * Computes gain map statistics from the full-resolution log2 gain
     * values (each in [MIN_LOG2_GAIN, MAX_LOG2_GAIN]).
     *
     * @param log2Gains per-pixel log2(HDR/SDR) gains
     * @return stats; {@link Stats#maxContentBoost} is 0 only when no data
     *         was provided
     */
    public static Stats compute(float[] log2Gains) {
        if (log2Gains == null || log2Gains.length == 0) {
            return new Stats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0.0f, 1.0f);
        }
        int total = log2Gains.length;
        int[] histogram = new int[HISTOGRAM_BINS];
        for (float l : log2Gains) {
            histogram[binForGain(l)]++;
        }
        return computeFromHistogram(histogram, total);
    }

    /** Maps a shader gain value to the matching statistics histogram bin. */
    public static int binForGain(float gain) {
        if (!Float.isFinite(gain)) {
            gain = 0.0f;
        }
        double span = MAX_LOG2_GAIN - MIN_LOG2_GAIN;
        int bin = (int) ((gain - MIN_LOG2_GAIN) * (HISTOGRAM_BINS / span));
        return Math.max(0, Math.min(HISTOGRAM_BINS - 1, bin));
    }

    /**
     * Computes gain map statistics from a pre-built histogram of
     * {@link #HISTOGRAM_BINS} bins spanning
     * [MIN_LOG2_GAIN, MAX_LOG2_GAIN]. This avoids materialising a
     * full-resolution float array in the pipeline node.
     *
     * @param histogram bin counts
     * @param total     total pixel count
     * @return stats; {@link Stats#maxContentBoost} is 0 only when no data
     *         was provided
     */
    public static Stats computeFromHistogram(int[] histogram, int total) {
        if (histogram == null || histogram.length != HISTOGRAM_BINS || total <= 0) {
            return new Stats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0.0f, 1.0f);
        }
        long histogramTotal = 0;
        for (int count : histogram) {
            if (count < 0) {
                return new Stats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0.0f, 1.0f);
            }
            histogramTotal += count;
        }
        if (histogramTotal != total) {
            return new Stats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0.0f, 1.0f);
        }
        double binWidth = (MAX_LOG2_GAIN - MIN_LOG2_GAIN) / HISTOGRAM_BINS;
        int aboveThresholdCount = 0;
        int belowThresholdCount = 0;
        for (int i = 0; i < HISTOGRAM_BINS; i++) {
            double center = MIN_LOG2_GAIN + (i + 0.5) * binWidth;
            if (center > LOG2_MIN_RATIO) {
                aboveThresholdCount += histogram[i];
            }
        }
        double p50 = percentile(histogram, binWidth, total * 0.5);
        double p999 = percentile(histogram, binWidth, total * 0.999);
        double maxL = maxValue(histogram, binWidth);
        double minL = minValue(histogram, binWidth);
        // 99.9th percentile among the above-threshold (highlight) pixels:
        // walk down from the brightest bin until the top 0.1% of the
        // highlight tail is covered, so extreme single-pixel outliers are
        // excluded while the true scene maximum is preserved.
        double highlightTail;
        if (aboveThresholdCount == 0) {
            highlightTail = LOG2_MIN_RATIO;
        } else {
            double highlightTailTarget =
                    Math.max(1.0, aboveThresholdCount * HIGHLIGHT_TAIL_EXCLUSION);
            highlightTail = percentileFromTop(histogram, binWidth, highlightTailTarget);
        }
        // 99.9th percentile among the below-threshold (shadow) pixels:
        // walk up from the darkest bin until the bottom 0.1% of the shadow
        // tail is covered, so extreme dark single-pixel outliers are
        // excluded while the true scene shadow depth is preserved.
        double shadowTail = 0.0;
        double boostLog2 = Math.max(highlightTail, LOG2_MIN_RATIO);
        float maxContentBoost = (float) Math.pow(2.0, boostLog2);
        if (maxContentBoost < 1.0f) {
            maxContentBoost = 1.0f;
        } else if (maxContentBoost > MAX_CONTENT_BOOST) {
            maxContentBoost = MAX_CONTENT_BOOST;
        }
        float minContentBoost = 1.0f;
        return new Stats(total, p50, p999, highlightTail, shadowTail, maxL, minL,
                aboveThresholdCount, belowThresholdCount,
                maxContentBoost, minContentBoost);
    }

    private static double percentile(int[] histogram, double binWidth, double targetCount) {
        if (targetCount <= 0) {
            return MIN_LOG2_GAIN;
        }
        int cumulative = 0;
        for (int i = 0; i < HISTOGRAM_BINS; i++) {
            cumulative += histogram[i];
            if (cumulative >= targetCount) {
                return MIN_LOG2_GAIN + (i + 0.5) * binWidth;
            }
        }
        return MAX_LOG2_GAIN;
    }

    private static double percentileFromTop(int[] histogram, double binWidth,
                                            double targetCount) {
        int cumulative = 0;
        for (int i = HISTOGRAM_BINS - 1; i >= 0; i--) {
            cumulative += histogram[i];
            if (cumulative >= targetCount) {
                return MIN_LOG2_GAIN + (i + 0.5) * binWidth;
            }
        }
        return LOG2_MIN_RATIO;
    }

    private static double percentileFromBottom(int[] histogram, double binWidth,
                                               double targetCount) {
        int cumulative = 0;
        for (int i = 0; i < HISTOGRAM_BINS; i++) {
            cumulative += histogram[i];
            if (cumulative >= targetCount) {
                return MIN_LOG2_GAIN + (i + 0.5) * binWidth;
            }
        }
        return 0.0;
    }

    private static double maxValue(int[] histogram, double binWidth) {
        for (int i = HISTOGRAM_BINS - 1; i >= 0; i--) {
            if (histogram[i] > 0) {
                return MIN_LOG2_GAIN + (i + 0.5) * binWidth;
            }
        }
        return 0.0;
    }

    private static double minValue(int[] histogram, double binWidth) {
        for (int i = 0; i < HISTOGRAM_BINS; i++) {
            if (histogram[i] > 0) {
                return MIN_LOG2_GAIN + (i + 0.5) * binWidth;
            }
        }
        return 0.0;
    }
}
