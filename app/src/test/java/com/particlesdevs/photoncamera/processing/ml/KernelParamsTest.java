package com.particlesdevs.photoncamera.processing.ml;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static org.junit.Assert.assertEquals;

public class KernelParamsTest {

    private static FloatBuffer channelMajor(int w, int h) {
        int plane = w * h;
        FloatBuffer buf = ByteBuffer.allocateDirect(3 * plane * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int i = 0; i < plane; i++) {
            buf.put(i, i * 0.01f);              // s1
            buf.put(plane + i, 100f + i);       // s2
            buf.put(2 * plane + i, -50f + i);   // rho
        }
        return buf;
    }

    private static FloatBuffer band(int w, int rows) {
        return ByteBuffer.allocateDirect(w * rows * 4 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    @Test
    public void interleaveBandMatchesExpectedLayout() {
        int w = 5, h = 3, plane = w * h;
        FloatBuffer src = channelMajor(w, h);
        FloatBuffer dst = band(w, h);
        KernelParams.interleaveBand(src, w, plane, 0, h, dst);
        for (int i = 0; i < plane; i++) {
            assertEquals(i * 0.01f, dst.get(i * 4), 0f);
            assertEquals(100f + i, dst.get(i * 4 + 1), 0f);
            assertEquals(-50f + i, dst.get(i * 4 + 2), 0f);
            assertEquals(1.0f, dst.get(i * 4 + 3), 0f);
        }
        // Absolute access: positions untouched.
        assertEquals(0, src.position());
        assertEquals(0, dst.position());
    }

    @Test
    public void bandedUploadEqualsSingleBand() {
        int w = 5, h = 5, plane = w * h;
        FloatBuffer src = channelMajor(w, h);
        FloatBuffer whole = band(w, h);
        KernelParams.interleaveBand(src, w, plane, 0, h, whole);

        // Non-divisible banding (2 + 2 + 1 rows) must concatenate identically.
        FloatBuffer part = band(w, 2);
        int o = 0;
        for (int y0 = 0; y0 < h; ) {
            int rows = Math.min(2, h - y0);
            KernelParams.interleaveBand(src, w, plane, y0, rows, part);
            for (int i = 0; i < w * rows * 4; i++) {
                assertEquals(whole.get(o + i), part.get(i), 0f);
            }
            o += w * rows * 4;
            y0 += rows;
        }
        assertEquals(w * h * 4, o);
    }
}
