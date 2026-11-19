package com.particlesdevs.photoncamera.util;

import org.junit.Test;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import static org.junit.Assert.*;

public class BurstHotPixelFilterTest {
    private static final int W = 48, H = 48;
    private static class Frame implements BurstHotPixelFilter.Frame {
        final float[] data = new float[W * H];
        @Override public float get(int index) { return data[index]; }
        @Override public void set(int index, float value) { data[index] = value; }
    }
    private Frame[] flat(int count, float value) {
        Frame[] result = new Frame[count];
        for (int f = 0; f < count; ++f) { result[f] = new Frame(); Arrays.fill(result[f].data, value); }
        return result;
    }
    private BurstHotPixelFilter.Result correct(Frame[] frames, int cfa, int... sites) {
        return BurstHotPixelFilter.correct(frames, W, H, cfa, sites, .0004f, .000001f, 6f, 2000);
    }
    private void unchanged(Frame[] frames, int cfa, int... sites) {
        float[][] before = Arrays.stream(frames).map(f -> f.data.clone()).toArray(float[][]::new);
        assertEquals(0, correct(frames, cfa, sites).sites.length);
        for (int f = 0; f < frames.length; ++f) assertArrayEquals(before[f], frames[f].data, 0f);
    }

    @Test public void allCfaOriginsRepairOnlyTheDefectAndKeepHealthyColorSitesExact() {
        for (int cfa = 0; cfa < 4; ++cfa) {
            Frame[] frames = flat(22, 0f);
            int site = 24 * W + 24;
            float expected = 0;
            for (Frame f : frames) {
                for (int y = 0; y < H; ++y) for (int x = 0; x < W; ++x) {
                    int phase = 2 * (y % 2) + x % 2;
                    f.data[y * W + x] = phase == cfa ? .1f : (phase == 3 - cfa ? .3f : .2f);
                }
                expected = f.data[site]; f.data[site] = 1f;
            }
            float[] before = frames[0].data.clone();
            BurstHotPixelFilter.Result result = correct(frames, cfa, site, site);
            assertArrayEquals(new int[]{site}, result.sites);
            for (Frame f : frames) for (int i = 0; i < before.length; ++i)
                assertEquals(i == site ? expected : before[i], f.data[i], 0f);
            for (int count : result.correctedPerFrame) assertEquals(1, count);
        }
    }

    @Test public void adjacentGreenDefectsExcludeEachOtherAndAreOrderIndependent() {
        int a = 24 * W + 25, b = 25 * W + 24;
        for (int[] order : new int[][]{{a,b},{b,a}}) {
            Frame[] frames = flat(12, .2f);
            for (Frame f : frames) { f.data[a] = 1; f.data[b] = 1; }
            assertEquals(2, correct(frames, 0, order).sites.length);
            for (Frame f : frames) { assertEquals(.2f, f.data[a], 0f); assertEquals(.2f, f.data[b], 0f); }
        }
    }

    @Test public void movingPointsAndChangingPeakAmplitudesAreNotSensorDefects() {
        Frame[] moving = flat(10, .1f), changing = flat(10, .1f);
        int center = 24 * W + 16;
        for (int f = 0; f < 10; ++f) {
            moving[f].data[center + 2 * f] = 1;
            changing[f].data[center] = .4f + .05f * f;
        }
        unchanged(moving, 0, center);
        unchanged(changing, 0, center);
    }

    @Test public void clippedEdgesAndOpticalPointHighlightsRemainUntouched() {
        Frame[] frames = flat(10, .1f);
        for (Frame f : frames) for (int y = 0; y < H; ++y) for (int x = 0; x < W; ++x) {
            double spot = .1 + 1.2 * Math.exp(-((x - 15.) * (x - 15.) + (y - 24.) * (y - 24.)) / 2);
            f.data[y * W + x] = x > 32 ? 1f : (float) Math.min(1, spot);
        }
        int[] sites = new int[W * H]; for (int i = 0; i < sites.length; ++i) sites[i] = i;
        unchanged(frames, 0, sites);
    }

    @Test public void fineTextureAndIndependentNoiseAreNotMedianFiltered() {
        int[] sites = new int[W * H]; for (int i = 0; i < sites.length; ++i) sites[i] = i;
        Frame[] texture = flat(12, .1f), noise = flat(12, .1f);
        Random rng = new Random(7021);
        for (int f = 0; f < texture.length; ++f) for (int y = 0; y < H; ++y) for (int x = 0; x < W; ++x) {
            texture[f].data[y * W + x] = (float) (.4 + .2 * Math.sin(1.7 * x) * Math.cos(1.9 * y));
            noise[f].data[y * W + x] += (float) rng.nextGaussian() * .012f;
        }
        unchanged(texture, 0, sites);
        unchanged(noise, 0, sites);
    }

    @Test public void perFrameGuardPreservesUnsampledHighlightOverDefect() {
        Frame[] frames = flat(22, .1f); int site = 24 * W + 24;
        for (Frame f : frames) f.data[site] = 1;
        // Frame one is outside the ten evenly spaced verification frames.
        frames[1].data[site - 2] = 1;
        BurstHotPixelFilter.Result result = correct(frames, 0, site);
        assertEquals(1, result.sites.length);
        assertEquals(0, result.correctedPerFrame[1]);
        assertEquals(1f, frames[1].data[site], 0f);
        assertEquals(.1f, frames[2].data[site], 0f);
    }

    @Test public void insufficientFramesBordersInvalidNoiseAndOverfullListsFailClosed() {
        Frame[] frames = flat(2, .1f); int site = 24 * W + 24;
        for (Frame f : frames) f.data[site] = 1;
        unchanged(frames, 0, site);
        frames = flat(3, .1f);
        for (Frame f : frames) { f.data[site] = 1; f.data[0] = 1; }
        unchanged(frames, 0, -1, 0, W * H, Integer.MAX_VALUE);
        assertEquals(0, BurstHotPixelFilter.correct(frames,W,H,0,new int[]{site},Float.NaN,0,6,2000).sites.length);
        BurstHotPixelFilter.Result limited = BurstHotPixelFilter.correct(frames,W,H,0,new int[]{site},0,0,6,0);
        assertTrue(limited.rejectedOverfull);
        assertEquals(1f, frames[0].data[site], 0f);
    }

    @Test public void reportedNoisyWarmPixelsAndNeighboringPairAreRepaired() {
        // Six 9x9 sensor patches from dark burst 130552, at the reported upright
        // coordinates and nearby defects; ten evenly spaced frames, black 64,
        // white 1023. The old detector rejected every case. No full images used.
        try (Scanner input = new Scanner(getClass().getResourceAsStream("/burst-hot-pixels.txt"))) {
            int cases = input.nextInt(), count = input.nextInt(), size = input.nextInt();
            int site = 24 * W + 24;
            for (int c = 0; c < cases; ++c) {
                String location = input.nextInt() + "," + input.nextInt();
                int cfa = input.nextInt();
                Frame[] frames = flat(count, 0);
                for (Frame frame : frames)
                    for (int y = 0; y < size; ++y) for (int x = 0; x < size; ++x)
                        frame.data[(24 + y - size / 2) * W + 24 + x - size / 2] =
                                (input.nextInt() - 64) / 959f;
                float[][] before = Arrays.stream(frames).map(f -> f.data.clone()).toArray(float[][]::new);
                BurstHotPixelFilter.Result result = BurstHotPixelFilter.correct(frames, W, H, cfa,
                        new int[]{site}, .00254347455f, .0000468228973f, 6f, 2000);
                assertArrayEquals(location, new int[]{site}, result.sites);
                for (int f = 0; f < frames.length; ++f) {
                    assertEquals(location, 1, result.correctedPerFrame[f]);
                    assertTrue(location, frames[f].data[site] < before[f][site] - .04f);
                    for (int i = 0; i < W * H; ++i)
                        if (i != site) assertEquals(location, before[f][i], frames[f].data[i], 0f);
                }
            }
        }
    }

    @Test public void spatiallySupportedOpticalSpotsSurviveTemporalMedianAndNeighborTrim() {
        int[] sites = new int[W * H]; for (int i = 0; i < sites.length; ++i) sites[i] = i;
        for (int cfa = 0; cfa < 4; ++cfa)
            for (double radius : new double[]{.7, 1.0, 1.5})
                for (double phase : new double[]{0, .5}) {
                    Frame[] frames = flat(10, 0);
                    for (Frame f : frames) for (int y = 0; y < H; ++y) for (int x = 0; x < W; ++x) {
                        int channel = (x & 1) + 2 * (y & 1);
                        double gain = channel == cfa ? .7 : channel == 3 - cfa ? .4 : 1;
                        double dx = x - 24 - phase, dy = y - 24 - phase;
                        f.data[y * W + x] = (float) Math.min(1,
                                .03 + gain * Math.exp(-(dx * dx + dy * dy) / (2 * radius * radius)));
                    }
                    unchanged(frames, cfa, sites);
                }
    }

    @Test public void occasionalStrongNoiseWithoutMajoritySupportIsNotRepaired() {
        Frame[] frames = flat(10, .03f); int site = 24 * W + 24;
        // The first-frame proposal passes, but only half the samples have strong
        // evidence. All frames have weak evidence, so this exercises majority.
        for (int f = 0; f < frames.length; ++f) frames[f].data[site] = f < 5 ? .07f : .051f;
        unchanged(frames, 0, site);
    }

    @Test public void reportedWarmPixelsTrackBackgroundRatherThanAbsoluteSignal() {
        // Actual FP16-normalized 9x9 patches from all 25 tester frames, at raw
        // (2794,2099) and (2936,2063). The old absolute-intensity stability and
        // maximum-neighbor significance checks rejected these visible defects.
        try (Scanner input = new Scanner(getClass().getResourceAsStream("/burst-hot-pixels-local-excess.txt"))) {
            input.useLocale(java.util.Locale.ROOT);
            int cases = input.nextInt(), count = input.nextInt(), size = input.nextInt();
            int site = 24 * W + 24;
            for (int c = 0; c < cases; ++c) {
                String location = input.nextInt() + "," + input.nextInt();
                int cfa = input.nextInt();
                Frame[] frames = flat(count, 0);
                for (Frame frame : frames)
                    for (int y = 0; y < size; ++y) for (int x = 0; x < size; ++x)
                        frame.data[(24 + y - size / 2) * W + 24 + x - size / 2] = input.nextFloat();
                float[][] before = Arrays.stream(frames).map(f -> f.data.clone()).toArray(float[][]::new);
                BurstHotPixelFilter.Result result = BurstHotPixelFilter.correct(frames, W, H, cfa,
                        new int[]{site}, .000570853125f, .00000317882539f, 6f, 2000);
                assertArrayEquals(location, new int[]{site}, result.sites);
                for (int f = 0; f < frames.length; ++f) {
                    assertEquals(location, 1, result.correctedPerFrame[f]);
                    assertTrue(location, frames[f].data[site] < before[f][site] - .02f);
                    for (int i = 0; i < W * H; ++i)
                        if (i != site) assertEquals(location, before[f][i], frames[f].data[i], 0f);
                }
            }
        }
    }

    @Test public void changingBackgroundKeepsOpticalSpotsAndMovingEdgesIntact() {
        Frame[] frames = flat(10, 0);
        int[] sites = new int[W * H]; for (int i = 0; i < sites.length; ++i) sites[i] = i;
        for (int f = 0; f < frames.length; ++f)
            for (int y = 0; y < H; ++y) for (int x = 0; x < W; ++x) {
                double background = .03 + .02 * f;
                double spot = .3 * Math.exp(-((x - 16.) * (x - 16.) + (y - 24.) * (y - 24.)) / 2);
                frames[f].data[y * W + x] = (float) (background + spot + (x >= 33 + f / 3 ? .4 : 0));
            }
        unchanged(frames, 0, sites);
    }

    @Test public void reportedDefectsSurviveOccasionalEdgesAndLocalTextureChanges() {
        // Six newly reported defects, 25 original FP16-normalized tester frames.
        // Some frames contain passing edges: keep the per-frame highlight guard,
        // while ensuring that these no longer veto the entire sensor site.
        try (Scanner input = new Scanner(getClass().getResourceAsStream("/burst-hot-pixels-moving-background.txt"))) {
            input.useLocale(java.util.Locale.ROOT);
            int cases = input.nextInt(), count = input.nextInt(), size = input.nextInt();
            int site = 24 * W + 24;
            for (int c = 0; c < cases; ++c) {
                String location = input.nextInt() + "," + input.nextInt();
                int cfa = input.nextInt();
                Frame[] frames = flat(count, 0);
                for (Frame frame : frames)
                    for (int y = 0; y < size; ++y) for (int x = 0; x < size; ++x)
                        frame.data[(24 + y - size / 2) * W + 24 + x - size / 2] = input.nextFloat();
                float[][] before = Arrays.stream(frames).map(f -> f.data.clone()).toArray(float[][]::new);
                BurstHotPixelFilter.Result result = BurstHotPixelFilter.correct(frames, W, H, cfa,
                        new int[]{site}, .000570853125f, .00000317882539f, 6f, 2000);
                assertArrayEquals(location, new int[]{site}, result.sites);
                assertTrue(location, frames[0].data[site] < before[0][site] - .02f);
                assertTrue(location, Arrays.stream(result.correctedPerFrame).sum() >= 20);
                for (int f = 0; f < frames.length; ++f)
                    for (int i = 0; i < W * H; ++i)
                        if (i != site) assertEquals(location, before[f][i], frames[f].data[i], 0f);
            }
        }
    }

    @Test public void moreThanTwoUnsupportedSamplesStillRejectTheSite() {
        Frame[] frames = flat(10, .03f); int site = 24 * W + 24;
        for (Frame f : frames) f.data[site] = .15f;
        for (int f = 0; f < 3; ++f) frames[f].data[site - 2] = .3f;
        unchanged(frames, 0, site);
    }

    @Test public void knownDefectIsRepairedBelowProposalFloorButOverlaidHighlightIsPreserved() {
        Frame[] frames = flat(22, .005f); int site = 24 * W + 24;
        for (Frame f : frames) f.data[site] = .05f;
        frames[1].data[site] = .02f; // Unsampled frame, excess < 0.02 but > 3 sigma.
        frames[3].data[site - 2] = .1f; // Another unsampled frame with scene support.
        BurstHotPixelFilter.Result result = correct(frames, 0, site);
        assertArrayEquals(new int[]{site}, result.sites);
        assertEquals(.005f, frames[1].data[site], 0f);
        assertEquals(.05f, frames[3].data[site], 0f);
    }
}
