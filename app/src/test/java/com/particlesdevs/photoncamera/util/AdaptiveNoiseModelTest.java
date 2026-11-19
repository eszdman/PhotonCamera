package com.particlesdevs.photoncamera.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AdaptiveNoiseModelTest {
    private static final int BINS = 64;
    private static final double VARIANCE_SCALE = 1000.0;

    @Test
    public void calibrationUsesMatchingHdrLayersAndIsoOnly() {
        org.junit.Assert.assertArrayEquals(new int[]{0, 2, 5}, AdaptiveNoiseModel.selectFrames(
                new float[]{1, 4, 1, 8, 1, 1}, new int[]{100, 100, 100, 100, 200, 100}, 100, 9));
        org.junit.Assert.assertArrayEquals(new int[]{0, 5}, AdaptiveNoiseModel.selectFrames(
                new float[]{1, 4, 1, 8, 1, 1}, new int[]{100, 100, 100, 100, 200, 100}, 100, 2));
    }

    @Test
    public void mismatchedIsoOrScaledReferenceCannotPoisonPersistentModel() {
        assertEquals(0, AdaptiveNoiseModel.selectFrames(new float[]{1, 1},
                new int[]{100, 100}, 200, 9).length);
        assertEquals(0, AdaptiveNoiseModel.selectFrames(new float[]{4, 4},
                new int[]{100, 100}, 100, 9).length);
    }

    @Test
    public void brightSceneFitsAcrossFullBrightnessRange() {
        int[] histogram = new int[BINS * BINS];
        double s = 0.003, o = 1e-5;
        for (int row = 40; row <= 60; row += 5) {
            double b = brightness(row);
            int column = (int) (Math.sqrt(s * b + o) * VARIANCE_SCALE);
            histogram[row * BINS + column] = 200;
        }
        AdaptiveNoiseModel.Model fitted = fit(histogram, 2.0);
        assertNotNull(fitted);
        // All of these brightness values were previously clipped into row63.
        for (double b : new double[]{0.4, 0.6, 0.85}) {
            double expected = s * b + o;
            assertEquals(expected, fitted.s * b + fitted.o, expected * 0.06);
        }
    }

    @Test
    public void negativeSlopeRefitsInterceptInsteadOfKeepingInflatedExtrapolation() {
        int[] histogram = new int[BINS * BINS];
        int[] rows = {8, 24, 40, 56};
        int[] columns = {50, 40, 30, 20};
        double expectedO = 0;
        for (int i = 0; i < rows.length; i++) {
            histogram[rows[i] * BINS + columns[i]] = 100;
            expectedO += variance(columns[i]) / rows.length;
        }
        AdaptiveNoiseModel.Model fitted = fit(histogram, 0);
        assertNotNull(fitted);
        assertEquals(0, fitted.s, 0);
        assertEquals(expectedO, fitted.o, 1e-12);
    }

    @Test
    public void negativeInterceptRefitsSlopeThroughOrigin() {
        int[] histogram = new int[BINS * BINS];
        double sumBv = 0, sumB2 = 0;
        for (int row = 24; row <= 56; row += 8) {
            double b = brightness(row);
            int column = (int) (Math.sqrt(0.004 * (b - 0.1)) * VARIANCE_SCALE);
            histogram[row * BINS + column] = 100;
            sumBv += b * variance(column);
            sumB2 += b * b;
        }
        AdaptiveNoiseModel.Model fitted = fit(histogram, 0);
        assertNotNull(fitted);
        assertEquals(0, fitted.o, 0);
        assertEquals(sumBv / sumB2, fitted.s, 1e-12);
    }

    @Test
    public void saturatedVarianceCellsNeverInfluenceFit() {
        int[] histogram = new int[BINS * BINS];
        for (int row : new int[]{8, 24, 40, 56})
            histogram[row * BINS + 10] = 100;
        AdaptiveNoiseModel.Model clean = fit(histogram, 0);
        assertNotNull(clean);
        for (int row = 0; row < BINS; row++)
            histogram[row * BINS + BINS - 1] = 1000000;
        AdaptiveNoiseModel.Model clipped = fit(histogram, 0);
        assertNotNull(clipped);
        assertEquals(clean.s, clipped.s, 1e-12);
        assertEquals(clean.o, clipped.o, 1e-12);
    }

    @Test
    public void narrowOrSparseSceneRetainsPriorInsteadOfEstimatingNoise() {
        int[] histogram = new int[BINS * BINS];
        for (int row = 30; row <= 32; row++)
            histogram[row * BINS + 20] = 100;
        assertNull(fit(histogram, 2));
        histogram = new int[BINS * BINS];
        for (int row : new int[]{8, 24, 40, 56})
            histogram[row * BINS + 20] = 1;
        assertNull(fit(histogram, 2));
    }

    @Test
    public void clippedOrEmptyHistogramCannotBecomeNoiseCalibration() {
        int[] histogram = new int[BINS * BINS];
        assertNull(fit(histogram, 2));
        for (int row = 0; row < BINS; row++)
            histogram[row * BINS + BINS - 1] = 1000;
        assertNull(fit(histogram, 2));
        assertNull(AdaptiveNoiseModel.fitHistogram(histogram, BINS, BINS,
                BINS, VARIANCE_SCALE, Double.NaN, 45, 2));
    }

    @Test
    public void lowIsoTextureLeakCannotExceedVarianceAllowanceAtAnyBrightness() {
        AdaptiveNoiseModel.Model contaminated = new AdaptiveNoiseModel.Model(
                0.000774, 0.001303, 20);
        double baselineS = 5e-5, baselineO = 1e-7;
        AdaptiveNoiseModel.Model bounded = AdaptiveNoiseModel.limitToBaseline(
                contaminated, baselineS, baselineO, 2);
        for (double b : new double[]{0, 0.01, 0.2, 0.5, 1}) {
            double variance = bounded.s * b + bounded.o;
            assertTrue(variance <= 2 * (baselineS * b + baselineO) + 1e-15);
        }
        assertEquals(contaminated.points, bounded.points);
        assertTrue(AdaptiveNoiseModel.isLowIso(200, 800));
        assertFalse(AdaptiveNoiseModel.isLowIso(201, 800));
        assertTrue(AdaptiveNoiseModel.isLowIso(800, 6400));
    }

    @Test
    public void lowIsoClampedSlopeRestoresCompleteBaselineModel() {
        AdaptiveNoiseModel.Model fitted = new AdaptiveNoiseModel.Model(1e-10, 6e-5, 20);
        AdaptiveNoiseModel.Model bounded = AdaptiveNoiseModel.limitToBaseline(fitted, 5e-5, 1e-7, 2);
        assertEquals(1e-4, bounded.s, 1e-15);
        assertEquals(2e-7, bounded.o, 1e-15);
    }

    @Test
    public void zeroReadNoiseBaselineStillBoundsTextureLeak() {
        AdaptiveNoiseModel.Model fitted = new AdaptiveNoiseModel.Model(.001, .001, 20);
        AdaptiveNoiseModel.Model bounded = AdaptiveNoiseModel.limitToBaseline(fitted, 5e-5, 0, 2);
        assertEquals(1e-4, bounded.s, 1e-15);
        assertEquals(0, bounded.o, 0);
    }

    @Test
    public void readOffsetLimitDoesNotIncreaseAHealthyShotSlope() {
        AdaptiveNoiseModel.Model fitted = new AdaptiveNoiseModel.Model(.005, .0002, 20);
        AdaptiveNoiseModel.Model bounded = AdaptiveNoiseModel.limitToBaseline(fitted, .015, 3e-5, 2);
        assertEquals(.005, bounded.s, 1e-15);
        assertEquals(6e-5, bounded.o, 1e-15);
    }

    @Test
    public void spatialRowsRecoverGaussianNoiseInLowerRawRegionWithoutBaselineCaps() {
        int[] histogram = spatialHistogram(1200, 0.14, 24000, false);
        AdaptiveNoiseModel.Model fitted = spatialFit(histogram, 0.14, 24000);
        assertSpatialVariance(fitted, 0.04);
        assertTrue(fitted.rows >= 35);
        assertEquals(0, fitted.textureRows);
    }

    @Test
    public void highNoiseRowsRetainShotSlopeAfterBlackClippingRejection() {
        int[] histogram = new int[BINS * BINS];
        double response = 0.14, scale = 1500;
        for (int row = 1; row < BINS - 1; row++)
            setGaussianRow(histogram, row, 500, response, scale,
                    5e-3 * spatialBrightness(row) + 1e-5);
        AdaptiveNoiseModel.Model fitted = spatialFit(histogram, response, scale);
        assertNotNull(fitted);
        assertTrue(fitted.s > 0);
        for (double b : new double[]{0.15, 0.3, 0.5}) {
            double expected = 5e-3 * b + 1e-5;
            assertEquals(expected, fitted.s * b + fitted.o, expected * 0.05);
        }
    }

    @Test
    public void nearbyCleanBrightnessRowsRejectLocalizedTexture() {
        AdaptiveNoiseModel.Model fitted = spatialFit(
                spatialHistogram(600, 0.14, 24000, true), 0.14, 24000);
        assertSpatialVariance(fitted, 0.07);
        assertTrue(fitted.textureRows >= 8);
        assertTrue(fitted.rows >= 25);
    }

    @Test
    public void sparseSpatialRowsStillRecoverNoiseWithLocalizedTexture() {
        // Approximately 1800 samples, matching a 128x128 packed image at
        // stride 3. No known noise model or post-fit cap is supplied.
        int[] histogram = new int[BINS * BINS];
        java.util.Random random = new java.util.Random(41);
        for (int row = 18; row <= 58; row++) {
            double b = spatialBrightness(row);
            double sigma = Math.sqrt(5e-5 * b + 1e-7);
            if (b >= 0.08 && b <= 0.15) sigma *= 3.5;
            for (int sample = 0; sample < 44; sample++) {
                int column = (int) (Math.abs(random.nextGaussian()) * sigma * 0.14 * 24000);
                histogram[row * BINS + Math.min(column, BINS - 1)]++;
            }
        }
        AdaptiveNoiseModel.Model fitted = spatialFit(histogram, 0.14, 24000);
        assertSpatialVariance(fitted, 0.35);
        assertTrue(fitted.textureRows > 0);
    }

    @Test
    public void isolatedLowBrightnessRowCannotBecomeTheNoiseFloor() {
        int[] histogram = spatialHistogram(800, 0.14, 40000, false);
        setGaussianRow(histogram, 35, 800, 0.14, 40000,
                0.09 * (5e-5 * spatialBrightness(35) + 1e-7));
        AdaptiveNoiseModel.Model fitted = spatialFit(histogram, 0.14, 40000);
        assertSpatialVariance(fitted, 0.05);
        assertEquals(40, fitted.rows);
    }

    @Test
    public void twoAdjacentLowRowsNeedIndependentNeighborSupport() {
        int[] histogram = spatialHistogram(800, 0.14, 40000, false);
        for (int row = 35; row <= 36; row++)
            setGaussianRow(histogram, row, 800, 0.14, 40000,
                    0.09 * (5e-5 * spatialBrightness(row) + 1e-7));
        AdaptiveNoiseModel.Model fitted = spatialFit(histogram, 0.14, 40000);
        assertSpatialVariance(fitted, 0.05);
        assertEquals(39, fitted.rows);
    }

    @Test
    public void overflowingRowsAreCensoredAndCountedAsTexture() {
        int[] histogram = spatialHistogram(800, 0.14, 24000, false);
        for (int row = 30; row <= 42; row++) {
            java.util.Arrays.fill(histogram, row * BINS, (row + 1) * BINS, 0);
            // Large residuals must not disappear and leave only a quiet tail.
            histogram[row * BINS + 2] = 100;
            histogram[row * BINS + BINS - 1] = 700;
        }
        AdaptiveNoiseModel.Model fitted = spatialFit(histogram, 0.14, 24000);
        assertSpatialVariance(fitted, 0.06);
        assertEquals(13, fitted.textureRows);
        assertEquals(28, fitted.rows);
    }

    @Test
    public void spatialHistogramScaleAndResidualResponsePreserveSensorUnits() {
        AdaptiveNoiseModel.Model first = spatialFit(
                spatialHistogram(1200, 0.14, 24000, false), 0.14, 24000);
        AdaptiveNoiseModel.Model second = spatialFit(
                spatialHistogram(1200, 0.30, 11000, false), 0.30, 11000);
        assertSpatialVariance(first, 0.04);
        assertSpatialVariance(second, 0.04);
        for (double b : new double[]{0.03, 0.1, 0.2})
            assertEquals(first.s * b + first.o, second.s * b + second.o,
                    (5e-5 * b + 1e-7) * 0.02);
    }

    @Test
    public void blackClippedRowsAndBrightnessOverflowCannotLowerSpatialNoise() {
        int[] histogram = spatialHistogram(800, 0.14, 24000, false);
        for (int row = 0; row <= 17; row++) histogram[row * BINS] = 10000;
        histogram[(BINS - 1) * BINS + 1] = 1000000;
        assertSpatialVariance(spatialFit(histogram, 0.14, 24000), 0.05);
    }

    @Test
    public void insufficientSpatialCoverageOrSamplesRetainsPrior() {
        int[] narrow = spatialHistogram(800, 0.14, 24000, false);
        for (int row = 0; row < BINS; row++)
            if (row < 30 || row > 34)
                java.util.Arrays.fill(narrow, row * BINS, (row + 1) * BINS, 0);
        assertNull(spatialFit(narrow, 0.14, 24000));
        assertNull(spatialFit(spatialHistogram(12, 0.14, 24000, false), 0.14, 24000));
        int[] overflow = new int[BINS * BINS];
        for (int row = 0; row < BINS; row++) overflow[row * BINS + BINS - 1] = 100;
        assertNull(spatialFit(overflow, 0.14, 24000));
        assertNull(spatialFit(new int[BINS * BINS], Double.NaN, 24000));
    }

    private static AdaptiveNoiseModel.Model spatialFit(int[] histogram, double response, double scale) {
        return AdaptiveNoiseModel.fitSpatialHistogram(histogram, BINS, BINS,
                BINS * Math.sqrt(3), scale, response);
    }

    private static void assertSpatialVariance(AdaptiveNoiseModel.Model fitted, double tolerance) {
        assertNotNull(fitted);
        assertTrue(fitted.s >= 0 && fitted.o >= 0);
        for (double b : new double[]{0.03, 0.1, 0.2}) {
            double expected = 5e-5 * b + 1e-7;
            assertEquals("sensor variance at brightness " + b, expected,
                    fitted.s * b + fitted.o, expected * tolerance);
        }
    }

    private static int[] spatialHistogram(int samples, double response, double scale, boolean texture) {
        int[] histogram = new int[BINS * BINS];
        for (int row = 18; row <= 58; row++) {
            double b = spatialBrightness(row);
            double variance = 5e-5 * b + 1e-7;
            if (texture && b >= 0.08 && b <= 0.15) variance *= 12.25;
            setGaussianRow(histogram, row, samples, response, scale, variance);
        }
        return histogram;
    }

    private static void setGaussianRow(int[] histogram, int row, int samples,
                                       double response, double scale, double variance) {
        double stdBins = Math.sqrt(variance) * response * scale;
        int before = 0;
        for (int column = 0; column < BINS; column++) {
            int cumulative = column == BINS - 1 ? samples
                    : (int) Math.round(samples * erf((column + 1) / (Math.sqrt(2) * stdBins)));
            histogram[row * BINS + column] = cumulative - before;
            before = cumulative;
        }
    }

    // Independent analytic half-normal CDF used to synthesize histogram
    // populations; the fitter does not use this approximation.
    private static double erf(double x) {
        double t = 1 / (1 + 0.3275911 * x);
        return 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
    }

    private static double spatialBrightness(int row) {
        double b = (row + 0.5) / (BINS * Math.sqrt(3));
        return b * b;
    }

    private static AdaptiveNoiseModel.Model fit(int[] histogram, double gate) {
        return AdaptiveNoiseModel.fitHistogram(histogram, BINS, BINS, BINS,
                VARIANCE_SCALE, 1, 45, gate);
    }

    private static double brightness(int row) {
        double b = (row + 0.5) / BINS;
        return b * b;
    }

    private static double variance(int column) {
        double sigma = (column + 0.5) / VARIANCE_SCALE;
        return sigma * sigma;
    }
}
