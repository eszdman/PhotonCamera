package com.particlesdevs.photoncamera.processing.ultrahdr;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UltraHdrGainMapStatsTest {

    private static float[] flatGains(int total) {
        float[] data = new float[total];
        java.util.Arrays.fill(data, 0.0f);
        return data;
    }

    private static float[] gainsWithHighlight(int total, int highlightCount, float log2Gain) {
        float[] data = new float[total];
        java.util.Arrays.fill(data, 0.0f);
        for (int i = 0; i < highlightCount; i++) {
            data[i] = log2Gain;
        }
        return data;
    }

    @Test
    public void flatSceneStillGetsMinimalBoost() {
        // Never skipped: a flat scene yields the 1.05x floor boost with an
        // all-zero gain map (exact no-op reconstruction) and no shadow
        // darkening (min content boost = 1.0).
        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(flatGains(1_000_000));
        assertTrue(stats.maxContentBoost > 0.0f);
        assertEquals(1.05f, stats.maxContentBoost, 0.001f);
        assertEquals(1.0f, stats.minContentBoost, 0.001f);
        assertEquals(0, stats.aboveThresholdCount);
        assertEquals(0, stats.belowThresholdCount);
    }

    @Test
    public void emptyInputHasNoBoost() {
        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(null);
        assertEquals(0.0f, stats.maxContentBoost, 0.0f);
        assertEquals(1.0f, stats.minContentBoost, 0.0f);
        stats = UltraHdrGainMapStats.compute(new float[0]);
        assertEquals(0.0f, stats.maxContentBoost, 0.0f);
        assertEquals(1.0f, stats.minContentBoost, 0.0f);
    }

    @Test
    public void shadowSceneProducesNoGain() {
        // Negative ratios are intentionally discarded so shadows remain the
        // SDR base rendition.
        float[] data = gainsWithHighlight(1_000_000, 1_000, -1.0f);
        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(data);
        assertEquals(0, stats.belowThresholdCount);
        assertEquals(1.0f, stats.minContentBoost, 0.001f);
        assertEquals(1.05f, stats.maxContentBoost, 0.001f);
    }

    @Test
    public void mixedSceneKeepsHighlightsAndIgnoresShadows() {
        int total = 100_000;
        float[] data = new float[total];
        java.util.Arrays.fill(data, 0.0f);
        java.util.Arrays.fill(data, 0, 1_000, -1.0f);
        java.util.Arrays.fill(data, 1_000, 2_000, 2.0f);

        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(data);

        assertEquals(0, stats.belowThresholdCount);
        assertEquals(1_000, stats.aboveThresholdCount);
        assertEquals(1.0f, stats.minContentBoost, 0.001f);
        assertEquals(4.0f, stats.maxContentBoost, 0.02f);
    }

    @Test
    public void nonFiniteGainValuesAreNeutral() {
        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(
                new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY});

        assertEquals(0, stats.aboveThresholdCount);
        assertEquals(0, stats.belowThresholdCount);
        assertEquals(1.05f, stats.maxContentBoost, 0.001f);
        assertEquals(1.0f, stats.minContentBoost, 0.001f);
    }

    @Test
    public void gainBinsClampToThePositiveRange() {
        assertEquals(0, UltraHdrGainMapStats.binForGain(-100.0f));
        assertEquals(UltraHdrGainMapStats.HISTOGRAM_BINS - 1,
                UltraHdrGainMapStats.binForGain(100.0f));
        assertEquals(0, UltraHdrGainMapStats.binForGain(-1.0f));
        assertTrue(UltraHdrGainMapStats.binForGain(1.0f) > 0);
    }

    @Test
    public void sparseShadowExcursionsDoNotCreateGain() {
        int total = 100_000;
        float[] data = new float[total];
        java.util.Arrays.fill(data, 0.0f);
        java.util.Arrays.fill(data, 0, 50, -2.0f);

        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(data);

        assertEquals(1.0f, stats.minContentBoost, 0.001f);
    }

    @Test
    public void invalidHistogramShapeHasNoGainData() {
        UltraHdrGainMapStats.Stats stats =
                UltraHdrGainMapStats.computeFromHistogram(new int[1], 1);
        assertEquals(0.0f, stats.maxContentBoost, 0.0f);
        assertEquals(1.0f, stats.minContentBoost, 0.0f);
    }

    @Test
    public void negativeOutliersDoNotCreateGain() {
        int total = 1_000_000;
        float[] data = new float[total];
        java.util.Arrays.fill(data, 0, 5_000, -0.5f);
        data[0] = -3.0f;
        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(data);
        assertEquals(1.0f, stats.minContentBoost, 0.001f);
    }

    @Test
    public void deepShadowClampsToMinBoost() {
        // Negative values are discarded while the positive side stays at the
        // standard 1.05x floor.
        float[] data = new float[100_000];
        java.util.Arrays.fill(data, -4.0f);
        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(data);
        assertEquals(1.0f, stats.minContentBoost, 0.001f);
        assertEquals(1.05f, stats.maxContentBoost, 0.001f);
    }

    @Test
    public void tinyHighlightClusterIsKept() {
        // 100 highlight pixels: below the old skip minimum, now kept.
        UltraHdrGainMapStats.Stats stats =
                UltraHdrGainMapStats.compute(gainsWithHighlight(1_000_000, 100, 0.6f));
        assertTrue(stats.maxContentBoost > 0.0f);
        assertEquals(100, stats.aboveThresholdCount);
        // 99.9th percentile of the 100-pixel tail == the highlight value.
        assertEquals(1.516f, stats.maxContentBoost, 0.01f);
    }

    @Test
    public void sparseHighlightIsKeptWithAccurateBoost() {
        // 0.05% coverage at ratio 1.52.
        float log2Gain = (float) (Math.log(1.52) / Math.log(2.0));
        UltraHdrGainMapStats.Stats stats =
                UltraHdrGainMapStats.compute(gainsWithHighlight(1_000_000, 500, log2Gain));
        assertTrue(stats.maxContentBoost > 0.0f);
        assertEquals(500, stats.aboveThresholdCount);
        assertEquals(1.52f, stats.maxContentBoost, 0.01f);
    }

    @Test
    public void moderateHighlightCoverageIsKept() {
        // 0.2% coverage at ratio 2.0.
        float log2Gain = 1.0f;
        UltraHdrGainMapStats.Stats stats =
                UltraHdrGainMapStats.compute(gainsWithHighlight(1_000_000, 2_000, log2Gain));
        assertTrue(stats.maxContentBoost > 0.0f);
        assertEquals(2.0f, stats.maxContentBoost, 0.01f);
    }

    @Test
    public void broadHighlightSceneUsesTailPercentile() {
        // 10% of pixels at log2 gain 1.0, 0.5% of pixels at log2 gain 2.5.
        int total = 1_000_000;
        float[] data = new float[total];
        java.util.Arrays.fill(data, 0, 100_000, 1.0f);
        java.util.Arrays.fill(data, 100_000, 105_000, 2.5f);
        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(data);
        assertTrue(stats.maxContentBoost > 0.0f);
        // Highlight tail 99.9th percentile: the top 0.1% of the 105k
        // highlight pixels are the 105 pixels at 2.5 -> boost ~5.66x.
        assertEquals(5.66f, stats.maxContentBoost, 0.1f);
    }

    @Test
    public void maxBoostIsClampedToSixteen() {
        float[] data = new float[1_000_000];
        java.util.Arrays.fill(data, 0, 500_000, 4.0f);
        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(data);
        assertTrue(stats.maxContentBoost > 0.0f);
        // Bin-centre quantisation of the histogram caps the value just below
        // 16.0 (2^3.996); assert the ceiling behaviour.
        assertEquals(15.96f, stats.maxContentBoost, 0.05f);
        assertTrue(stats.maxContentBoost <= 16.0f);
    }

    @Test
    public void outlierPixelsDoNotInflateBoost() {
        // A single pixel at log2 gain 3.0 among a large low-level highlight
        // region must not drive the content boost.
        int total = 1_000_000;
        float[] data = new float[total];
        java.util.Arrays.fill(data, 0, 5_000, (float) (Math.log(1.5) / Math.log(2.0)));
        data[0] = 3.0f;
        UltraHdrGainMapStats.Stats stats = UltraHdrGainMapStats.compute(data);
        assertTrue(stats.maxContentBoost > 0.0f);
        // 99.9th percentile of the 5001-pixel highlight tail excludes the top
        // 5 pixels, so the boost reflects the 1.5x region, not the 8x outlier.
        assertEquals(1.5f, stats.maxContentBoost, 0.05f);
        assertFalse(stats.highlightTail > 1.0);
    }
}
