package com.particlesdevs.photoncamera.processing.ml;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/**
 * Device-side tests for {@link FlowNetNcnnProcessor}. Runs the real
 * flownet ncnn model from assets on the actual ncnnMl native lib
 * (Vulkan), feeding random RGBA frames + checking the dense flow output.
 */
@RunWith(AndroidJUnit4.class)
public class FlowNetNcnnProcessorTest {

    private static final int WIDTH = 512;
    private static final int HEIGHT = 384;

    @Test
    public void runInference_withRandomValues() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FlowNetNcnnProcessor processor = FlowNetNcnnProcessor.start(context);
        try {
            assertTrue("model should load from assets", processor.waitReady(30000) && processor.isReady());

            Random rnd = new Random(42L);
            FloatBuffer base = randomRgba(rnd, WIDTH, HEIGHT);
            FloatBuffer alter = randomRgba(rnd, WIDTH, HEIGHT);

            FlowNetNcnnProcessor.FlowResult result = processor.runInference(base, alter, WIDTH, HEIGHT);
            assertNotNull("inference must produce a result", result);
            assertEquals(WIDTH, result.width);
            assertEquals(HEIGHT, result.height);

            FloatBuffer flow = result.asFloatBuffer();
            assertEquals(2 * WIDTH * HEIGHT, flow.remaining());
            while (flow.hasRemaining()) {
                assertTrue("flow must be finite", Float.isFinite(flow.get()));
            }
        } finally {
            processor.close();
        }
    }

    @Test
    public void runInference_withIdenticalFrames_returnsZeroFlow() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FlowNetNcnnProcessor processor = FlowNetNcnnProcessor.start(context);
        try {
            assertTrue("model should load from assets", processor.waitReady(30000) && processor.isReady());

            FloatBuffer frame = randomRgba(new Random(7L), WIDTH, HEIGHT);

            FlowNetNcnnProcessor.FlowResult result = processor.runInference(frame, frame, WIDTH, HEIGHT);
            assertNotNull("inference must produce a result", result);

            FloatBuffer flow = result.asFloatBuffer();
            long count = 0;
            double sumMagnitude = 0;
            while (flow.hasRemaining()) {
                float x = flow.get();
                float y = flow.get();
                assertTrue("flow must be finite", Float.isFinite(x) && Float.isFinite(y));
                sumMagnitude += Math.hypot(x, y);
                count++;
            }
            double mean = sumMagnitude / count;
            assertTrue("identical frames should have ~zero flow, got mean=" + mean, mean < 2.0);
        } finally {
            processor.close();
        }
    }

    @Test
    public void runInference_withInvalidInputs_returnsNull() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FlowNetNcnnProcessor processor = FlowNetNcnnProcessor.start(context);
        try {
            assertTrue("model should load from assets", processor.waitReady(30000) && processor.isReady());
            assertNullSafe(processor);
        } finally {
            processor.close();
        }
    }

    @Test
    public void benchmark_onFullResolution() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FlowNetNcnnProcessor processor = FlowNetNcnnProcessor.start(context);
        try {
            assertTrue("model should load from assets", processor.waitReady(30000) && processor.isReady());

            FloatBuffer base = randomRgba(new Random(0L), WIDTH, HEIGHT);
            FloatBuffer alter = randomRgba(new Random(1L), WIDTH, HEIGHT);

            long best = Long.MAX_VALUE;
            FlowNetNcnnProcessor.FlowResult result = null;
            for (int i = 0; i < 3; i++) {
                long start = System.nanoTime();
                result = processor.runInference(base, alter, WIDTH, HEIGHT);
                long elapsed = System.nanoTime() - start;
                best = Math.min(best, elapsed);
            }
            assertNotNull(result);
            android.util.Log.d("FlowNetBenchmark",
                    WIDTH + "x" + HEIGHT + " best=" + (best / 1_000_000) + "ms");
        } finally {
            processor.close();
        }
    }

    private static void assertNullSafe(FlowNetNcnnProcessor processor) {
        FloatBuffer frame = randomRgba(new Random(1L), WIDTH, HEIGHT);
        org.junit.Assert.assertNull(processor.runInference(null, frame, WIDTH, HEIGHT));
        org.junit.Assert.assertNull(processor.runInference(frame, null, WIDTH, HEIGHT));
        org.junit.Assert.assertNull(processor.runInference(frame, frame, 0, HEIGHT));
        org.junit.Assert.assertNull(processor.runInference(frame, frame, WIDTH, -1));
    }

    private static FloatBuffer randomRgba(Random rnd, int width, int height) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(width * height * 4 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int i = 0; i < width * height; i++) {
            buffer.put(rnd.nextFloat() * 255f); // B
            buffer.put(rnd.nextFloat() * 255f); // G
            buffer.put(rnd.nextFloat() * 255f); // R
            buffer.put(255f);                   // A, ignored
        }
        buffer.rewind();
        return buffer;
    }
}
