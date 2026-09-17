package com.particlesdevs.photoncamera.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * IsoBin aggregation policy: the lower-half trimmed mean. Per-capture noise
 * estimates are right-skewed (texture leaks positively), so the store's
 * blended value must track the clean-capture floor without latching onto a
 * single low outlier.
 */
public class DynamicNoiseStoreIsoBinTest {

    @Test
    public void average_prefersLowerHalfAgainstTextureOutliers() {
        DynamicNoiseStore.IsoBin bin = new DynamicNoiseStore.IsoBin();
        // 5 clean captures and 5 textured captures (leak only adds variance):
        // a plain mean would read the midpoint, the trimmed mean reads the
        // clean set.
        for (int i = 0; i < 5; i++) bin.add(0.005, 1.0e-5);
        for (int i = 0; i < 5; i++) bin.add(0.007, 1.4e-5);
        DynamicNoiseStore.NoiseEstimate avg = bin.average();
        assertEquals(0.005, avg.s, 1e-9);
        assertEquals(1.0e-5, avg.o, 1e-12);
    }

    @Test
    public void average_singleLowOutlierDoesNotLatch() {
        DynamicNoiseStore.IsoBin bin = new DynamicNoiseStore.IsoBin();
        bin.add(0.003, 1.0e-5); // one unlucky capture (gate over-rejection)
        for (int i = 0; i < 9; i++) bin.add(0.005, 1.0e-5);
        DynamicNoiseStore.NoiseEstimate avg = bin.average();
        // lowest 5 of 10 = 0.003 + 4x0.005, not the pure minimum 0.003
        assertEquals(0.0046, avg.s, 1e-9);
    }

    @Test
    public void average_emptyWindow() {
        assertNull(new DynamicNoiseStore.IsoBin().average());
    }

    @Test
    public void average_singleSample() {
        DynamicNoiseStore.IsoBin bin = new DynamicNoiseStore.IsoBin();
        bin.add(0.0042, 2.0e-5);
        DynamicNoiseStore.NoiseEstimate avg = bin.average();
        assertEquals(0.0042, avg.s, 1e-12);
        assertEquals(2.0e-5, avg.o, 1e-15);
    }

    @Test
    public void replaceMin_lowersInPlaceWithoutEvictingOthers() {
        DynamicNoiseStore.IsoBin bin = new DynamicNoiseStore.IsoBin();
        bin.add(0.005, 1.0e-5, 111L);   // scene A, textured first shot
        bin.add(0.006, 1.2e-5, 222L);   // scene B
        bin.add(0.007, 1.4e-5, 333L);   // scene C
        int sizeBefore = bin.count();
        // Re-shot of scene A came out cleaner: replace in place with the pair.
        bin.replaceMin(111L, 0.004, 0.9e-5);
        assertEquals(sizeBefore, bin.count());
        DynamicNoiseStore.NoiseEstimate avg = bin.average();
        // lower half (2 of 3) = 0.004 + 0.006
        assertEquals((0.004 + 0.006) / 2, avg.s, 1e-9);
    }

    @Test
    public void replaceMin_higherEstimateKeepsOld() {
        DynamicNoiseStore.IsoBin bin = new DynamicNoiseStore.IsoBin();
        bin.add(0.005, 1.0e-5, 111L);
        bin.replaceMin(111L, 0.008, 1.5e-5); // noisier re-shot: ignored
        assertEquals(0.005, bin.average().s, 1e-12);
    }

    @Test
    public void replaceMin_unknownKeyIsNoOp() {
        DynamicNoiseStore.IsoBin bin = new DynamicNoiseStore.IsoBin();
        bin.add(0.005, 1.0e-5, 111L);
        bin.replaceMin(999L, 0.001, 0.1e-5);
        assertEquals(0.005, bin.average().s, 1e-12);
        assertEquals(1, bin.count());
    }
}
