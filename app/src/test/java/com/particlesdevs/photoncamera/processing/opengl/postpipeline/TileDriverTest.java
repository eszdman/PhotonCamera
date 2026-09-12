package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TileDriverTest {

    @Test
    public void evenSplit() {
        List<int[]> bands = TileDriver.computeBands(1024, 256);
        assertEquals(4, bands.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i * 256, bands.get(i)[0]);
            assertEquals((i + 1) * 256, bands.get(i)[1]);
        }
    }

    @Test
    public void raggedTailClamped() {
        List<int[]> bands = TileDriver.computeBands(1000, 256);
        assertEquals(4, bands.size());
        assertEquals(768, bands.get(3)[0]);
        assertEquals(1000, bands.get(3)[1]);
        // Contiguous, gap-free coverage.
        for (int i = 1; i < bands.size(); i++) {
            assertEquals(bands.get(i - 1)[1], bands.get(i)[0]);
        }
    }

    @Test
    public void oversizeTileIsOneBand() {
        List<int[]> bands = TileDriver.computeBands(100, 2048);
        assertEquals(1, bands.size());
        assertEquals(0, bands.get(0)[0]);
        assertEquals(100, bands.get(0)[1]);
    }

    @Test
    public void emptyImageIsNoBands() {
        assertEquals(0, TileDriver.computeBands(0, 256).size());
    }

    @Test
    public void snapBandsAlignedTopMidBottom() {
        java.util.List<int[]> bands = TileDriver.snapBands(6944, 1024);
        assertEquals(3, bands.size());
        assertEquals(0, bands.get(0)[0]);
        assertEquals(2048, bands.get(0)[1]);
        assertEquals(4096, bands.get(2)[0]);
        assertEquals(6944, bands.get(2)[1]);
        // Middle band snapped to the grid and covering the center.
        assertEquals(0, bands.get(1)[0] % 1024);
        assertEquals(0, bands.get(1)[1] % 1024);
        assertTrue(bands.get(1)[0] <= 3472 && 3472 <= bands.get(1)[1]);
    }

    @Test
    public void snapBandsDedupesSmallImages() {
        java.util.List<int[]> bands = TileDriver.snapBands(512, 1024);
        assertEquals(1, bands.size());
        assertEquals(0, bands.get(0)[0]);
        assertEquals(512, bands.get(0)[1]);
    }

    @Test
    public void clampInteriorKeepsTopBandWhole() {
        int[] keep = TileDriver.clampInterior(0, 1024, 3072, 2, false);
        assertEquals(0, keep[0]);
        assertEquals(1024, keep[1]);
    }

    @Test
    public void clampInteriorDropsBottomHaloRows() {
        int[] keep = TileDriver.clampInterior(2048, 1024, 3072, 2, false);
        assertEquals(2048, keep[0]);
        assertEquals(3070, keep[1]);
    }

    @Test
    public void clampInteriorTopSkipDropsFirstRowOffTop() {
        int[] keep = TileDriver.clampInterior(1024, 1024, 3072, 5, true);
        assertEquals(1025, keep[0]);
        assertEquals(2048, keep[1]);
        int[] top = TileDriver.clampInterior(0, 1024, 3072, 5, true);
        assertEquals(0, top[0]);
        assertEquals(1024, top[1]);
    }

    @Test
    public void clampInteriorEmptyWhenBandInsideHalo() {
        int[] keep = TileDriver.clampInterior(3070, 2, 3072, 5, true);
        assertEquals(keep[0], keep[1]);
    }

    @Test
    public void expandWindowMiddleBand() {
        int[] w = TileDriver.expandWindow(1024, 2048, 3072, 5);
        assertEquals(1019, w[0]);
        assertEquals(2053, w[1]);
    }

    @Test
    public void expandWindowClampsTopAndBottom() {
        int[] top = TileDriver.expandWindow(0, 2048, 3072, 7);
        assertEquals(0, top[0]);
        assertEquals(2055, top[1]);
        int[] bot = TileDriver.expandWindow(2048, 3072, 3072, 7);
        assertEquals(2041, bot[0]);
        assertEquals(3072, bot[1]);
    }

    @Test
    public void expandWindowZeroHaloIsIdentity() {
        int[] w = TileDriver.expandWindow(512, 1024, 3072, 0);
        assertEquals(512, w[0]);
        assertEquals(1024, w[1]);
    }

    @Test
    public void inputWindowMiddleBand() {
        // 2x upscale (zoom 0.5): output [1024,2048) needs input ~[512,1024).
        int[] w = TileDriver.inputWindow(1024, 2048, 1536, 0.5f, 6);
        assertEquals(512 - 6, w[0]);
        assertEquals(1024 + 6, w[1]);
    }

    @Test
    public void inputWindowClampsTopAndBottom() {
        int[] top = TileDriver.inputWindow(0, 1024, 1536, 0.5f, 6);
        assertEquals(0, top[0]);
        assertEquals(512 + 6, top[1]);
        int[] bot = TileDriver.inputWindow(2048, 3072, 1536, 0.5f, 6);
        assertEquals(1024 - 6, bot[0]);
        assertEquals(1536, bot[1]);
    }

    @Test
    public void inputWindowUnityScale() {
        int[] w = TileDriver.inputWindow(100, 200, 1000, 1.0f, 2);
        assertEquals(98, w[0]);
        assertEquals(202, w[1]);
    }

    @Test
    public void clampInteriorXKeepsLeftBandWhole() {
        int[] keep = TileDriver.clampInteriorX(0, 1024, 4096, 2, false);
        assertEquals(0, keep[0]);
        assertEquals(1024, keep[1]);
    }

    @Test
    public void clampInteriorXDropsRightHaloCols() {
        int[] keep = TileDriver.clampInteriorX(3072, 1024, 4096, 2, false);
        assertEquals(3072, keep[0]);
        assertEquals(4094, keep[1]);
    }

    @Test
    public void clampInteriorXLeftSkipDropsFirstColOffLeft() {
        int[] keep = TileDriver.clampInteriorX(1024, 1024, 4096, 5, true);
        assertEquals(1025, keep[0]);
        assertEquals(2048, keep[1]);
        int[] left = TileDriver.clampInteriorX(0, 1024, 4096, 5, true);
        assertEquals(0, left[0]);
        assertEquals(1024, left[1]);
    }

    @Test
    public void clampInteriorXEmptyWhenBandInsideHalo() {
        int[] keep = TileDriver.clampInteriorX(4094, 2, 4096, 5, true);
        assertEquals(keep[0], keep[1]);
    }

    @Test
    public void badArgsThrow() {        try {
            TileDriver.computeBands(100, 0);
            fail("expected tileRows");
        } catch (IllegalArgumentException expected) {
        }
        try {
            TileDriver.computeBands(-1, 256);
            fail("expected height");
        } catch (IllegalArgumentException expected) {
        }
    }
}
