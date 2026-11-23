//
// Created by eszdman on 19.11.2026.
//

package com.particlesdevs.photoncamera.processing.processor;

import android.graphics.Point;
import android.util.Pair;
import androidx.annotation.Keep;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.GyroAlignment;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.settings.DynamicNoiseStore;
import com.particlesdevs.photoncamera.settings.TunableInjector;
import com.particlesdevs.photoncamera.settings.merge.ESD4D;
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

// One synchronous capture. JNI borrows the direct buffers only for the duration of each call.
@Keep
final class KomputeBurst {
    private final ArrayList<ImageFrame> frames;
    private final Parameters params;
    private final ESD4D tuning = new ESD4D();
    private boolean useGyro;
    private boolean consumed;
    ByteBuffer bayer;
    private ByteBuffer kernels;
    private Point kernelSize;

    private KomputeBurst(ArrayList<ImageFrame> frames, Parameters params) {
        this.frames = frames;
        this.params = params;
    }

    static KomputeBurst tryMerge(ArrayList<ImageFrame> frames, Parameters params) {
        KomputeBurst shot = new KomputeBurst(frames, params);
        try {
            if (!shot.run()) return null;
            return shot;
        } catch (RuntimeException | LinkageError e) {
            if (shot.consumed) throw new IllegalStateException("Kompute burst failed after input transfer", e);
            Log.w("HdrxProcessor", "Using GLES for this burst: " + e);
            return null;
        } finally {
            if (shot.consumed) for (ImageFrame frame : frames) if (frame.buffer != null) frame.close();
        }
    }

    private boolean run() {
        TunableInjector.inject(tuning);
        if (!tuning.supportsKomputeMerge()) return false;
        useGyro = tuning.gyroOnly || (tuning.gyroPrealignment && "halide".equals(tuning.alignerSelect));
        int pixels = Math.multiplyExact(params.rawSize.x, params.rawSize.y);
        for (ImageFrame f : frames) {
            if (f.width != params.rawSize.x || f.height != params.rawSize.y || f.buffer == null ||
                    !f.buffer.isDirect() || f.packedBits < 0 || f.packedBits > 16 ||
                    (f.packedBits == 0 && f.buffer.capacity() != 2L * pixels) ||
                    (f.packedBits != 0 && (f.fp16 || f.buffer.capacity() < (pixels * (long)f.packedBits + 7) / 8)))
                return false;
        }
        System.loadLibrary("esd4d_startup");
        double s = 0, o = 0;
        for (Pair<Double,Double> n : params.noiseModeler.baseModel) { s += n.first; o += n.second; }
        boolean success = false;
        try {
            mergeWithTunables(params.rawSize.x, params.rawSize.y, params.cfaPattern, params.whiteLevel, params.blackLevel,
                    s/3, o/3, params.noiseModeler.AnalogueISO, params.binned,
                    (float)PhotonCamera.getSettings().mergeStrength, tuning.pack(),
                    params.mapSize.x, params.mapSize.y, params.gainMap,
                    PhotonCamera.getAppContext().getCacheDir().getAbsolutePath());
            success = true;
            return true;
        } finally {
            if (!success) {
                if (bayer != null) Allocator.free(bayer);
                releaseKernels();
            }
        }
    }

    private native void mergeWithTunables(int w, int h, int cfa, int white, float[] black, double s, double o,
            int analogIso, boolean binned, float strength, double[] tunables,
            int gainWidth, int gainHeight, float[] gains, String cache);
    private static native void add(long burst, ByteBuffer raw, int bits, boolean fp16, float exposure,
            int iso, int x, int y, boolean binned, float[] homography);

    private void collect(long burst) {
        ImageFrame ref = frames.get(0);
        for (ImageFrame frame : frames) {
            float[] h = useGyro && frame != ref ? GyroAlignment.between(ref, frame, params) : null;
            add(burst, frame.buffer, frame.packedBits, frame.fp16, frame.pair.layerMpy,
                    frame.pair.iso, frame.cropOriginX, frame.cropOriginY, frame.binned,
                    h == null ? null : GyroAlignment.columnMajor(h));
            consumed = true;
            frame.close();
        }
    }

    private double[] calibration(boolean commit, double s, double o) {
        DynamicNoiseStore store = DynamicNoiseStore.dynamicNoiseStore;
        DynamicNoiseStore.NoiseEstimate n = commit
                ? store.commitAndGet(params.physicalID, params.iso, params.noiseModeler.AnalogueISO,
                        s, o, params.exposureTime, params.binned)
                : store.getEstimate(params.physicalID, params.iso, params.noiseModeler.AnalogueISO, params.binned);
        return n == null ? null : new double[]{n.s, n.o, n.higherIsoReference ? 1 : 0};
    }

    private ByteBuffer allocateBayer(int bytes) {
        bayer = Allocator.allocate(bytes);
        return bayer;
    }

    private ByteBuffer allocateKernels(int w, int h) {
        kernelSize = new Point(w,h);
        kernels = Allocator.allocate(Math.multiplyExact(Math.multiplyExact(w,h), 8));
        return kernels;
    }

    private void appliedNoise(double s, double o) {
        java.util.Arrays.fill(params.noiseModeler.baseModel, new Pair<>(s,o));
        params.noiseModeler.setAdaptiveMpy(1);
    }

    void handOffKernels(PostPipeline pipeline) {
        pipeline.kernelParams = kernels.order(ByteOrder.nativeOrder()).asShortBuffer();
        pipeline.kernelParamsSize = kernelSize;
        pipeline.kernelParamsBase = kernels;
        kernels = null;
    }

    void releaseKernels() {
        if (kernels != null) Allocator.free(kernels);
        kernels = null;
    }
}
