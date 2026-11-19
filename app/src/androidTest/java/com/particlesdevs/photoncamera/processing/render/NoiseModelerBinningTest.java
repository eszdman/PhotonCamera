package com.particlesdevs.photoncamera.processing.render;

import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class NoiseModelerBinningTest {
    @Test
    public void binnedProfile_scalesPerChannelBeforeStacking() {
        Pair<Double, Double>[] profile = new Pair[] {
                new Pair<>(0.004, 0.000008),
                new Pair<>(0.006, 0.000012),
                new Pair<>(0.008, 0.000016),
                new Pair<>(0.012, 0.000024)};
        NoiseModeler full = new NoiseModeler(profile, 1600, 100, 0, null);
        NoiseModeler binned = new NoiseModeler(profile, 1600, 100, 0, null, true);
        full.computeStackingNoiseModel(8);
        binned.computeStackingNoiseModel(8);
        for (int c = 0; c < 3; c++) {
            assertEquals(full.baseModel[c].first / 4.0, binned.baseModel[c].first, 1e-15);
            assertEquals(full.baseModel[c].second / 4.0, binned.baseModel[c].second, 1e-15);
            assertEquals(full.computeModel[c].first / 4.0, binned.computeModel[c].first, 1e-15);
            assertEquals(full.computeModel[c].second / 4.0, binned.computeModel[c].second, 1e-15);
        }
        assertEquals(0.004, profile[0].first, 1e-15);
        assertEquals(0.000008, profile[0].second, 1e-15);
    }

    @Test
    public void binnedFallback_scalesGeneratedModelAndAnalyticFloors() {
        NoiseModeler full = new NoiseModeler(null, 1600, 100, 0, null);
        NoiseModeler binned = new NoiseModeler(null, 1600, 100, 0, null, true);
        assertEquals(full.baseModel[0].first / 4.0, binned.baseModel[0].first, 1e-15);
        assertEquals(full.baseModel[0].second / 4.0, binned.baseModel[0].second, 1e-15);
        assertEquals(full.SPlace(100) / 4.0, binned.SPlace(100), 1e-15);
        assertEquals(full.OPlace(100) / 4.0, binned.OPlace(100), 1e-15);
    }
}
