package com.particlesdevs.photoncamera.ui.camera.views.viewfinder;

import static org.junit.Assert.assertEquals;

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
}
