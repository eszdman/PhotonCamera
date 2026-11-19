package com.particlesdevs.photoncamera.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.Log;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Multisample persistent store for the dynamic noise model.
 * Keyed by camera/sensor and software-binning mode, then ISO bin (log2 stops).
 * A measurement list per profile prevents duplicate scenes (same
 * exposure/iso) from being committed more than once, avoiding bias.
 *
 * Binning is relative to the minimum ISO observed for each physicalID
 * (any value the device reports, e.g. 50/100/125/200...). The bin index is
 * {@code round(log2(iso / minIso))}. Whenever a new (smaller) minimum ISO is
 * found, the whole binned map for that physicalID is rebuilt (re-binned)
 * against the new reference so prior samples land in the correct stops.
 *
 * The store persists to JSON in the app's private storage
 * (files/noise_model/&lt;physicalID&gt;[_binned].json) so learned models survive
 * process restarts. Loading is lazy and saving is async.
 */
public class DynamicNoiseStore {
    private static final String DIR_NAME = "noise_model";
    /** v6 rejects ambiguous sampling modes and the old unconstrained fits. */
    private static final int CURRENT_VERSION = 6;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Single low-priority daemon thread for async disk persistence.
    private static final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DynamicNoiseStore-IO");
        t.setPriority(Thread.MIN_PRIORITY);
        t.setDaemon(true);
        return t;
    });

    // physicalID -> (isoBin -> IsoBin)
    private final Map<ProfileKey, Map<Integer, IsoBin>> store = new ConcurrentHashMap<>();
    // physicalID -> ordered raw samples (iso, s, o); source of truth for rebinning
    private final Map<ProfileKey, ArrayList<RawSample>> rawSamples = new ConcurrentHashMap<>();
    // physicalID -> minimum ISO observed so far (dynamic bin reference)
    private final Map<ProfileKey, Integer> minIso = new ConcurrentHashMap<>();
    // physicalID -> bounded recent-scene window (cooldown guard, in-memory only)
    private final Map<ProfileKey, RecentWindow> recentScenes = new ConcurrentHashMap<>();
    // physicalID -> loaded flag (avoids re-reading disk on every commit)
    private final Set<ProfileKey> loaded = ConcurrentHashMap.newKeySet();

    private final File storageDirectory;

    public DynamicNoiseStore() {
        this(null);
    }

    // Explicit storage root keeps persistence tests independent of Android.
    DynamicNoiseStore(File storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    /**
     * Clear every camera and binning mode. Call off the UI thread: serializing
     * deletion after queued saves prevents an older save restoring reset data.
     * The store lock blocks new commits until disk and memory are both reset.
     */
    public synchronized void resetAll() throws IOException {
        Future<Void> deletion = ioExecutor.submit(() -> {
            File[] files = dir().listFiles();
            if (files == null) throw new IOException("Cannot list noise model directory");
            for (File file : files) {
                if (file.getName().matches("-?\\d+(?:_binned)?\\.json(?:\\.tmp)?")
                        && !file.delete()) {
                    throw new IOException("Cannot delete noise model " + file.getName());
                }
            }
            return null;
        });
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    deletion.get();
                    break;
                } catch (InterruptedException e) {
                    // Finish the reset before allowing new commits, even if
                    // its initiating UI has gone away. Restore interruption below.
                    interrupted = true;
                }
            }
            store.clear();
            rawSamples.clear();
            minIso.clear();
            recentScenes.clear();
            loaded.clear();
        } catch (ExecutionException e) {
            throw new IOException("Could not reset adaptive noise models", e.getCause());
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static final class ProfileKey {
        final int physicalID;
        final boolean binned;

        ProfileKey(int physicalID, boolean binned) {
            this.physicalID = physicalID;
            this.binned = binned;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof ProfileKey)) return false;
            ProfileKey p = (ProfileKey) other;
            return physicalID == p.physicalID && binned == p.binned;
        }

        @Override public int hashCode() {
            return 31 * physicalID + (binned ? 1 : 0);
        }

        @Override public String toString() {
            return physicalID + (binned ? " (binned)" : "");
        }
    }

    /**
     * Single S/O noise-model estimation sample. Carries the scene key of the
     * commit that produced it (0 for legacy/persistence without a key) so a
     * re-shot scene can be matched and sharpened in place.
     */
    public static final class NoiseEstimate {
        public final double s;
        public final double o;
        public final long key;
        public final boolean higherIsoReference;
        private final int iso;
        public NoiseEstimate(double s, double o) {
            this(s, o, 0L);
        }
        public NoiseEstimate(double s, double o, long key) {
            this(s, o, key, 0, false);
        }

        private NoiseEstimate(double s, double o, long key, int iso, boolean reference) {
            this.s = s;
            this.o = o;
            this.key = key;
            this.iso = iso;
            this.higherIsoReference = reference;
        }
    }

    /**
     * Adaptive moving-average window for one ISO bin.
     * Grows up to {@link #MAX_WINDOW} estimations; once filled with 10 estimations
     * it always keeps a moving average of the last 10 elements to smooth out
     * estimator fluctuations without over-weighting stale history.
     */
    public static final class IsoBin {
        public static final int MAX_WINDOW = 10;
        private final ArrayList<NoiseEstimate> samples = new ArrayList<>(MAX_WINDOW);
        // Total estimations ever committed to this bin (for diagnostics).
        private int totalEstimations = 0;

        public void add(double s, double o) {
            add(s, o, 0L);
        }

        public void add(double s, double o, long key) {
            add(0, s, o, key);
        }

        private void add(int iso, double s, double o, long key) {
            samples.add(new NoiseEstimate(s, o, key, iso, false));
            totalEstimations++;
            // Adaptive moving window: cap at MAX_WINDOW. Before 10 estimations the
            // window grows with the data (averaging all available); once 10 is
            // reached it becomes a fixed moving average of the last 10.
            if (samples.size() > MAX_WINDOW) {
                samples.remove(0);
            }
        }

        /**
         * Duplicate-scene sharpening: replace the most recent sample with
         * this key by the quieter complete pair, in place - the window never slides,
         * so other scenes keep their slots. No-op for unknown keys.
         */
        public void replaceMin(long key, double s, double o) {
            if (key == 0L) return;
            for (int i = samples.size() - 1; i >= 0; i--) {
                NoiseEstimate n = samples.get(i);
                if (n.key == key) {
                    if (variance(s, o) < variance(n.s, n.o)) {
                        samples.set(i, new NoiseEstimate(s, o, key, n.iso, false));
                    }
                    return;
                }
            }
        }

        public NoiseEstimate average() {
            return averageAt(0);
        }

        private NoiseEstimate averageAt(int targetIso) {
            if (samples.isEmpty()) return null;
            // Normalize BEFORE trimming: one stop-wide ISO buckets otherwise
            // return the same coefficients for ISO 100 and ISO 140.
            ArrayList<NoiseEstimate> sorted = new ArrayList<>();
            for (NoiseEstimate n : samples) {
                double ratio = targetIso > 0 && n.iso > 0 ? (double) targetIso / n.iso : 1.0;
                sorted.add(new NoiseEstimate(n.s * ratio, n.o * ratio * ratio));
            }
            // Rank complete models, so clamped near-zero slopes with enormous
            // offsets cannot win against an actually quieter measurement.
            sorted.sort((a, b) -> Double.compare(variance(a.s, a.o), variance(b.s, b.o)));
            int keep = Math.max(1, (sorted.size() + 1) / 2);
            double ss = 0.0, so = 0.0;
            for (int i = 0; i < keep; i++) {
                ss += sorted.get(i).s;
                so += sorted.get(i).o;
            }
            return new NoiseEstimate(ss / keep, so / keep);
        }

        private int distinctScenes() {
            Set<Long> keys = new java.util.HashSet<>();
            for (NoiseEstimate n : samples) if (n.key != 0) keys.add(n.key);
            return keys.size();
        }

        public int count() {
            return samples.size();
        }

        public int totalEstimations() {
            return totalEstimations;
        }
    }

    /**
     * Bounded FIFO window of recently committed scene keys. A scene is
     * blocked while its key is still in the window and allowed again once
     * {@link #COOLDOWN} other scenes have pushed it out. This replaces the
     * old permanent dedup set with a cooldown so repeated identical
     * exposure/iso scenes can refresh the moving average periodically.
     *
     * Not persisted: cooldown resets each process start, which keeps the
     * learned model fresh instead of permanently freezing a scene out.
     */
    private static final class RecentWindow {
        // Number of distinct scenes that must pass before the same scene
        // is accepted again. 3 → a scene re-commits after ~3 other scenes.
        static final int COOLDOWN = 3;
        private final java.util.LinkedHashSet<Long> recent =
                new java.util.LinkedHashSet<>(COOLDOWN + 1);

        /** @return true if the scene may be committed (cooldown elapsed / new). */
        synchronized boolean allow(long key) {
            if (recent.contains(key)) return false;
            recent.add(key);
            if (recent.size() > COOLDOWN) {
                java.util.Iterator<Long> it = recent.iterator();
                if (it.hasNext()) { it.next(); it.remove(); }
            }
            return true;
        }
    }

    private static long sceneKey(double exposureTime, int iso) {
        long expNs = Math.round(exposureTime * 1.0e9);
        return ((long) iso << 32) ^ expNs;
    }

    private static double variance(double s, double o) {
        return 0.5 * s + o;
    }

    private static boolean validSample(int iso, double s, double o) {
        return iso > 0 && Double.isFinite(s) && Double.isFinite(o) && s > 0 && o >= 0;
    }

    private File dir() {
        if (storageDirectory != null) {
            storageDirectory.mkdirs();
            return storageDirectory;
        }
        File base = PhotonCamera.getSettingsManagerStatic().getContext().getFilesDir();
        File d = new File(base, DIR_NAME);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File fileFor(ProfileKey profile) {
        return new File(dir(), profile.physicalID + (profile.binned ? "_binned" : "") + ".json");
    }

    /** Serialized representation of one physicalID's state. */
    private static final class StoreDto {
        @SerializedName("version")
        int version;
        @SerializedName("physicalID")
        int physicalID;
        @SerializedName("binned")
        boolean binned;
        @SerializedName("minIso")
        int minIso;
        @SerializedName("samples")
        List<RawSample> samples;
    }

    /**
     * Lazily load a physicalID's state from disk on first access. Safe to
     * call repeatedly; only performs I/O once per physicalID per process.
     * The cooldown window is intentionally not restored so scenes can
     * refresh the model after a restart.
     */
    private void ensureLoaded(ProfileKey profile) {
        if (!loaded.add(profile)) return;
        File f = fileFor(profile);
        if (!f.exists()) return;
        try (FileReader r = new FileReader(f)) {
            StoreDto dto = GSON.fromJson(r, StoreDto.class);
            if (dto == null) return;
            if (dto.version != CURRENT_VERSION || dto.binned != profile.binned
                    || dto.physicalID != profile.physicalID) {
                Log.d("DynamicNoiseStore", "Discarding store v" + dto.version
                        + " incompatible with v" + CURRENT_VERSION + " pid=" + profile
                        + " (estimator or sampling mode changed)");
                return;
            }
            if (dto.minIso > 0) minIso.put(profile, dto.minIso);
            if (dto.samples != null) {
                ArrayList<RawSample> valid = new ArrayList<>();
                for (RawSample sample : dto.samples) {
                    if (sample != null && validSample(sample.iso, sample.s, sample.o)) valid.add(sample);
                }
                rawSamples.put(profile, valid);
            }
            Integer base = minIso.get(profile);
            if (base != null && rawSamples.get(profile) != null) {
                rebuildBins(profile, base);
            }
            Log.d("DynamicNoiseStore", "Loaded pid=" + profile
                    + " minIso=" + dto.minIso
                    + " samples=" + (dto.samples == null ? 0 : dto.samples.size()));
        } catch (Exception e) {
            Log.d("DynamicNoiseStore", "Load failed pid=" + profile + ": " + e);
        }
    }

    /**
     * Asynchronously persist a profile's state. Serializes rawSamples
     * and minIso, then writes atomically. The cooldown window is not
     * persisted so scenes can re-commit after a process restart.
     */
    private void saveAsync(ProfileKey profile) {
        ArrayList<RawSample> samples = rawSamples.get(profile);
        Integer base = minIso.get(profile);
        if (samples == null || base == null) return;
        final StoreDto dto = new StoreDto();
        dto.version = CURRENT_VERSION;
        dto.physicalID = profile.physicalID;
        dto.binned = profile.binned;
        dto.minIso = base;
        dto.samples = new ArrayList<>(samples);
        ioExecutor.submit(() -> {
            File f = fileFor(profile);
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            try {
                try (FileWriter w = new FileWriter(tmp)) {
                    GSON.toJson(dto, w);
                }
                java.nio.file.Files.move(tmp.toPath(), f.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Log.d("DynamicNoiseStore", "Save failed pid=" + profile + ": " + e);
                tmp.delete();
            }
        });
    }

    /**
     * Log2 ISO bin relative to the current dynamic minimum ISO.
     */
    private static int isoBin(int iso, int baseIso) {
        if (iso <= 0) iso = baseIso;
        if (baseIso <= 0) baseIso = iso;
        double bin = Math.log((double) iso / (double) baseIso) / Math.log(2.0);
        return (int) Math.round(bin);
    }

    /**
     * Rebuild the binned map for a profile from its raw samples against the
     * supplied minimum ISO. Preserves insertion order within each bin so the
     * moving-window average stays temporally consistent.
     */
    private void rebuildBins(ProfileKey profile, int newMinIso) {
        Map<Integer, IsoBin> bins = new ConcurrentHashMap<>();
        ArrayList<RawSample> samples = rawSamples.get(profile);
        if (samples != null) {
            for (RawSample rs : samples) {
                int bin = isoBin(rs.iso, newMinIso);
                IsoBin ib = bins.computeIfAbsent(bin, k -> new IsoBin());
                ib.add(rs.iso, rs.s, rs.o, rs.key);
            }
        }
        store.put(profile, bins);
    }

    /**
     * First commits the estimation to the noise map (unless this scene was
     * already measured), then returns the blended (averaged) S/O for the
     * resolved ISO bin. Committing before reading ensures the new sample
     * participates in the average. A scene still in cooldown does not add a
     * new sample (which would evict other scenes from the window) but
     * sharpens its existing sample in place with the lower estimate -
     * texture leak is one-sided, so the lower of two shots of the same scene
     * is the cleaner measurement. If the committed ISO is a new minimum, the
     * map is rebuilt against the new reference before reading back. State is
     * persisted to disk asynchronously after a commit or an in-place
     * sharpening.
     *
     * @return blended estimate, or null if the bin is still empty.
     */
    public NoiseEstimate commitAndGet(int physicalID, int iso, int analogIso,
                                      double s, double o, double exposureTime) {
        return commitAndGet(physicalID, iso, analogIso, s, o, exposureTime, false);
    }

    public synchronized NoiseEstimate commitAndGet(int physicalID, int iso, int analogIso,
                                      double s, double o, double exposureTime, boolean binned) {
        ProfileKey profile = new ProfileKey(physicalID, binned);
        ensureLoaded(profile);
        if (!validSample(iso, s, o) || !Double.isFinite(exposureTime) || exposureTime <= 0) {
            return resolve(profile, iso, analogIso);
        }

        RecentWindow window = recentScenes.computeIfAbsent(profile,
                k -> new RecentWindow());
        long key = sceneKey(exposureTime, iso);
        boolean allowed = window.allow(key);
        if (allowed) {
            ArrayList<RawSample> samples =
                    rawSamples.computeIfAbsent(profile, k -> new ArrayList<>());
            samples.add(new RawSample(iso, s, o, key));

            Integer curMin = minIso.get(profile);
            if (curMin == null || iso < curMin) {
                // New minimum ISO found: rebuild the whole map against it.
                minIso.put(profile, iso);
                rebuildBins(profile, iso);
                Log.d("DynamicNoiseStore", "New min ISO=" + iso
                        + " for pid=" + profile + ", rebuilt map ("
                        + samples.size() + " samples)");
            } else {
                Map<Integer, IsoBin> bins = store.computeIfAbsent(profile,
                        k -> new ConcurrentHashMap<>());
                int bin = isoBin(iso, curMin);
                IsoBin ib = bins.computeIfAbsent(bin, k -> new IsoBin());
                ib.add(iso, s, o, key);
                Log.d("DynamicNoiseStore", "Committed pid=" + profile
                        + " iso=" + iso + " bin=" + bin + " S=" + s + " O=" + o
                        + " count=" + ib.count() + " total=" + ib.totalEstimations());
            }
            saveAsync(profile);
        } else {
            // Same scene still in cooldown: replace its sample in place with
            // the lower pair instead of skipping, never evicting other
            // scenes' samples from the window.
            sharpenDuplicate(profile, iso, key, s, o);
            Log.d("DynamicNoiseStore", "Scene in cooldown, sharpened pid=" + profile
                    + " iso=" + iso + " exp=" + exposureTime + " S=" + s + " O=" + o);
        }

        return resolve(profile, iso, analogIso);
    }

    /**
     * Replaces the most recent raw sample with this scene key by the quieter
     * pair (both the persisted raw list and the live bin), in place.
     */
    private void sharpenDuplicate(ProfileKey profile, int iso, long key, double s, double o) {
        if (key == 0L) return;
        ArrayList<RawSample> samples = rawSamples.get(profile);
        if (samples == null) return;
        for (int i = samples.size() - 1; i >= 0; i--) {
            RawSample rs = samples.get(i);
            if (rs.key == key) {
                if (variance(s, o) < variance(rs.s, rs.o)) {
                    samples.set(i, new RawSample(iso, s, o, key));
                    Map<Integer, IsoBin> bins = store.get(profile);
                    if (bins != null) {
                        IsoBin ib = bins.get(isoBin(iso, minIso.getOrDefault(profile, iso)));
                        if (ib != null) ib.replaceMin(key, s, o);
                    }
                    saveAsync(profile);
                }
                return;
            }
        }
    }

    public synchronized IsoBin getBin(int physicalID, int iso) {
        ProfileKey profile = new ProfileKey(physicalID, false);
        ensureLoaded(profile);
        Integer baseIso = minIso.get(profile);
        if (baseIso == null) return null;
        Map<Integer, IsoBin> bins = store.get(profile);
        if (bins == null) return null;
        return bins.get(isoBin(iso, baseIso));
    }

    /** Read the model even when this capture cannot provide a usable fit. */
    public synchronized NoiseEstimate getEstimate(int physicalID, int iso, int analogIso,
                                                  boolean binned) {
        ProfileKey profile = new ProfileKey(physicalID, binned);
        ensureLoaded(profile);
        return resolve(profile, iso, analogIso);
    }

    private NoiseEstimate resolve(ProfileKey profile, int iso, int analogIso) {
        if (iso <= 0) return null;
        Integer base = minIso.get(profile);
        Map<Integer, IsoBin> bins = store.get(profile);
        if (base == null || bins == null) return null;
        IsoBin local = bins.get(isoBin(iso, base));
        NoiseEstimate measured = local == null ? null : local.averageAt(iso);
        NoiseEstimate reference = projectHigherIso(bins, base, iso, analogIso);
        if (reference == null) return measured;
        // Keep complete coefficient pairs. A tiny slope with a huge offset
        // must not be combined with the projected tiny offset: that would
        // erase real shot noise. Check both shadows and mid-gray for leakage.
        if (measured != null && measured.s * 0.02 + measured.o <= reference.s * 0.02 + reference.o
                && variance(measured.s, measured.o) <= variance(reference.s, reference.o)) {
            return new NoiseEstimate(measured.s, measured.o, 0L, iso, true);
        }
        return reference;
    }

    /**
     * Low-ISO detail is much larger than its noise. Use mature measurements
     * at least two stops higher, and at least three stops above the sensor's
     * observed minimum. Work in analogue-gain space so digital gain is not
     * mistaken for another independent analogue calibration point.
     */
    private static NoiseEstimate projectHigherIso(Map<Integer, IsoBin> bins, int base,
                                                  int targetIso, int analogIso) {
        double minimumReferenceIso = Math.max(4.0 * targetIso, 8.0 * Math.min(base, targetIso));
        ArrayList<Integer> indices = new ArrayList<>(bins.keySet());
        java.util.Collections.sort(indices);
        ArrayList<double[]> anchors = new ArrayList<>();
        for (int index : indices) {
            IsoBin bin = bins.get(index);
            if (bin.distinctScenes() < 3) continue;
            int referenceIso = (int) Math.round(base * Math.pow(2.0, index));
            if (referenceIso < minimumReferenceIso) continue;
            NoiseEstimate n = bin.averageAt(referenceIso);
            double gain = analogIso > 0 ? Math.max(1.0, (double) referenceIso / analogIso) : 1.0;
            double analogue = referenceIso / gain;
            if (analogue < 4.0 * targetIso) continue;
            anchors.add(new double[]{analogue, n.s / gain, n.o / (gain * gain)});
            if (anchors.size() == 3) break;
        }
        if (anchors.isEmpty()) return null;

        // Shot-noise variance is linear in gain. A median of normalized
        // coefficients prevents a single textured higher-ISO bucket from
        // controlling every daylight capture.
        ArrayList<Double> slopes = new ArrayList<>();
        double wx = 0, wy = 0, wxx = 0, wxy = 0;
        double minimumReadNoise = Double.POSITIVE_INFINITY;
        for (double[] a : anchors) {
            slopes.add(a[1] / a[0]);
            double x = a[0] * a[0];
            wx += x;
            wy += a[2];
            wxx += x * x;
            wxy += x * a[2];
            minimumReadNoise = Math.min(minimumReadNoise, a[2]);
        }
        java.util.Collections.sort(slopes);
        double slope = slopes.get(slopes.size() / 2);
        double count = anchors.size();
        double denominator = count * wxx - wx * wx;
        double readSlope = wxy / wxx;
        double readFloor = 0;
        if (denominator > 1e-12 * count * wxx) {
            readSlope = (count * wxy - wx * wy) / denominator;
            readFloor = (wy - readSlope * wx) / count;
            // Nonnegative least squares preserves the ISO-independent read
            // floor; simply scaling O by ISO² would lose it at low ISO.
            if (readSlope < 0) {
                readSlope = 0;
                readFloor = wy / count;
            } else if (readFloor < 0) {
                readFloor = 0;
                readSlope = wxy / wxx;
            }
        }
        // A positive gain model cannot have a constant floor above its
        // quietest measured offset. One textured anchor can otherwise turn
        // into a large floor shared by every lower-ISO capture.
        if (readFloor > minimumReadNoise) {
            readFloor = minimumReadNoise;
            readSlope = Math.max(0.0, (wxy - readFloor * wx) / wxx);
        }
        double targetGain = analogIso > 0 ? Math.max(1.0, (double) targetIso / analogIso) : 1.0;
        double targetAnalog = targetIso / targetGain;
        double s = slope * targetIso;
        double o = Math.min(minimumReadNoise,
                readSlope * targetAnalog * targetAnalog + readFloor) * targetGain * targetGain;
        return validSample(targetIso, s, o)
                ? new NoiseEstimate(s, o, 0L, targetIso, true) : null;
    }

    /** Raw sample kept for re-binning when a new minimum ISO is discovered.
     *  Also serialized to JSON via Gson. */
    private static final class RawSample {
        @SerializedName("iso")
        final int iso;
        @SerializedName("s")
        final double s;
        @SerializedName("o")
        final double o;
        /** Scene key of the commit (0 in stores written before keys were
         *  tracked; such samples never match a re-shot scene). */
        @SerializedName("key")
        final long key;
        RawSample(int iso, double s, double o) {
            this(iso, s, o, 0L);
        }
        RawSample(int iso, double s, double o, long key) {
            this.iso = iso;
            this.s = s;
            this.o = o;
            this.key = key;
        }
    }

    // Process-wide persistent dynamic noise model store.
    public static final DynamicNoiseStore dynamicNoiseStore = new DynamicNoiseStore();
}
