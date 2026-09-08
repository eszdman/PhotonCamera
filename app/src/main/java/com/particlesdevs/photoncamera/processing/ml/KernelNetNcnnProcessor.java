package com.particlesdevs.photoncamera.processing.ml;

import android.content.Context;
import android.content.res.AssetManager;

import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Runs the PKN anisotropic kernel PARAMETER model natively (ONNX graph exported
 * to ncnn, Vulkan backend for GPU inference).
 *
 * The exported graph is dynamic-shape (pure convolutions), so any camera
 * resolution works: the model halves the spatial dims internally.
 *
 *   in0  (W,H,1)  luma plane, values in [0,1]
 *   in1  (W,H,1)  sigma plane (scalar noise estimate tiled to full res)
 *   out  (W/2,H/2,3) channels: 0 = s1, 1 = s2, 2 = rho
 */
public final class KernelNetNcnnProcessor {
    private static final String TAG = "KernelNetNcnnProcessor";
    private static final String MODEL_PARAM = "models/kernelnet_aniso_v2_2_params.ncnn.param";
    private static final int NUM_PARAMS = 3;      // s1, s2, rho

    static { System.loadLibrary("ncnnMl"); }

    private long nativeHandle;

    public KernelNetNcnnProcessor(Context context) {
        AssetManager am = context.getAssets();
        long h = 0;
        try {
            h = nativeCreate(am, MODEL_PARAM);
        } catch (Throwable t) {
            Log.e(TAG, "KernelNetNcnn: failed to initialize", t);
        }
        nativeHandle = h;
        if (nativeHandle == 0) {
            Log.w(TAG, "KernelNetNcnn: model unavailable: " + MODEL_PARAM
                    + " (see NcnnML logcat for the native load error)");
        }
    }

    public boolean isReady() {
        return nativeHandle != 0;
    }

    /**
     * Run the parameter model on one luma plane.
     *
     * @param gray   luma plane in [0,1], position 0, length == width*height
     * @param width  input width
     * @param height input height
     * @param sigma  estimated noise sigma
     * @return params at half resolution, or null on error / if not ready
     */
    public Result runInference(FloatBuffer gray, int width, int height, float sigma) {
        if (!isReady() || gray == null || width <= 0 || height <= 0) return null;
        long start = System.nanoTime();
        int outW = (width - 1) / 2 + 1;
        int outH = (height - 1) / 2 + 1;
        ByteBuffer outBuf = ByteBuffer.allocateDirect(NUM_PARAMS * outH * outW * 4)
                .order(ByteOrder.nativeOrder());
        gray.rewind();
        boolean ok;
        try {
            ok = nativeRun(nativeHandle, gray, width, height, sigma, outBuf);
        } catch (Throwable t) {
            Log.e(TAG, "KernelNetNcnn: inference failed", t);
            return null;
        }
        if (!ok) {
            Log.e(TAG, "KernelNetNcnn: inference returned an error");
            return null;
        }
        Log.d(TAG, "inference " + width + "x" + height + " -> "
                + outW + "x" + outH + " in " + (System.nanoTime() - start) / 1_000_000 + "ms");
        return new Result(outBuf, outW, outH);
    }

    /** Close the native ncnn net. Safe to call multiple times. */
    public void close() {
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
    }

    /**
     * Parameter map at half resolution. Channel-major layout [s1, s2, rho],
     * each channel outH*outW floats in row-major order, values in
     * s1/s2 in [0, 2], rho in [-1, 1].
     */
    public static class Result implements KernelNetResult {
        private final ByteBuffer params;   // direct, native order, NUM_PARAMS*outH*outW floats
        public final int width;            // half-res
        public final int height;           // half-res

        Result(ByteBuffer params, int width, int height) {
            this.params = params;
            this.width = width;
            this.height = height;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        public ByteBuffer params() {
            return params;
        }

        @Override
        public FloatBuffer asFloatBuffer() {
            FloatBuffer fb = params.asFloatBuffer();
            fb.rewind();
            return fb;
        }
    }

    private static native long nativeCreate(AssetManager assetManager, String paramPath);
    private static native boolean nativeRun(long handle, FloatBuffer gray, int width, int height,
                                             float sigma, ByteBuffer out);
    private static native void nativeDestroy(long handle);
}
