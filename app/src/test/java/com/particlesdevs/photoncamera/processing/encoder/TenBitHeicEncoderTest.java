package com.particlesdevs.photoncamera.processing.encoder;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

/**
 * JVM tests for the packed-sink tile copy used by the 10-bit encoder paths.
 */
public class TenBitHeicEncoderTest {

    private static ByteBuffer makeFrame(int w, int h) {
        ByteBuffer buf = ByteBuffer.allocate(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                buf.putInt((y << 16) | (x << 8) | 0xAB);
            }
        }
        buf.rewind();
        return buf;
    }

    private static void assertTile(ByteBuffer src, int fullW, int fullH,
            int tx, int ty, int tileW, int tileH) {
        ByteBuffer ref = src.duplicate();
        ref.clear();
        ByteBuffer out = ByteBuffer.allocate(tileW * tileH * 4);
        TenBitHeicEncoder.copyAbgr1010102Tile(src, fullW, fullH, tx, ty, tileW, tileH, out);
        for (int r = 0; r < tileH; r++) {
            for (int c = 0; c < tileW; c++) {
                int sx = Math.min(tx + c, fullW - 1);
                int sy = Math.min(ty + r, fullH - 1);
                int expected = ref.getInt((sy * fullW + sx) * 4);
                int actual = out.getInt((r * tileW + c) * 4);
                assertEquals("pixel " + c + "," + r, expected, actual);
            }
        }
    }

    /**
     * Regression: the per-row loop used to narrow the duplicate's limit to the
     * row it had just read and then position() beyond it (newPosition > limit
     * on every tile taller than one row). The source may also be left with an
     * arbitrary position/limit by the GL sink; the copy must ignore both.
     */
    @Test
    public void multiRowTileWithMutatedSource() {
        ByteBuffer src = makeFrame(16, 8);
        src.position(20);
        src.limit(40);
        assertTile(src, 16, 8, 0, 0, 8, 4);
        assertTile(src, 16, 8, 8, 2, 8, 4);
    }

    /** Edge tiles replicate the last valid column/row into the padding. */
    @Test
    public void edgeTileReplicatesLastPixel() {
        ByteBuffer src = makeFrame(10, 6);
        assertTile(src, 10, 6, 0, 0, 8, 4);
        assertTile(src, 10, 6, 6, 4, 8, 4);
    }
}
