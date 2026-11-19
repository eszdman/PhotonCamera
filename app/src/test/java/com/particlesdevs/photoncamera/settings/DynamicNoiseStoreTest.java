package com.particlesdevs.photoncamera.settings;

import org.junit.Rule;
import org.junit.Test;
import org.junit.After;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.io.FileWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DynamicNoiseStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private DynamicNoiseStore newStore() throws Exception {
        return new DynamicNoiseStore(temporary.newFolder());
    }

    private static void addReferences(DynamicNoiseStore store, boolean binned) {
        // Known gain model, including a real ISO-independent read-noise floor.
        for (int iso : new int[]{400, 800, 1600}) {
            for (int scene = 1; scene <= 3; scene++) {
                double scale = binned ? 0.25 : 1;
                store.commitAndGet(2, iso, 3200, 8e-7 * iso * scale,
                        (2e-12 * iso * iso + 2e-7) * scale, scene * .01, binned);
            }
        }
    }

    @Test public void trimmedMeanRejectsTinySlopeWithHugeReadOffset() {
        DynamicNoiseStore.IsoBin bin = new DynamicNoiseStore.IsoBin();
        for (int i = 0; i < 5; i++) bin.add(5e-5, 1e-7);
        for (int i = 0; i < 5; i++) bin.add(1e-10, 6e-5);
        assertEquals(5e-5, bin.average().s, 1e-15);
        assertEquals(1e-7, bin.average().o, 1e-15);
    }

    @Test public void duplicateDoesNotReplaceGoodModelWithLowerSlopeAndHugeOffset() {
        DynamicNoiseStore.IsoBin bin = new DynamicNoiseStore.IsoBin();
        bin.add(5e-5, 1e-7, 11);
        bin.replaceMin(11, 1e-10, 6e-5);
        assertEquals(5e-5, bin.average().s, 1e-15);
    }

    @Test public void normalizeToExactIsoWithinStopBucket() throws Exception {
        DynamicNoiseStore store = newStore();
        store.commitAndGet(2, 100, 3200, 1e-4, 1e-6, .01);
        DynamicNoiseStore.NoiseEstimate model = store.getEstimate(2, 140, 3200, false);
        assertEquals(1.4e-4, model.s, 1e-15);
        assertEquals(1.96e-6, model.o, 1e-15);
        assertFalse(model.higherIsoReference);
    }

    @Test public void higherIsoProjectionFixesBothLowIsoTextureAndClampedSlope() throws Exception {
        DynamicNoiseStore store = newStore();
        store.commitAndGet(2, 50, 3200, 1e-10, 6e-5, .01);
        addReferences(store, false);
        DynamicNoiseStore.NoiseEstimate model = store.getEstimate(2, 50, 3200, false);
        assertTrue(model.higherIsoReference);
        assertEquals(4e-5, model.s, 1e-14);
        assertEquals(2.05e-7, model.o, 1e-14);
        // A new contaminated fit must remain bounded immediately, too.
        model = store.commitAndGet(2, 50, 3200, .002, .001, .02);
        assertEquals(4e-5, model.s, 1e-14);
        assertEquals(2.05e-7, model.o, 1e-14);
    }

    @Test public void noProjectionUntilThreeDistinctScenesHaveBeenMeasured() throws Exception {
        DynamicNoiseStore store = newStore();
        store.commitAndGet(2, 50, 3200, 4e-5, 1e-7, .01);
        for (int i = 0; i < 3; i++)
            store.commitAndGet(2, 800, 3200, 6.4e-4, 1e-6, .01);
        assertFalse(store.getEstimate(2, 50, 3200, false).higherIsoReference);
    }

    @Test public void higherIsoHistoryIsUsableBeforeAnyLowIsoFit() throws Exception {
        DynamicNoiseStore store = newStore();
        addReferences(store, false);
        DynamicNoiseStore.NoiseEstimate model = store.getEstimate(2, 50, 3200, false);
        assertNotNull(model);
        assertTrue(model.higherIsoReference);
        assertEquals(4e-5, model.s, 1e-14);
        assertEquals(2.05e-7, model.o, 1e-14);
    }

    @Test public void digitalGainDoesNotCreateAnArtificialAnalogueReference() throws Exception {
        DynamicNoiseStore store = newStore();
        store.commitAndGet(2, 50, 400, 1e-10, 6e-5, .01);
        for (int iso : new int[]{400, 800, 1600}) {
            for (int scene = 1; scene <= 3; scene++) {
                store.commitAndGet(2, iso, 400, 8e-7 * iso, 2e-12 * iso * iso, scene * .01);
            }
        }
        DynamicNoiseStore.NoiseEstimate model = store.getEstimate(2, 50, 400, false);
        assertTrue(model.higherIsoReference);
        assertEquals(4e-5, model.s, 1e-14);
        assertEquals(5e-9, model.o, 1e-14);
        // All gain above ISO400 is digital: there is no 4x higher analogue
        // calibration for a target ISO200.
        assertNull(store.getEstimate(2, 200, 400, false));
    }

    @Test public void texturedAnchorCannotRaiseReadFloorAboveQuietestAnchor() throws Exception {
        DynamicNoiseStore store = newStore();
        store.commitAndGet(2, 50, 3200, 1e-10, 6e-5, .01);
        for (int iso : new int[]{400, 800, 1600}) {
            for (int scene = 1; scene <= 3; scene++) {
                double o = iso == 800 ? .001 : 2e-12 * iso * iso + 2e-7;
                store.commitAndGet(2, iso, 3200, 8e-7 * iso, o, scene * .01);
            }
        }
        DynamicNoiseStore.NoiseEstimate model = store.getEstimate(2, 50, 3200, false);
        assertTrue(model.higherIsoReference);
        assertTrue(model.o <= 2e-12 * 400 * 400 + 2e-7);
    }

    @Test public void cleanLowerIsoModelIsRetained() throws Exception {
        DynamicNoiseStore store = newStore();
        store.commitAndGet(2, 50, 3200, 3e-5, 1e-7, .01);
        addReferences(store, false);
        DynamicNoiseStore.NoiseEstimate model = store.getEstimate(2, 50, 3200, false);
        assertEquals(3e-5, model.s, 1e-15);
        assertEquals(1e-7, model.o, 1e-15);
        assertTrue(model.higherIsoReference);
    }

    @Test public void binnedAndFullResolutionHistoriesNeverMix() throws Exception {
        DynamicNoiseStore store = newStore();
        store.commitAndGet(2, 100, 3200, 1e-4, 1e-6, .01, false);
        store.commitAndGet(2, 100, 3200, 2.5e-5, 2.5e-7, .01, true);
        assertEquals(1e-4, store.getEstimate(2, 100, 3200, false).s, 1e-15);
        assertEquals(2.5e-5, store.getEstimate(2, 100, 3200, true).s, 1e-15);
        addReferences(store, true);
        assertFalse(store.getEstimate(2, 100, 3200, false).higherIsoReference);
        assertTrue(store.getEstimate(2, 100, 3200, true).higherIsoReference);
    }

    @Test public void invalidMeasurementsDoNotPoisonHistory() throws Exception {
        DynamicNoiseStore store = newStore();
        assertNull(store.commitAndGet(2, 0, 3200, 1e-4, 1e-6, .01));
        assertNull(store.commitAndGet(2, 100, 3200, Double.NaN, 1e-6, .01));
        store.commitAndGet(2, 100, 3200, 1e-4, 1e-6, .01);
        assertEquals(1e-4, store.commitAndGet(2, 100, 3200, 1, Double.POSITIVE_INFINITY, .02).s, 1e-15);
        assertEquals(1, store.getBin(2, 100).count());
    }

    @Test public void rebinningDoesNotChangeExactIsoModel() throws Exception {
        DynamicNoiseStore store = newStore();
        store.commitAndGet(2, 200, 3200, 2e-4, 4e-6, .01);
        store.commitAndGet(2, 50, 3200, 5e-5, 2.5e-7, .02);
        assertEquals(2e-4, store.getEstimate(2, 200, 3200, false).s, 1e-15);
        assertEquals(4e-6, store.getEstimate(2, 200, 3200, false).o, 1e-15);
    }

    @Test public void persistenceRetainsSamplingModeAndModels() throws Exception {
        File root = temporary.newFolder();
        DynamicNoiseStore store = new DynamicNoiseStore(root);
        store.commitAndGet(2, 100, 3200, 1e-4, 1e-6, .01, false);
        store.commitAndGet(2, 100, 3200, 2.5e-5, 2.5e-7, .01, true);
        awaitWrites();
        assertTrue(new File(root, "2.json").isFile());
        assertTrue(new File(root, "2_binned.json").isFile());
        DynamicNoiseStore reloaded = new DynamicNoiseStore(root);
        assertEquals(1e-4, reloaded.getEstimate(2, 100, 3200, false).s, 1e-15);
        assertEquals(2.5e-5, reloaded.getEstimate(2, 100, 3200, true).s, 1e-15);
    }

    @Test public void oldUnmarkedSamplingModesAreNotReused() throws Exception {
        File root = temporary.newFolder();
        try (FileWriter writer = new FileWriter(new File(root, "2.json"))) {
            writer.write("{\"version\":5,\"physicalID\":2,\"minIso\":50,\"samples\":["
                    + "{\"iso\":50,\"s\":1e-10,\"o\":6e-5,\"key\":123}]}");
        }
        DynamicNoiseStore store = new DynamicNoiseStore(root);
        assertNull(store.getEstimate(2, 50, 3200, false));
        assertNull(store.getEstimate(2, 50, 3200, true));
    }

    @Test public void resetClearsQueuedSavesAllModesAndCooldown() throws Exception {
        File root = temporary.newFolder();
        DynamicNoiseStore store = new DynamicNoiseStore(root);
        for (int scene = 1; scene <= 20; scene++) {
            store.commitAndGet(2, 100, 3200, 1e-4, 1e-6, scene * .01, false);
            store.commitAndGet(2, 100, 3200, 2.5e-5, 2.5e-7, scene * .01, true);
            store.commitAndGet(3, 200, 3200, 2e-4, 4e-6, scene * .01, false);
        }
        File backup = new File(root, "manual-backup.json");
        try (FileWriter writer = new FileWriter(backup)) { writer.write("keep"); }
        File pending = new File(root, "4_binned.json.tmp");
        try (FileWriter writer = new FileWriter(pending)) { writer.write("stale"); }
        store.resetAll();
        awaitWrites();
        assertNull(store.getEstimate(2, 100, 3200, false));
        assertNull(store.getEstimate(2, 100, 3200, true));
        assertNull(store.getEstimate(3, 200, 3200, false));
        assertFalse(pending.exists());
        assertEquals(1, root.listFiles().length);
        assertTrue(backup.exists());
        DynamicNoiseStore reloaded = new DynamicNoiseStore(root);
        assertNull(reloaded.getEstimate(2, 100, 3200, false));
        assertNull(reloaded.getEstimate(2, 100, 3200, true));
        // The most recent scene must be eligible again after resetting.
        assertNotNull(store.commitAndGet(2, 100, 3200, 8e-5, 8e-7, .20, false));
        assertEquals(1, store.getBin(2, 100).count());
        assertEquals(8e-5, store.getEstimate(2, 100, 3200, false).s, 1e-15);
    }

    @Test public void resetAllowsSameProcessToLoadRestoredModel() throws Exception {
        File root = temporary.newFolder();
        DynamicNoiseStore store = new DynamicNoiseStore(root);
        store.commitAndGet(2, 100, 3200, 1e-4, 1e-6, .01);
        awaitWrites();
        File model = new File(root, "2.json");
        byte[] backup = java.nio.file.Files.readAllBytes(model.toPath());
        store.resetAll();
        java.nio.file.Files.write(model.toPath(), backup);
        assertEquals(1e-4, store.getEstimate(2, 100, 3200, false).s, 1e-15);
    }

    private static void awaitWrites() throws Exception {
        Field field = DynamicNoiseStore.class.getDeclaredField("ioExecutor");
        field.setAccessible(true);
        ((ExecutorService) field.get(null)).submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    @After public void finishPersistence() throws Exception {
        awaitWrites();
    }
}
