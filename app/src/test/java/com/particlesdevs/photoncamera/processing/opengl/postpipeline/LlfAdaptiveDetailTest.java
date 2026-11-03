package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LlfAdaptiveDetailTest {
    private static final float EPS = 1.0e-4f;

    /** Bin positions in bin units, like AutoExposureCurve's mapped[]. */
    private static float[] mapped(int bins) {
        float[] m = new float[bins];
        for (int i = 0; i < bins; i++) m[i] = i;
        return m;
    }

    private static int[][] counts3(int bins, int[][] c) {
        return c; // explicit per-channel arrays
    }

    @Test
    public void percentileTracksTheCdf() {
        int[] counts = new int[]{0, 0, 100, 0};
        float[] m = mapped(4);
        assertEquals(2f / 3f, LlfAdaptiveDetail.percentile(counts, m, 100, 0.01f), EPS);
        assertEquals(2f / 3f, LlfAdaptiveDetail.percentile(counts, m, 100, 0.99f), EPS);
        assertEquals(0f, LlfAdaptiveDetail.percentile(counts, m, 0, 0.5f), EPS);
        assertEquals(0f, LlfAdaptiveDetail.percentile(null, m, 100, 0.5f), EPS);
    }

    @Test
    public void spreadFlatSceneIsNarrow() {
        int bins = 16;
        int[] c = new int[bins];
        c[5] = 500;
        c[6] = 1000;
        c[7] = 500;
        float s = LlfAdaptiveDetail.spread(new int[][]{c, c, c}, new float[][]{mapped(bins), mapped(bins), mapped(bins)},
                bins, new int[]{2000, 2000, 2000}, 0f);
        // p2 at bin 5, p98 at bin 7 over 15 steps.
        assertEquals(2f / 15f, s, 0.02f);
    }

    @Test
    public void spreadFullRangeIsOne() {
        int bins = 16;
        int[] c = new int[bins];
        for (int i = 0; i < bins; i++) c[i] = 10;
        float s = LlfAdaptiveDetail.spread(new int[][]{c, c, c}, new float[][]{mapped(bins), mapped(bins), mapped(bins)},
                bins, new int[]{160, 160, 160}, 0f);
        assertEquals(1f, s, 0.05f);
    }

    @Test
    public void spreadUsesTheChannelCrossing() {
        int bins = 16;
        int[] wide = new int[bins];
        for (int i = 0; i < bins; i++) wide[i] = 10;
        int[] narrow = new int[bins];
        for (int i = 6; i <= 8; i++) narrow[i] = 100;
        float s = LlfAdaptiveDetail.spread(new int[][]{wide, narrow, wide},
                new float[][]{mapped(bins), mapped(bins), mapped(bins)},
                bins, new int[]{160, 300, 160}, 0f);
        // The narrow channel defines both edges.
        float expected = 2f / 15f;
        assertEquals(expected, s, 0.03f);
    }

    @Test
    public void spreadUnmeasuredWhenNoMass() {
        int bins = 8;
        float s = LlfAdaptiveDetail.spread(new int[][]{new int[bins], new int[bins], new int[bins]},
                new float[][]{mapped(bins), mapped(bins), mapped(bins)},
                bins, new int[]{0, 0, 0}, 0f);
        assertEquals(-1f, s, EPS);
    }

    @Test
    public void robustBlendMovesTheLowEdge() {
        int bins = 16;
        int[] c = new int[bins];
        for (int i = 0; i < bins; i++) c[i] = 10; // uniform -> p2 at 0, p5 at ~0.05*15
        float p2 = LlfAdaptiveDetail.spread(new int[][]{c, c, c}, new float[][]{mapped(bins), mapped(bins), mapped(bins)},
                bins, new int[]{160, 160, 160}, 0f);
        float p5 = LlfAdaptiveDetail.spread(new int[][]{c, c, c}, new float[][]{mapped(bins), mapped(bins), mapped(bins)},
                bins, new int[]{160, 160, 160}, 1f);
        assertTrue("p5 spread must be <= p2 spread", p5 <= p2 + EPS);
    }

    @Test
    public void densityCountsBinsAtOrAboveThreshold() {
        int[] counts = new int[]{10, 10, 10, 10};
        assertEquals(0.5f, LlfAdaptiveDetail.density(counts, 2), EPS);
        assertEquals(1.0f, LlfAdaptiveDetail.density(counts, 0), EPS);
        assertEquals(-1f, LlfAdaptiveDetail.density(new int[0], 0), EPS);
        assertEquals(-1f, LlfAdaptiveDetail.density(new int[]{0, 0}, 1), EPS);
    }

    @Test
    public void flatCleanSceneGetsTheMax() {
        LlfAdaptiveDetail.Tuning t = new LlfAdaptiveDetail.Tuning();
        LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
        s.spread = 0.2f;
        s.spreadRobust = 0.2f;
        s.noise = 0.001f;
        s.clipped = 0f;
        s.density = 0f;
        assertEquals(t.max, LlfAdaptiveDetail.effectiveDetail(t, s), EPS);
    }

    @Test
    public void fullRangeSceneGetsTheFloor() {
        LlfAdaptiveDetail.Tuning t = new LlfAdaptiveDetail.Tuning();
        LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
        s.spread = 0.8f;
        s.spreadRobust = 0.8f;
        assertEquals(t.floor, LlfAdaptiveDetail.effectiveDetail(t, s), EPS);
    }

    @Test
    public void unmeasuredSpreadFallsBackToFloor() {
        LlfAdaptiveDetail.Tuning t = new LlfAdaptiveDetail.Tuning();
        LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
        assertEquals(t.floor, LlfAdaptiveDetail.effectiveDetail(t, s), EPS);
        s.spread = Float.NaN;
        assertEquals(t.floor, LlfAdaptiveDetail.effectiveDetail(t, s), EPS);
    }

    @Test
    public void noiseAttenuatesTheFlatBoost() {
        LlfAdaptiveDetail.Tuning t = new LlfAdaptiveDetail.Tuning();
        LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
        s.spread = 0.2f;
        s.spreadRobust = 0.2f;
        s.noise = 0.05f; // above noiseNoisy -> full attenuation
        float expected = t.floor + (t.max - t.floor) * t.noiseBoostMin;
        assertEquals(expected, LlfAdaptiveDetail.effectiveDetail(t, s), EPS);
    }

    @Test
    public void clippingAttenuatesTheBoost() {
        LlfAdaptiveDetail.Tuning t = new LlfAdaptiveDetail.Tuning();
        LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
        s.spread = 0.2f;
        s.spreadRobust = 0.2f;
        s.clipped = 0.1f; // >= clipRef -> half the boost with the default tuning
        float expected = t.floor + (t.max - t.floor) * (1f - t.clipBoostAtten);
        assertEquals(expected, LlfAdaptiveDetail.effectiveDetail(t, s), EPS);
    }

    @Test
    public void detailDensityAttenuatesTheBoost() {
        LlfAdaptiveDetail.Tuning t = new LlfAdaptiveDetail.Tuning();
        LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
        s.spread = 0.2f;
        s.spreadRobust = 0.2f;
        s.density = 0.5f; // >= densityHigh
        float expected = t.floor + (t.max - t.floor) * t.densityBoostMin;
        assertEquals(expected, LlfAdaptiveDetail.effectiveDetail(t, s), EPS);
    }

    @Test
    public void policyIsMonotoneAndBounded() {
        LlfAdaptiveDetail.Tuning t = new LlfAdaptiveDetail.Tuning();
        float prev = Float.MAX_VALUE;
        for (float spread = 0f; spread <= 1.0001f; spread += 0.05f) {
            LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
            s.spread = spread;
            s.spreadRobust = spread;
            float v = LlfAdaptiveDetail.effectiveDetail(t, s);
            assertTrue("spread " + spread + " raised the detail", v <= prev + EPS);
            assertTrue(v >= t.floor - EPS && v <= t.max + EPS);
            prev = v;
        }
        prev = Float.MAX_VALUE;
        for (float noise = 0f; noise <= 0.1001f; noise += 0.005f) {
            LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
            s.spread = 0.1f;
            s.spreadRobust = 0.1f;
            s.noise = noise;
            float v = LlfAdaptiveDetail.effectiveDetail(t, s);
            assertTrue("noise " + noise + " raised the detail", v <= prev + EPS);
            prev = v;
        }
        prev = Float.MAX_VALUE;
        for (float clip = 0f; clip <= 1.0001f; clip += 0.05f) {
            LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
            s.spread = 0.1f;
            s.spreadRobust = 0.1f;
            s.clipped = clip;
            float v = LlfAdaptiveDetail.effectiveDetail(t, s);
            assertTrue("clip " + clip + " raised the detail", v <= prev + EPS);
            prev = v;
        }
        prev = Float.MAX_VALUE;
        for (float density = 0f; density <= 1.0001f; density += 0.05f) {
            LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
            s.spread = 0.1f;
            s.spreadRobust = 0.1f;
            s.density = density;
            float v = LlfAdaptiveDetail.effectiveDetail(t, s);
            assertTrue("density " + density + " raised the detail", v <= prev + EPS);
            prev = v;
        }
    }

    @Test
    public void invertedTunablesStayBounded() {
        LlfAdaptiveDetail.Tuning t = new LlfAdaptiveDetail.Tuning();
        t.floor = 0.5f;
        t.max = 0.2f; // inverted -> treated as floor == max
        LlfAdaptiveDetail.Scene s = new LlfAdaptiveDetail.Scene();
        s.spread = 0.1f;
        s.spreadRobust = 0.1f;
        assertEquals(0.5f, LlfAdaptiveDetail.effectiveDetail(t, s), EPS);
        t.floor = 0f;
        t.max = 1.5f;
        t.spreadFlat = 0.7f;
        t.spreadFull = 0.3f; // inverted ramp -> hard step, still monotone/bounded
        LlfAdaptiveDetail.Scene flat = new LlfAdaptiveDetail.Scene();
        flat.spread = 0.2f;
        flat.spreadRobust = 0.2f;
        assertTrue(LlfAdaptiveDetail.effectiveDetail(t, flat) >= 0f
                && LlfAdaptiveDetail.effectiveDetail(t, flat) <= 1.5f + EPS);
    }

    @Test
    public void smoothstepIsSafeForDegenerateRanges() {
        assertEquals(0f, LlfAdaptiveDetail.smoothstep(0.5f, 0.5f, 0.25f), EPS);
        assertEquals(1f, LlfAdaptiveDetail.smoothstep(0.5f, 0.5f, 0.5f), EPS);
        assertEquals(0f, LlfAdaptiveDetail.smoothstep(0f, 1f, -1f), EPS);
        assertEquals(1f, LlfAdaptiveDetail.smoothstep(0f, 1f, 2f), EPS);
        assertEquals(0.5f, LlfAdaptiveDetail.smoothstep(0f, 1f, 0.5f), EPS);
    }
}
