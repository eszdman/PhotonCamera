package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreviewScopeAnalyzerTest {

    private static byte[] rgba(int... pixels) {
        byte[] bytes = new byte[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            bytes[i * 4] = (byte) ((p >> 24) & 0xFF);
            bytes[i * 4 + 1] = (byte) ((p >> 16) & 0xFF);
            bytes[i * 4 + 2] = (byte) ((p >> 8) & 0xFF);
            bytes[i * 4 + 3] = (byte) (p & 0xFF);
        }
        return bytes;
    }

    private static int rgb(int r, int g, int b) {
        return (r << 24) | (g << 16) | (b << 8) | 0xFF;
    }

    @Test
    public void histogramBinsEachChannelExactly() {
        byte[] data = rgba(
                rgb(255, 0, 0),
                rgb(0, 255, 0),
                rgb(0, 0, 255),
                rgb(128, 128, 128));
        int[][] bins = new int[3][256];
        PreviewScopeAnalyzer.fillHistogram(data, 2, 2, bins, 256);

        assertEquals(1, bins[0][255]);
        assertEquals(2, bins[0][0]);
        assertEquals(1, bins[0][128]);
        assertEquals(2, bins[1][0]);
        assertEquals(1, bins[1][255]);
        assertEquals(1, bins[1][128]);
        assertEquals(2, bins[2][0]);
        assertEquals(1, bins[2][255]);
        assertEquals(1, bins[2][128]);
    }

    @Test
    public void histogramScalesToSmallerBinCount() {
        byte[] data = rgba(rgb(255, 255, 255), rgb(0, 0, 0));
        int[][] bins = new int[3][64];
        PreviewScopeAnalyzer.fillHistogram(data, 2, 1, bins, 64);

        assertEquals(1, bins[0][63]);
        assertEquals(1, bins[0][0]);
    }

    @Test
    public void histogramClearsPreviousBins() {
        int[][] bins = new int[3][256];
        bins[0][10] = 7;
        PreviewScopeAnalyzer.fillHistogram(rgba(rgb(1, 2, 3)), 1, 1, bins, 256);
        assertEquals(0, bins[0][10]);
        assertEquals(1, bins[0][1]);
    }

    @Test
    public void histogramIgnoresPixelsBeyondBuffer() {
        byte[] data = rgba(rgb(255, 255, 255));
        int[][] bins = new int[3][256];
        PreviewScopeAnalyzer.fillHistogram(data, 4, 4, bins, 256);
        assertEquals(1, bins[0][255]);
        assertEquals(1, bins[1][255]);
        assertEquals(1, bins[2][255]);
    }

    @Test
    public void sqrtScaleCompressesAndReportsMax() {
        int[][] bins = new int[3][4];
        bins[0][0] = 0;
        bins[0][1] = 4;
        bins[0][2] = 9;
        bins[1][3] = 16;
        bins[2][2] = 25;

        int max = PreviewScopeAnalyzer.applySqrtScale(bins);
        assertEquals(5, max);
        assertEquals(0, bins[0][0]);
        assertEquals(2, bins[0][1]);
        assertEquals(3, bins[0][2]);
        assertEquals(4, bins[1][3]);
        assertEquals(5, bins[2][2]);
    }

    @Test
    public void sqrtScaleOfEmptyBinsIsOne() {
        assertEquals(1, PreviewScopeAnalyzer.applySqrtScale(new int[3][16]));
    }

    @Test
    public void invalidInputIsSafe() {
        int[][] bins = new int[3][256];
        PreviewScopeAnalyzer.fillHistogram(null, 2, 2, bins, 256);
        PreviewScopeAnalyzer.fillHistogram(new byte[0], 0, 0, bins, 256);
        assertEquals(0, bins[0][0]);
    }

    @Test
    public void waveformMapsColumnsAndValues() {
        byte[] data = rgba(rgb(255, 0, 0), rgb(0, 0, 255));
        int[][] counts = new int[3][2 * 256];
        int max = PreviewScopeAnalyzer.fillWaveform(data, 2, 1, counts, 2, 256);

        assertEquals(1, max);
        assertEquals(1, counts[0][255 * 2 + 0]);
        assertEquals(1, counts[0][0 * 2 + 1]);
        assertEquals(1, counts[1][0 * 2 + 0]);
        assertEquals(1, counts[1][0 * 2 + 1]);
        assertEquals(1, counts[2][0 * 2 + 0]);
        assertEquals(1, counts[2][255 * 2 + 1]);
    }

    @Test
    public void waveformAggregatesPixelsPerColumn() {
        byte[] data = rgba(rgb(255, 255, 255), rgb(255, 255, 255),
                rgb(255, 255, 255), rgb(255, 255, 255));
        int[][] counts = new int[3][2 * 256];
        int max = PreviewScopeAnalyzer.fillWaveform(data, 4, 1, counts, 2, 256);

        assertEquals(2, max);
        assertEquals(2, counts[0][255 * 2 + 0]);
        assertEquals(2, counts[0][255 * 2 + 1]);
    }

    @Test
    public void waveformScalesToFewerBins() {
        byte[] data = rgba(rgb(255, 0, 0));
        int[][] counts = new int[3][1 * 64];
        PreviewScopeAnalyzer.fillWaveform(data, 1, 1, counts, 1, 64);
        assertEquals(1, counts[0][63]);
        assertEquals(1, counts[1][0]);
        assertEquals(1, counts[2][0]);
    }

    @Test
    public void waveformBitmapIsAdditiveAndFlipped() {
        int[][] counts = new int[3][8 * 4];
        counts[0][1 * 8 + 1] = 1;
        counts[1][1 * 8 + 1] = 1;
        counts[2][0 * 8 + 6] = 1;
        int[] pixels = new int[8 * 4];
        PreviewScopeAnalyzer.renderWaveformBitmap(counts, 1f, pixels, 8, 4);

        assertEquals(0xFFFFFF00, pixels[2 * 8 + 1]);
        assertEquals(0xFF0000FF, pixels[3 * 8 + 6]);
        assertEquals(0, pixels[7]);
    }

    @Test
    public void waveformBitmapGlowSurroundsCoreWithoutFakingZeros() {
        int[][] counts = new int[3][8 * 4];
        counts[0][1 * 8 + 3] = 1;
        int[] pixels = new int[8 * 4];
        PreviewScopeAnalyzer.renderWaveformBitmap(counts, 1f, pixels, 8, 4);

        int core = pixels[2 * 8 + 3];
        assertEquals(255, (core >> 16) & 0xFF);
        int glow = pixels[2 * 8 + 5];
        assertTrue(glow != 0 && ((glow >> 16) & 0xFF) < 255);
        assertEquals(0, pixels[0]);
    }

    @Test
    public void waveformBitmapAppliesPercentileNormalizer() {
        int[][] counts = new int[3][1 * 1];
        counts[0][0] = 1;
        int[] pixels = new int[1];
        PreviewScopeAnalyzer.renderWaveformBitmap(counts, 4f, pixels, 1, 1);
        assertEquals(128, (pixels[0] >> 16) & 0xFF);

        counts[0][0] = 100;
        PreviewScopeAnalyzer.renderWaveformBitmap(counts, 4f, pixels, 1, 1);
        assertEquals(255, (pixels[0] >> 16) & 0xFF);
    }

    @Test
    public void waveformBitmapClampsChannelSums() {
        int[][] counts = new int[3][1 * 1];
        counts[0][0] = 1;
        counts[1][0] = 1;
        counts[2][0] = 1;
        int[] pixels = new int[1];
        PreviewScopeAnalyzer.renderWaveformBitmap(counts, 1f, pixels, 1, 1);
        assertEquals(0xFFFFFFFF, pixels[0]);
    }

    @Test
    public void percentileNormalizerPicksRequestedPercentile() {
        int[][] counts = new int[3][100];
        for (int i = 0; i < 90; i++) {
            counts[0][i] = 1;
        }
        for (int i = 90; i < 100; i++) {
            counts[1][i] = 10;
        }
        assertEquals(10f, PreviewScopeAnalyzer.percentileNormalizer(counts, 10, 0.99f), 0.001f);
        assertEquals(1f, PreviewScopeAnalyzer.percentileNormalizer(counts, 10, 0.50f), 0.001f);
    }

    @Test
    public void percentileNormalizerGuardsDegenerateSkew() {
        int[][] counts = new int[3][100];
        for (int i = 0; i < 99; i++) {
            counts[0][i] = 1;
        }
        counts[1][0] = 100;
        float normalizer = PreviewScopeAnalyzer.percentileNormalizer(counts, 100, 0.99f);
        assertTrue(normalizer >= 3f && normalizer < 4f);
    }

    @Test
    public void waveformEmptyCountsStayTransparent() {
        int[] pixels = new int[4];
        PreviewScopeAnalyzer.renderWaveformBitmap(new int[3][4], 1f, pixels, 2, 2);
        for (int pixel : pixels) {
            assertEquals(0, pixel);
        }
    }
}
