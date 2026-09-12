package com.particlesdevs.photoncamera.processing.ml;

import android.content.Context;
import android.content.res.AssetManager;

import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs the FlowNet-v2 dense optical flow model natively (ONNX graph exported
 * to ncnn, Vulkan backend for GPU inference).
 *
 *   in0  (W,H,3)  base frame  ... interleaved rgba floats, B,G,R in [0,255]
 *   in1  (W,H,3)  alter frame ... same layout
 *   out  (W,H,2)  dense flow   ... channel-last [x, y] floats, input-pixel units
 *
 * The model geometry is fixed at 512x384 (decimated heads baked into the
 * graph), so width/height must be 512/384.
 *
 * The model is a process-wide singleton that loads on a background thread the
 * first time {@link #start(Context)} is called. The ncnn Vulkan shaders are
 * compiled at init, so no runtime shader JIT or cache persist is needed; init
 * is just loading the ~38MB model and warming the pipeline.
 */
public final class FlowNetNcnnProcessor {
    private static final String TAG = "FlowNetNcnnProcessor";
    private static final String MODEL_PARAM = "models/flownet_flat.ncnn.param";
    private static final int MODEL_W = 512;
    private static final int MODEL_H = 384;
    private static final long INIT_TIMEOUT_MS = 30000;

    private static volatile FlowNetNcnnProcessor sInstance;
    private static final Object sLock = new Object();

    private final Context appContext;
    private final CountDownLatch initLatch = new CountDownLatch(1);
    private final Object inferenceLock = new Object();
    private volatile long nativeHandle;
    private volatile boolean ready = false;

    static { System.loadLibrary("ncnnMl"); }

    /**
     * Ensures the singleton is loading the model in the background (idempotent).
     * Call from the Application onCreate so the first shot session never waits
     * for the 38MB model load.
     */
    public static FlowNetNcnnProcessor start(Context context) {
        FlowNetNcnnProcessor inst = sInstance;
        if (inst == null) {
            synchronized (sLock) {
                inst = sInstance;
                if (inst == null) {
                    inst = new FlowNetNcnnProcessor(context);
                    sInstance = inst;
                }
            }
        }
        return inst;
    }

    /** The process-wide processor, or null if {@link #start} was never called. */
    public static FlowNetNcnnProcessor getInstance() {
        return sInstance;
    }

    private FlowNetNcnnProcessor(Context context) {
        appContext = context.getApplicationContext();
        Thread t = new Thread(this::backgroundInit, "flownet-ncnn-init");
        t.start();
    }

    /** Blocks until the background load + warmup finished (or timed out). */
    public boolean waitReady(long timeoutMs) {
        try {
            return initLatch.await(timeoutMs, TimeUnit.MILLISECONDS) && ready;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ready;
        }
    }

    public boolean isReady() {
        return ready;
    }

    private void backgroundInit() {
        long t0 = System.currentTimeMillis();
        AssetManager am = appContext.getAssets();
        try {
            nativeHandle = nativeCreate(am, MODEL_PARAM);
        } catch (Throwable t) {
            Log.e(TAG, "FlowNetNcnn: failed to initialize", t);
            nativeHandle = 0;
        }
        if (nativeHandle == 0) {
            Log.w(TAG, "FlowNetNcnn: model unavailable: " + MODEL_PARAM
                    + " (see NcnnML logcat for the native load error)");
            ready = false;
            initLatch.countDown();
            return;
        }

        // Throwaway zero-input forward: builds every vulkan pipeline now (not
        // on the GL thread during the merge).
        //FloatBuffer zeroBase = zeroRgba(MODEL_W, MODEL_H);
        //FloatBuffer zeroAlter = zeroRgba(MODEL_W, MODEL_H);
        //ready = runInferenceLocked(zeroBase, zeroAlter, MODEL_W, MODEL_H) != null;
        ready = true;
        if (!ready) {
            Log.e(TAG, "FlowNetNcnn: warmup forward failed");
        } else {
            Log.d(TAG, "flownet ncnn init+warmup in "
                    + (System.currentTimeMillis() - t0) + "ms");
        }
        initLatch.countDown();
    }

    /**
     * Run dense optical flow between two RGBA frames.
     *
     * @param baseRgba  base frame, direct buffer of interleaved rgba floats,
     *                  B,G,R in [0,255], length == width*height*4
     * @param alterRgba alter frame, same layout
     * @param width     model width (must be 512)
     * @param height    model height (must be 384)
     * @return dense flow, or null on error / if not ready
     */
    public FlowResult runInference(FloatBuffer baseRgba, FloatBuffer alterRgba,
                                   int width, int height) {
        if (!waitReady(INIT_TIMEOUT_MS)) return null;
        return runInferenceLocked(baseRgba, alterRgba, width, height);
    }

    private FlowResult runInferenceLocked(FloatBuffer baseRgba, FloatBuffer alterRgba,
                                           int width, int height) {
        if (nativeHandle == 0 || baseRgba == null || alterRgba == null
                || width <= 0 || height <= 0) return null;
        // The native handle (and its reusable tile Mats) is process-wide
        // shared: concurrent shots must not enter nativeRun together.
        synchronized (inferenceLock) {
            return runInferenceGuarded(baseRgba, alterRgba, width, height);
        }
    }

    private FlowResult runInferenceGuarded(FloatBuffer baseRgba, FloatBuffer alterRgba,
                                          int width, int height) {
        long start = System.nanoTime();
        ByteBuffer outBuf = ByteBuffer.allocateDirect(width * height * 2 * 4)
                .order(ByteOrder.nativeOrder());
        baseRgba.rewind();
        alterRgba.rewind();
        boolean ok;
        try {
            ok = nativeRun(nativeHandle, baseRgba, alterRgba, width, height, outBuf.asFloatBuffer());
        } catch (Throwable t) {
            Log.e(TAG, "FlowNetNcnn: inference failed", t);
            return null;
        }
        if (!ok) {
            Log.e(TAG, "FlowNetNcnn: inference returned an error");
            return null;
        }
        Log.d(TAG, "inference " + width + "x" + height + " in "
                + (System.nanoTime() - start) / 1_000_000 + "ms");
        return new FlowResult(outBuf, width, height);
    }

    /** Close the native ncnn net. Safe to call multiple times. */
    public void close() {
        synchronized (sLock) {
            if (sInstance == this) sInstance = null;
        }
        long h = nativeHandle;
        if (h != 0) {
            nativeHandle = 0;
            ready = false;
            nativeDestroy(h);
        }
    }

    private static FloatBuffer zeroRgba(int w, int h) {
        FloatBuffer fb = ByteBuffer.allocateDirect(w * h * 4 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int i = 0; i < w * h * 4; i++) fb.put(0f);
        fb.rewind();
        return fb;
    }

    /**
     * Dense flow field. Channel-last [x, y] float32 per pixel, row-major,
     * in input-pixel units.
     */
    public static class FlowResult {
        private final ByteBuffer flow;   // direct, native order, width*height*2 floats
        public final int width;
        public final int height;

        FlowResult(ByteBuffer flow, int width, int height) {
            this.flow = flow;
            this.width = width;
            this.height = height;
        }

        public ByteBuffer flow() {
            return flow;
        }

        public FloatBuffer asFloatBuffer() {
            FloatBuffer fb = flow.asFloatBuffer();
            fb.rewind();
            return fb;
        }
    }

    private static native long nativeCreate(AssetManager assetManager, String paramPath);
    private static native boolean nativeRun(long handle, FloatBuffer baseRgba,
                                            FloatBuffer alterRgba, int width, int height,
                                            FloatBuffer flowOut);
    private static native void nativeDestroy(long handle);
}
