package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TiledCompareUtilTest {

    @Test
    public void identicalArraysAreExact() {
        int[] a = {0xFF000000, 0xFFFFFFFF, 0xFF123456, 0x80123456};
        int[] b = a.clone();
        assertEquals(0, TiledCompareUtil.maxAbsDiff(a, b));
        assertEquals(Double.POSITIVE_INFINITY, TiledCompareUtil.psnr(a, b), 0.0);
        assertEquals(TiledCompareUtil.sha256Hex(a), TiledCompareUtil.sha256Hex(b));
    }

    @Test
    public void singleChannelDiffMeasured() {
        int[] a = {0xFF000000};
        int[] b = {0xFF0A0000}; // R differs by 10
        assertEquals(10, TiledCompareUtil.maxAbsDiff(a, b));
        double psnr = TiledCompareUtil.psnr(a, b);
        // MSE = 100/4 = 25 over one pixel x4 channels.
        assertEquals(10.0 * Math.log10(255.0 * 255.0 / 25.0), psnr, 1e-9);
    }

    @Test
    public void maxAcrossChannels() {
        int[] a = {0xFF102030};
        int[] b = {0x80102040}; // A:127, R:0, G:0, B:16
        assertEquals(127, TiledCompareUtil.maxAbsDiff(a, b));
    }

    @Test
    public void lengthMismatchThrows() {
        try {
            TiledCompareUtil.maxAbsDiff(new int[4], new int[5]);
            fail("expected mismatch");
        } catch (IllegalArgumentException expected) {
        }
        try {
            TiledCompareUtil.psnr(new int[4], new int[5]);
            fail("expected mismatch");
        } catch (IllegalArgumentException expected) {
        }
        try {
            TiledCompareUtil.diffHeatmap(new int[4], new int[4], 3, 3);
            fail("expected mismatch");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void byteBufferFloatCompare() {
        java.nio.ByteBuffer a = java.nio.ByteBuffer.allocateDirect(3 * 4)
                .order(java.nio.ByteOrder.nativeOrder());
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocateDirect(3 * 4)
                .order(java.nio.ByteOrder.nativeOrder());
        a.asFloatBuffer().put(new float[]{1f, 2f, 3f});
        b.asFloatBuffer().put(new float[]{1f, 2.5f, 3f});
        a.position(0);
        b.position(0);
        assertEquals(0.5f, TiledCompareUtil.byteBufferFloatMaxAbsDiff(a, b), 1e-9f);
        // Positions untouched by the compare.
        assertEquals(0, a.position());
        assertEquals(0, b.position());
        b.asFloatBuffer().put(1, 2f);
        assertEquals(0f, TiledCompareUtil.byteBufferFloatMaxAbsDiff(a, b), 0f);
    }

    @Test
    public void byteBufferCompareSkipsAlpha() {
        // Same RGB, alpha 0 vs 1 (fresh tile vs recycled main): must pass,
        // since no post shader reads main-chain alpha.
        java.nio.ByteBuffer a = java.nio.ByteBuffer.allocateDirect(2 * 4 * 4)
                .order(java.nio.ByteOrder.nativeOrder());
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocateDirect(2 * 4 * 4)
                .order(java.nio.ByteOrder.nativeOrder());
        a.asFloatBuffer().put(new float[]{0.5f, 0.25f, 0.125f, 0f, 1f, 1f, 1f, 0f});
        b.asFloatBuffer().put(new float[]{0.5f, 0.25f, 0.125f, 1f, 1f, 1f, 1f, 1f});
        a.position(0);
        b.position(0);
        assertEquals(0f, TiledCompareUtil.byteBufferFloatMaxAbsDiff(a, b), 0f);
        // But an RGB difference still fails.
        b.asFloatBuffer().put(0, 0.6f);
        assertTrue(TiledCompareUtil.byteBufferFloatMaxAbsDiff(a, b) > 0f);
    }

    @Test
    public void floatExactTreatsNaNEqual() {        float[] a = {1f, Float.NaN, Float.POSITIVE_INFINITY, -0f};
        float[] b = {1f, Float.NaN, Float.POSITIVE_INFINITY, 0f};
        assertTrue(TiledCompareUtil.floatEqualsExact(a, b));
        assertEquals(0f, TiledCompareUtil.floatMaxAbsDiff(a, b), 0f);
        assertTrue(!TiledCompareUtil.floatEqualsExact(a, new float[3]));
        assertEquals(Float.POSITIVE_INFINITY,
                TiledCompareUtil.floatMaxAbsDiff(a, new float[3]), 0f);
        b[0] = 1.5f;
        assertTrue(!TiledCompareUtil.floatEqualsExact(a, b));
        assertEquals(0.5f, TiledCompareUtil.floatMaxAbsDiff(a, b), 1e-9f);
        b[1] = 0f;
        assertEquals(Float.POSITIVE_INFINITY,
                TiledCompareUtil.floatMaxAbsDiff(a, b), 0f);
    }

    @Test
    public void heatmapMarksOnlyDiffs() {        int[] a = {0xFF000000, 0xFFFFFFFF};
        int[] b = {0xFF000000, 0xFFFFFFFF};
        int[] heat = TiledCompareUtil.diffHeatmap(a, b, 2, 1);
        assertEquals(0xFF000000, heat[0]);
        assertEquals(0xFF000000, heat[1]);
        b[1] = 0xFFFEFEFE; // diff 1 per channel -> v = 16
        heat = TiledCompareUtil.diffHeatmap(a, b, 2, 1);
        assertEquals(0xFF000000, heat[0]);
        assertEquals(0xFF101010, heat[1]);
        assertTrue(TiledCompareUtil.sha256Hex(a).length() == 64);
    }
}
