package com.particlesdevs.photoncamera.processing.ml;

import android.content.Context;
import android.util.Half;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/**
 * Device-side tests for {@link KernelNetNcnnProcessor}. Runs the real
 * kernelnet ncnn model from assets on the ncnnMl native lib
 * (Vulkan backend), feeding random luma planes + random sigma.
 */
@RunWith(AndroidJUnit4.class)
public class KernelNetProcessorTest {

    @Test
    public void runInference_withRandomValues() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue("model should load from assets", processor.isReady());

            Random rnd = new Random(42L);
            int width = 128;
            int height = 96;
            FloatBuffer gray = randomGray(rnd, width, height);
            float sigma = 0.05f + rnd.nextFloat() * 0.05f; // training range [0.05, 0.1]

            KernelNetNcnnProcessor.Result result = processor.runInference(gray, width, height, sigma);
            assertNotNull("inference must produce a result", result);

            int outW = (width - 1) / 2 + 1;
            int outH = (height - 1) / 2 + 1;
            assertEquals(outW, result.width);
            assertEquals(outH, result.height);

            ShortBuffer params = result.params().asShortBuffer();
            assertEquals(4 * outW * outH, params.remaining());
            for (int i = 0; i < params.remaining(); i += 4) {
                assertTrue("kernel params must be finite", Float.isFinite(Half.toFloat(params.get(i))));
                assertTrue(Float.isFinite(Half.toFloat(params.get(i + 1))));
                assertTrue(Float.isFinite(Half.toFloat(params.get(i + 2))));
                assertEquals("alpha lane must be 1", 1.0f, Half.toFloat(params.get(i + 3)), 0.0f);
            }
        } finally {
            processor.close();
        }
    }

    @Test
    public void runInference_withInvalidInputs_returnsNull() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue("model should load from assets", processor.isReady());
            assertNullSafe(processor);
        } finally {
            processor.close();
        }
    }

    @Test
    public void benchmark_onLargeImage() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue("model should load from assets", processor.isReady());

            int width = 2000;
            int height = 1500;
            FloatBuffer gray = randomGray(new Random(0L), width, height);
            float sigma = 0.08f;

            long best = Long.MAX_VALUE;
            KernelNetNcnnProcessor.Result result = null;
            for (int i = 0; i < 3; i++) {
                long start = System.nanoTime();
                result = processor.runInference(gray, width, height, sigma);
                long elapsed = System.nanoTime() - start;
                best = Math.min(best, elapsed);
            }
            assertNotNull(result);
            android.util.Log.d("KernelNetBenchmark",
                    "2000x1500 -> " + result.width + "x" + result.height
                            + " best=" + (best / 1_000_000) + "ms");
        } finally {
            processor.close();
        }
    }

    /**
     * Runs at realistic full-res input (rawSize/2 = what ESD4D feeds the net on a
     * 48MP sensor) to observe peak device memory. Native side logs backend + RSS.
     */
    @Test
    public void benchmark_atFullResolution() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue("model should load from assets", processor.isReady());

            int width = 4000;
            int height = 3000;
            FloatBuffer gray = randomGray(new Random(7L), width, height);
            float sigma = 0.08f;

            long best = Long.MAX_VALUE;
            KernelNetNcnnProcessor.Result result = null;
            int iters = 3;
            for (int i = 0; i < iters; i++) {
                System.gc();
                long start = System.nanoTime();
                result = processor.runInference(gray, width, height, sigma);
                long elapsed = System.nanoTime() - start;
                best = Math.min(best, elapsed);
                android.util.Log.d("KernelNetBenchmark",
                        "4000x3000 iter=" + i + " -> " + result.width + "x" + result.height
                                + " " + (elapsed / 1_000_000) + "ms");
            }
            assertNotNull(result);
            android.util.Log.d("KernelNetBenchmark",
                    "4000x3000 -> " + result.width + "x" + result.height
                            + " best=" + (best / 1_000_000) + "ms");
        } finally {
            processor.close();
        }
    }

    /**
     * Memory verification for the fixed-size tiling path: RSS must stay flat
     * while inference runs on MANY DIFFERENT capture resolutions (each new WxH
     * used to grow ncnn's Vulkan blob/workspace caches monotonically).
     *
     * Protocol: preallocate all inputs, warm up once on every shape (warms the
     * tile-sized Vulkan allocators + output-buffer malloc arenas), take a
     * baseline RSS, then run 3 more rounds over all shapes and require
     * max(RSS) - baseline to stay under RSS_TOLERANCE_KB.
     */
    @Test
    public void rss_isStableAcrossVaryingCaptureResolutions() throws Exception {
        final long RSS_TOLERANCE_KB = 96L * 1024;
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue("model should load from assets", processor.isReady());

            final int[][] sizes = {{4000, 3000}, {4096, 3072}, {4160, 3120}, {4208, 3120}};
            final float sigma = 0.08f;

            // Inputs allocated BEFORE the baseline so they don't read as growth.
            FloatBuffer[] grays = new FloatBuffer[sizes.length];
            for (int i = 0; i < sizes.length; i++) {
                grays[i] = randomGray(new Random(100L + i), sizes[i][0], sizes[i][1]);
            }

            // Warmup round over every shape.
            for (int i = 0; i < sizes.length; i++) {
                assertNotNull(processor.runInference(grays[i], sizes[i][0], sizes[i][1], sigma));
            }

            long baseline = settleAndReadRssKb();
            long maxRss = baseline;
            for (int round = 1; round <= 3; round++) {
                for (int i = 0; i < sizes.length; i++) {
                    assertNotNull(processor.runInference(grays[i], sizes[i][0], sizes[i][1], sigma));
                }
                long rss = settleAndReadRssKb();
                maxRss = Math.max(maxRss, rss);
                android.util.Log.i("KernelNetRss", "round " + round + ": rss=" + (rss / 1024)
                        + "MB (baseline " + (baseline / 1024) + "MB, growth "
                        + ((rss - baseline) / 1024) + "MB)");
            }
            long growth = maxRss - baseline;
            android.util.Log.i("KernelNetRss", "final: baseline=" + (baseline / 1024)
                    + "MB max=" + (maxRss / 1024) + "MB maxGrowth=" + (growth / 1024) + "MB");
            assertTrue("RSS grew " + (growth / 1024) + "MB across 3 rounds of varying-res"
                    + " captures; tiled inference should keep the footprint fixed",
                    growth < RSS_TOLERANCE_KB);
        } finally {
            processor.close();
        }
    }

    /**
     * Correctness sweep for tiling across SMALL tile sizes: for every KN_TILE
     * the stitched result must match the old single full-res pass (KN_NOTILE=1)
     * inside the interior. Border band (receptive-field radius, 19px input =
     * 10 half-res px) differs by design: tiles clamp-pad instead of the conv
     * zero-pad the full-res path gets at image edges. Also logs per-size timing.
     */
    @Test
    public void tiled_matchesFullResAcrossTileSizes() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final int width = 2048;
        final int height = 1536;
        FloatBuffer gray = randomGray(new Random(9L), width, height);
        float sigma = 0.08f;

        float[] full;
        KernelNetNcnnProcessor fullProcessor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue("model should load from assets", fullProcessor.isReady());
            android.system.Os.setenv("KN_NOTILE", "1", true);
            full = paramsToArray(fullProcessor.runInference(gray, width, height, sigma));
        } finally {
            android.system.Os.unsetenv("KN_NOTILE");
            fullProcessor.close();
        }

        final int[] tileSizes = {64, 128, 256, 512, 768, 1024};
        for (int tileSize : tileSizes) {
            android.system.Os.setenv("KN_TILE", String.valueOf(tileSize), true);
            KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(context);
            try {
                assertTrue("model should load from assets", processor.isReady());
                processor.runInference(gray, width, height, sigma); // warmup
                long start = System.nanoTime();
                float[] tiled = paramsToArray(processor.runInference(gray, width, height, sigma));
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                int w = (width - 1) / 2 + 1;
                int h = (height - 1) / 2 + 1;
                assertEquals(4 * w * h, tiled.length);
                assertEquals(tiled.length, full.length);

                final int border = 10; // ceil(RF 19px / 2) half-res px
                float maxInterior = 0f;
                float maxBorder = 0f;
                for (int c = 0; c < 3; c++) {
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            float d = Math.abs(tiled[(y * w + x) * 4 + c]
                                    - full[(y * w + x) * 4 + c]);
                            if (x < border || y < border || x >= w - border || y >= h - border) {
                                maxBorder = Math.max(maxBorder, d);
                            } else {
                                maxInterior = Math.max(maxInterior, d);
                            }
                        }
                    }
                }
                android.util.Log.i("KernelNetRss", "tile=" + tileSize
                        + ": interior maxDiff=" + maxInterior
                        + " border maxDiff=" + maxBorder
                        + " " + elapsedMs + "ms (" + width + "x" + height + ")");
                assertTrue("tile=" + tileSize + " interior deviates from full-res pass by "
                        + maxInterior, maxInterior < 0.02f);
            } finally {
                android.system.Os.unsetenv("KN_TILE");
                processor.close();
            }
        }
    }

    /**
     * A/B against the old full-res path at capture resolutions: runs 4 unique
     * 12MP shapes through KN_NOTILE=1 (old behavior) and through tiling,
     * logging per-run time and RSS growth across the varying shapes.
     */
    @Test
    public void perf_memoryAndSpeed_fullResVsTiled() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final int[][] sizes = {{4000, 3000}, {4096, 3072}, {4160, 3120}, {4208, 3120}};
        final float sigma = 0.08f;
        FloatBuffer[] grays = new FloatBuffer[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            grays[i] = randomGray(new Random(200L + i), sizes[i][0], sizes[i][1]);
        }

        // Old path: single full-res pass per shape.
        android.system.Os.setenv("KN_NOTILE", "1", true);
        long fullResGrowth;
        long fullResMs;
        KernelNetNcnnProcessor fullProcessor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue(fullProcessor.isReady());
            assertNotNull(fullProcessor.runInference(grays[0], sizes[0][0], sizes[0][1], sigma));
            long baseline = settleAndReadRssKb();
            long maxRss = baseline;
            long totalMs = 0;
            for (int round = 0; round < 2; round++) {
                for (int i = 0; i < sizes.length; i++) {
                    long start = System.nanoTime();
                    assertNotNull(fullProcessor.runInference(grays[i], sizes[i][0], sizes[i][1], sigma));
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    if (round == 1) totalMs += ms;
                }
                maxRss = Math.max(maxRss, settleAndReadRssKb());
            }
            fullResGrowth = maxRss - baseline;
            fullResMs = totalMs / (2 * sizes.length);
        } finally {
            android.system.Os.unsetenv("KN_NOTILE");
            fullProcessor.close();
        }

        // New path: fixed-size tiles.
        long tiledGrowth;
        long tiledMs;
        KernelNetNcnnProcessor tiledProcessor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue(tiledProcessor.isReady());
            assertNotNull(tiledProcessor.runInference(grays[0], sizes[0][0], sizes[0][1], sigma));
            long baseline = settleAndReadRssKb();
            long maxRss = baseline;
            long totalMs = 0;
            for (int round = 0; round < 2; round++) {
                for (int i = 0; i < sizes.length; i++) {
                    long start = System.nanoTime();
                    assertNotNull(tiledProcessor.runInference(grays[i], sizes[i][0], sizes[i][1], sigma));
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    if (round == 1) totalMs += ms;
                }
                maxRss = Math.max(maxRss, settleAndReadRssKb());
            }
            tiledGrowth = maxRss - baseline;
            tiledMs = totalMs / (2 * sizes.length);
        } finally {
            tiledProcessor.close();
        }

        android.util.Log.i("KernelNetRss", "A/B: fullRes avg=" + fullResMs + "ms growth="
                + (fullResGrowth / 1024) + "MB | tiled(1024) avg=" + tiledMs + "ms growth="
                + (tiledGrowth / 1024) + "MB across 4 unique 12MP shapes x2 rounds");
        assertTrue("tiled RSS grew " + (tiledGrowth / 1024) + "MB; footprint should be fixed",
                tiledGrowth < 96L * 1024);
    }

    /**
     * Backend A/B: Vulkan vs CPU (KN_CPU=1) timing on the tiled path.
     * Best-of-3 after warmup at small/mid/capture resolutions. The native side
     * logs which backend each net actually got (see "kernelnet backend:" in
     * logcat, including vulkan_device= state).
     */
    @Test
    public void perf_vulkanVsCpu() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final int[][] sizes = {{512, 512}, {2048, 1536}, {4000, 3000}};
        final float sigma = 0.08f;

        android.system.Os.setenv("KN_CPU", "1", true);
        long[] cpuMs = timeBackend(context, sizes, sigma);
        android.system.Os.unsetenv("KN_CPU");
        long[] vkMs = timeBackend(context, sizes, sigma);

        for (int i = 0; i < sizes.length; i++) {
            String verdict = vkMs[i] < cpuMs[i] ? "vulkan faster" : "CPU faster";
            android.util.Log.i("KernelNetRss", sizes[i][0] + "x" + sizes[i][1]
                    + ": cpu=" + cpuMs[i] + "ms vulkan=" + vkMs[i] + "ms -> " + verdict);
        }
    }

    private static long[] timeBackend(Context context, int[][] sizes, float sigma) {
        KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue("model should load from assets", processor.isReady());
            long[] best = new long[sizes.length];
            for (int i = 0; i < sizes.length; i++) {
                int w = sizes[i][0];
                int h = sizes[i][1];
                FloatBuffer gray = randomGray(new Random(300L + i), w, h);
                assertNotNull(processor.runInference(gray, w, h, sigma)); // warmup
                best[i] = Long.MAX_VALUE;
                for (int r = 0; r < 3; r++) {
                    long start = System.nanoTime();
                    assertNotNull(processor.runInference(gray, w, h, sigma));
                    best[i] = Math.min(best[i], (System.nanoTime() - start) / 1_000_000);
                }
            }
            return best;
        } finally {
            processor.close();
        }
    }

    /**
     * Diagnostic for backend verification: sustained 12MP inference on CPU
     * (KN_CPU=1) then on Vulkan, each ~10s, with logcat phase markers. Correlate
     * with `adb shell dumpsys gpu | grep "Proc <pid>"` sampled from the host:
     * GPU device memory must appear only in the vulkan phase, proving the ops
     * really dispatch to the GPU (not per-layer CPU fallback).
     */
    @Test
    public void diag_gpuMemory_backends() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final int width = 4000;
        final int height = 3000;
        final float sigma = 0.08f;
        FloatBuffer gray = randomGray(new Random(400L), width, height);
        android.util.Log.i("KernelNetDiag", "PID " + android.os.Process.myPid());

        android.system.Os.setenv("KN_CPU", "1", true);
        KernelNetNcnnProcessor cpu = new KernelNetNcnnProcessor(context);
        try {
            assertTrue(cpu.isReady());
            runSustained(cpu, gray, width, height, sigma, 10000, "cpu");
        } finally {
            cpu.close();
            android.system.Os.unsetenv("KN_CPU");
        }

        KernelNetNcnnProcessor vk = new KernelNetNcnnProcessor(context);
        try {
            assertTrue(vk.isReady());
            runSustained(vk, gray, width, height, sigma, 10000, "vulkan");
        } finally {
            vk.close();
        }
        // Give the host sampler time to observe post-close GPU memory release.
        android.util.Log.i("KernelNetDiag", "DONE");
        Thread.sleep(3000);
    }

    private static void runSustained(KernelNetNcnnProcessor processor, FloatBuffer gray,
                                     int width, int height, float sigma, long durationMs,
                                     String tag) {
        android.util.Log.i("KernelNetDiag", "PHASE " + tag + " START");
        long end = android.os.SystemClock.uptimeMillis() + durationMs;
        int iters = 0;
        long minMs = Long.MAX_VALUE;
        while (android.os.SystemClock.uptimeMillis() < end) {
            long start = System.nanoTime();
            assertNotNull(processor.runInference(gray, width, height, sigma));
            minMs = Math.min(minMs, (System.nanoTime() - start) / 1_000_000);
            iters++;
        }
        android.util.Log.i("KernelNetDiag", "PHASE " + tag + " END iters=" + iters
                + " min=" + minMs + "ms");
    }

    /**
     * Per-stage profiling sweep (native logs "kernelnet stages"): vulkan with
     * pooled allocators (new default), old per-extraction allocators
     * (KN_NOALLOC=1), one big tile (KN_TILE=2048), fp32 path (KN_FP32=1), and
     * a CPU reference — all on the same 2048x1536 input, best-of-3.
     */
    @Test
    public void perf_backendStageProfile() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final int width = 4000;
        final int height = 3000;
        final float sigma = 0.08f;
        FloatBuffer gray = randomGray(new Random(500L), width, height);
        android.system.Os.setenv("KN_STAGETIMING", "1", true);
        try {
            profileConfig(context, "12mp vulkan-tile1024(default)", null, gray, width, height, sigma);
            profileConfig(context, "12mp vulkan-tile1536", "KN_TILE=1536", gray, width, height, sigma);
            profileConfig(context, "12mp vulkan-tile2048", "KN_TILE=2048", gray, width, height, sigma);
            profileConfig(context, "12mp vulkan-tile4096", "KN_TILE=4096", gray, width, height, sigma);
            profileConfig(context, "12mp cpu", "KN_CPU=1", gray, width, height, sigma);
        } finally {
            android.system.Os.unsetenv("KN_STAGETIMING");
        }
    }

    private static void profileConfig(Context context, String name, String env,
                                      FloatBuffer gray, int width, int height, float sigma) {
        if (env != null) {
            String[] kv = env.split("=");
            try {
                android.system.Os.setenv(kv[0], kv[1], true);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        android.util.Log.i("KernelNetRss", "=== config " + name + " ===");
        KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(context);
        try {
            assertTrue("model should load", processor.isReady());
            assertNotNull(processor.runInference(gray, width, height, sigma)); // warmup
            long best = Long.MAX_VALUE;
            for (int r = 0; r < 3; r++) {
                long start = System.nanoTime();
                assertNotNull(processor.runInference(gray, width, height, sigma));
                best = Math.min(best, (System.nanoTime() - start) / 1_000_000);
            }
            android.util.Log.i("KernelNetRss", "config " + name + ": best=" + best + "ms ("
                    + width + "x" + height + ")");
        } finally {
            processor.close();
            if (env != null) {
                String key = env.split("=")[0];
                try {
                    android.system.Os.unsetenv(key);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    /** GC + wait for DirectByteBuffer cleaners, then read VmRSS in kB. */
    private static long settleAndReadRssKb() throws Exception {
        System.gc();
        Thread.sleep(400);
        return readRssKb();
    }

    private static long readRssKb() throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.trim().split("\\s+");
                    return Long.parseLong(parts[1]);
                }
            }
        }
        throw new IllegalStateException("VmRSS not found in /proc/self/status");
    }

    private static float[] paramsToArray(KernelNetNcnnProcessor.Result result) {
        assertNotNull("inference must produce a result", result);
        ShortBuffer params = result.params().asShortBuffer();
        float[] out = new float[params.remaining()];
        for (int i = 0; i < out.length; i++) {
            out[i] = Half.toFloat(params.get(i));
        }
        return out;
    }

    private static void assertNullSafe(KernelNetNcnnProcessor processor) {
        assertNullSafe(processor, null, 64, 48, 0.08f);
        assertNullSafe(processor, randomGray(new Random(1L), 64, 48), 0, 48, 0.08f);
        assertNullSafe(processor, randomGray(new Random(2L), 64, 48), -1, 48, 0.08f);
        assertNullSafe(processor, randomGray(new Random(3L), 64, 48), 64, 0, 0.08f);
        assertNullSafe(processor, randomGray(new Random(4L), 64, 48), 64, -1, 0.08f);
    }

    private static void assertNullSafe(KernelNetNcnnProcessor processor, FloatBuffer gray,
                                       int width, int height, float sigma) {
        org.junit.Assert.assertNull(processor.runInference(gray, width, height, sigma));
    }

    private static FloatBuffer randomGray(Random rnd, int width, int height) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(width * height * 4)
                .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        //= FloatBuffer.allocate(width * height);
        for (int i = 0; i < width * height; i++) {
            buffer.put(rnd.nextFloat()); // luma in [0,1]
        }
        buffer.rewind();
        return buffer;
    }
}
