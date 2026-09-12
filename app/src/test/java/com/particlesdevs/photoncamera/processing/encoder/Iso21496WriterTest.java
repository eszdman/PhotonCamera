package com.particlesdevs.photoncamera.processing.encoder;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Iso21496WriterTest {

    private static byte[] expected(float gainMin, float gainMax, float hdrCap,
            int altN, int altD, int maxN, int maxD) {
        ByteBuffer bb = ByteBuffer.allocate(62).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) 0); // tmap version
        bb.putShort((short) 0); // min_version
        bb.putShort((short) 0); // writer_version
        bb.put((byte) 0x40); // single channel, base color space
        bb.putInt(0);
        bb.putInt(1);
        bb.putInt(altN);
        bb.putInt(altD);
        bb.putInt(0);
        bb.putInt(1);
        bb.putInt(maxN);
        bb.putInt(maxD);
        bb.putInt(1);
        bb.putInt(1);
        bb.putInt(1);
        bb.putInt(64);
        bb.putInt(1);
        bb.putInt(64);
        return bb.array();
    }

    @Test
    public void goldenTypicalValues() {
        // gainMin=0, gainMax=2.5, hdrCap=2.5: exact small fractions.
        byte[] actual = Iso21496Writer.tmapPayload(0f, 2.5f, 2.5f);
        assertEquals(62, actual.length);
        assertArrayEquals(expected(0f, 2.5f, 2.5f, 5, 2, 5, 2), actual);
    }

    @Test
    public void goldenIntegerValues() {
        byte[] actual = Iso21496Writer.tmapPayload(0f, 6f, 6f);
        assertArrayEquals(expected(0f, 6f, 6f, 6, 1, 6, 1), actual);
    }

    @Test
    public void degenerateInputsSanitized() {
        byte[] actual = Iso21496Writer.tmapPayload(Float.NaN, Float.NaN, Float.NaN);
        // gainMin=0, gainMax=1e-3, hdrCap=1e-3: must still parse structurally.
        assertEquals(0, actual[0]);
        assertEquals(0x40, actual[5] & 0xFF);
        assertEquals(62, actual.length);
        // Base headroom must be exactly 0/1.
        assertEquals(0, ByteBuffer.wrap(actual, 6, 4).order(ByteOrder.BIG_ENDIAN).getInt());
        assertEquals(1, ByteBuffer.wrap(actual, 10, 4).order(ByteOrder.BIG_ENDIAN).getInt());
    }

    @Test
    public void fractionsAreExact() {
        assertArrayEquals(new long[]{0, 1}, Iso21496Writer.toFraction(0.0));
        assertArrayEquals(new long[]{1, 1}, Iso21496Writer.toFraction(1.0));
        assertArrayEquals(new long[]{5, 2}, Iso21496Writer.toFraction(2.5));
        assertArrayEquals(new long[]{1, 64}, Iso21496Writer.toFraction(1.0 / 64.0));
        assertArrayEquals(new long[]{-3, 2}, Iso21496Writer.toFraction(-1.5));
        long[] third = Iso21496Writer.toFraction(1.0 / 3.0);
        assertTrue(third[1] > 0 && third[1] <= 100000L);
        assertTrue(Math.abs(third[0] / (double) third[1] - 1.0 / 3.0) < 1e-9);
    }
}
