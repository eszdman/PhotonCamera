package com.particlesdevs.photoncamera.processing.cpu;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.os.SystemClock;

import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GpuGyroPrewarp;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.scripts.PyramidAlignment;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_NEAREST;

/**
 * CPU (Halide / NEON) burst alignment - drop-in replacement for
 * {@link PyramidAlignment}, using the same Result atlas encoding. With gyro
 * prealignment the atlas contains residuals in prewarped coordinates; the
 * merge stage must compose them with the same reference-to-source homography.
 *
 * Structure mirrors PyramidAlignment (and the halide-align reference
 * implementation this wraps): the base frame's padded u8 pyramid is built
 * once per burst (nBase), then every alt frame is aligned against it with a
 * coarse-to-fine saturating-L1 tile search over 4 pyramid levels (nAlignFrame)
 * - 16x16-texel tiles with 50% OVERLAP (origins every 8 texels; 2x denser
 * vector field per axis), +-4 px exhaustive at the coarsest level + 3x3
 * refinement around the upsampled seed at finer levels, parabolic
 * sub-texel fit, sqrt-encoded u8 pyramids for noise-proportional
 * quantization, and a CPU 3x3 median filter of the final vector field that
 * replaces isolated mislocked tiles by their neighborhood median.
 *
 * The atlas is packed on the CPU like alignment/pack.glsl: vectors live on
 * the overlapped tile grid of whichever stage produced them and are
 * broadcast over the raw/16-grid atlas cells they cover, in
 * alignmentToVec4 encoding - floor(d)/rawHalf in xy, fract(d) in zw, d in
 * raw/2 texel units with alt(p + d) ~= base(p). With flow refinement
 * enabled ({@link #refineFlow}, default on) the field is the refined
 * level-0 grid - one vector per 16 raw px, edge-clamped. Without it the
 * hierarchical level-1 field is packed 1:1 onto a level-1-sized atlas (one
 * vector per 32 raw px cell); the caller (ESD4D) sizes
 * parameters.alignmentSize accordingly and merges with doubled TILE_AL
 * tiles, so every merge tile consumes exactly one native vector - no 2x2
 * broadcast.
 *
 * Frames must already be the fp16-normalized buffers produced by
 * Allocator.createF16 (ESD4D does this before alignment runs).
 */
public class HalideAlignment implements AutoCloseable {
    private static final String TAG = "HalideAlignment";

    /** Selects this path in ESD4D; false (or lib absent) falls back to the
     * GL pyramid alignment. */
    public static volatile boolean ENABLED = true;

    private static boolean available = false;
    static {
        try {
            System.loadLibrary("halidealign");
            available = true;
        } catch (UnsatisfiedLinkError e) {
            // armeabi-v7a builds ship without the Halide kernels
            Log.w(TAG, "libhalidealign unavailable, staying on GL alignment");
        }
    }

    /** Cap the Halide thread pool (e.g. to reserve cores for the GPU). */
    public static void setThreads(int n) {
        if (available) nSetThreads(n);
    }

    public Parameters parameters;
    public GLTexture Result;

    /**
     * Whether every frame's field is refined onto the level-0 (raw/2) grid
     * (block Lucas-Kanade Gauss-Newton seeded by the median-filtered
     * hierarchical field) instead of using the plain level-1 field. On =
     * returned arrays are 2x denser per axis (one vector per 16 raw px);
     * off = the plain level-1 field (one vector per 32 raw px), packed 1:1
     * - the caller must size parameters.alignmentSize to that native grid
     * (ESD4D does) and merge with doubled TILE_AL tiles. Pushed to the
     * library (before nInit) at the start of {@link #Run()}; set from the
     * ESD4D "Halide flow refinement" tunable.
     */
    public boolean refineFlow = true;
    /** Coupled fit with noise-aware scoring on the existing grid; used when refineFlow is off. */
    public boolean accurateSubpixel = true;
    /** Opt-in image-derived coarsest seed; requires accurate, unrefined mode. */
    public boolean coarseGlobal = false;
    /** Input raw-domain variance (including capture binning) = noiseS * normalized signal + noiseO, before merge-strength scaling. */
    public float noiseS = 0.0f, noiseO = 0.0f;
    /** Per-frame reference-to-source raw homographies. Returned vectors are residuals. */
    public float[][] gyroHomographies;

    private final ArrayList<ImageFrame> images;
    private final Point size;
    private long ctx;
    private final GLProg gyroProgram;
    private final GLTexture gyroInput;
    private GpuGyroPrewarp gyroPrewarp;

    // Baked generator params (must match the prebuilt kernels):
    // tile 16, stride 8 (50% overlap), radius 4, levels [1..4], sqrt
    // encoding, 5 base levels.
    private static final int MIN_LEVEL = 1;
    private static final int TILE = 16;
    private static final int STRIDE = 8;

    private static native long nInit(int rawW, int rawH);
    private static native int nBase(long ctx, ByteBuffer raw, float white);
    private static native float[] nAlignFrame(long ctx, ByteBuffer raw, float white);
    private static native void nRelease(long ctx);
    private static native void nSetThreads(int n);

    /**
     * Whether this build of the library refines every frame's field onto
     * the level-0 (raw/2) grid - the returned arrays are then 2x denser per
     * axis and must be broadcast 1:1 onto the atlas cells instead of 2x2.
     */
    private static native boolean nRefineEnabled();

    /** Runtime switch for the flow refinement; call before {@link #nInit}. */
    private static native void nSetRefineEnabled(boolean on);
    private static native void nSetAccurateSubpixelEnabled(boolean on);
    private static native void nSetCoarseGlobalEnabled(boolean on);
    private static native void nSetNoiseModel(long handle, float noiseS, float noiseO);

    public HalideAlignment(Point size, ArrayList<ImageFrame> images) {
        this(size, images, null, null);
    }

    /** Shares ESD4D's upload texture and program for GPU prealignment. */
    public HalideAlignment(Point size, ArrayList<ImageFrame> images, GLProg program, GLTexture input) {
        this.size = size;
        this.images = images;
        gyroProgram = program;
        gyroInput = input;
    }

    public static boolean isAvailable() {
        return available;
    }

    /** Logs the failure with the failing stage and arguments, then rethrows. */
    private static RuntimeException fail(String where, Throwable t) {
        Log.e(TAG, where + " failed: " + t, t);
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        throw new RuntimeException(where + ": " + t, t);
    }

    private static String bufInfo(ByteBuffer b) {
        return b == null ? "null" : ("cap=" + b.capacity() + " pos=" + b.position()
                + " rem=" + b.remaining() + " direct=" + b.isDirect());
    }

    private int gridWidth, gridHeight;
    private Point rawHalf;
    private float[] frameField;
    private FloatBuffer upload;
    private static final Point ORIGIN = new Point(0, 0);
    private long prewarpNs, matchingNs;
    private int warpedFrames;

    /** Full atlas for callers which inspect or replace motion before merging. */
    public void Run() {
        prepareReference();
        float[] atlas = new float[size.x * size.y * 4];
        for (int f = 1; f < images.size(); f++)
            packFrame(f, atlas, size, PyramidAlignment.alignmentShift(parameters, f));
        Result = new GLTexture(size, new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                BufferUtils.getFrom(atlas), GL_NEAREST, GL_CLAMP_TO_EDGE);
    }

    /** Keep the reference pyramid and reuse one motion texture for the merge loop. */
    public void startStreaming() {
        prepareReference();
        Point grid = parameters.alignmentSize;
        frameField = new float[grid.x * grid.y * 4];
        upload = ByteBuffer.allocateDirect(frameField.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        Result = new GLTexture(grid, new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                null, GL_NEAREST, GL_CLAMP_TO_EDGE);
    }

    public void alignFrame(int index) {
        packFrame(index, frameField, parameters.alignmentSize, ORIGIN);
        upload.clear();
        upload.put(frameField).flip();
        Result.loadData(upload);
    }

    private void prepareReference() {
        // The baked kernels match on a 32-raw-px native grid (level-1) or a
        // 16-raw-px one (level-0 refined), and the pack loop below samples
        // the field 1:1 onto parameters.alignmentSize, so everything here
        // assumes that grid matches the selected mode (ESD4D sizes it).
        // Warn loudly rather than silently landing the vectors on a wrong
        // grid.
        if (parameters.tile != TILE) {
            Log.w(TAG, "parameters.tile=" + parameters.tile + " but the Halide aligner"
                    + " is baked for a 16-raw-px atlas cell; vectors would land on the"
                    + " wrong grid - use the GL aligner for custom tile sizes");
        }
        final int rawW = parameters.rawSize.x;
        final int rawH = parameters.rawSize.y;
        rawHalf = new Point(rawW / 2, rawH / 2);

        // Refined (level-0) or plain (min_level = 1) field for this run:
        // push the tunable into the library first, then read back the mode
        // every downstream grid decision agrees with.
        nSetRefineEnabled(refineFlow);
        nSetAccurateSubpixelEnabled(accurateSubpixel);
        nSetCoarseGlobalEnabled(coarseGlobal);
        final boolean refine = nRefineEnabled();

        // Halide vector grid: overlapped 16-texel tiles with origins every
        // 8 texels (native stride 16 raw px at level 0, 32 at level 1),
        // last tile ending at the image edge. Level-0 image is raw/2,
        // min_level's is raw/4. Must match nInit's formulas.
        int gw = rawW, gh = rawH;
        final int halvings = refine ? 1 : MIN_LEVEL + 1;
        for (int i = 0; i < halvings; i++) { gw /= 2; gh /= 2; }
        gridWidth = Math.max(1, (gw - TILE) / STRIDE + 1);
        gridHeight = Math.max(1, (gh - TILE) / STRIDE + 1);

        Log.d(TAG, "raw " + rawW + "x" + rawH + ", " + images.size()
                + " frames (1 base + " + (images.size() - 1) + " aligned), "
                + (refine ? "flow-refined L0 field " + gridWidth + "x" + gridHeight
                          : "hierarchical L1 field " + gridWidth + "x" + gridHeight));
        try {
            ctx = nInit(rawW, rawH);
        } catch (Throwable t) {
            throw fail("nInit(rawW=" + rawW + ", rawH=" + rawH + ")", t);
        }
        if (ctx == 0) throw new IllegalStateException("HalideAlignment nInit failed");
        nSetNoiseModel(ctx, noiseS, noiseO);

        // Stage 1: base pyramid once (normalized fp16 -> effective white 1).
        ImageFrame base = images.get(0);
        long t0 = System.currentTimeMillis();
        int err = nBase(ctx, base.buffer, 1.0f);
        if (err != 0) throw new IllegalStateException("alignburst_base_f16 failed: " + err);
        Log.d(TAG, "base pyramid: " + (System.currentTimeMillis() - t0) + "ms");

    }

    @SuppressLint("DefaultLocale")
    private void packFrame(int f, float[] atlas, Point size, Point shift) {
        ImageFrame frame = images.get(f);
        // Alt normalized values are layerMpy times larger than base's;
        // effective white rescales them back (highlights clamp at the
        // base's range, same as the GLSL exposure clamp).
        float whiteEff = frame.pair.layerMpy > 0.f ? frame.pair.layerMpy : 1.f;
        long t0 = System.currentTimeMillis();
        float[] homography = gyroHomographies == null ? null : gyroHomographies[f];
        float[] vecs;
        long stageStart = SystemClock.elapsedRealtimeNanos();
        if (homography != null) {
            if (gyroPrewarp == null) {
                if (gyroProgram == null || gyroInput == null) {
                    throw new IllegalStateException("Gyro prealignment requires a GPU program and raw texture");
                }
                gyroPrewarp = new GpuGyroPrewarp(gyroProgram, gyroInput);
            }
            try (GpuGyroPrewarp.WarpedFrame warped = gyroPrewarp.warp(frame.buffer, homography)) {
                prewarpNs += SystemClock.elapsedRealtimeNanos() - stageStart;
                stageStart = SystemClock.elapsedRealtimeNanos();
                // Both hierarchical matching and flow refinement see the GPU-warped raw.
                vecs = nAlignFrame(ctx, warped.pixels, whiteEff);
                matchingNs += SystemClock.elapsedRealtimeNanos() - stageStart;
            }
            warpedFrames++;
        } else {
            vecs = nAlignFrame(ctx, frame.buffer, whiteEff);
            matchingNs += SystemClock.elapsedRealtimeNanos() - stageStart;
        }
        if (vecs == null) {
            throw new IllegalStateException("alignburst_f16 failed for frame " + f);
        }
        long tAlign = System.currentTimeMillis() - t0;

        if (f == 1 && gridWidth >= 2 && gridHeight >= 2) {
            // Sample vectors for diagnostics: center + corners of the
            // native field, in raw/2 texel units (before atlas packing).
            int[][] probe = {{gridWidth / 2, gridHeight / 2}, {1, 1}, {gridWidth - 2, 1},
                    {1, gridHeight - 2}, {gridWidth - 2, gridHeight - 2}};
            StringBuilder sb = new StringBuilder("frame 1 native vectors:");
            for (int[] pc : probe) {
                sb.append(String.format(" [(%d,%d) d=(%+.1f,%+.1f)]", pc[0], pc[1],
                        vecs[pc[1] * gridWidth + pc[0]],
                        vecs[gridWidth * gridHeight + pc[1] * gridWidth + pc[0]]));
            }
            Log.d(TAG, sb.toString());
        }
        // Sample the vector field 1:1 onto the atlas grid (values
        // already raw/2 units). The grid matches whichever stage
        // produced the field - refined level-0 tile origins sit at
        // multiples of 16 raw px, plain level-1 ones at multiples of 32
        // - both exactly the atlas cell origins of their mode (ESD4D
        // sizes parameters.alignmentSize and the merge's TILE_AL
        // accordingly); only the far-edge tail cells clamp to the last
        // native vector.
        for (int ty = 0; ty < parameters.alignmentSize.y && ty + shift.y < size.y; ty++) {
            int sy = Math.min(ty, gridHeight - 1);
            int row = sy * gridWidth;
            for (int tx = 0; tx < parameters.alignmentSize.x && tx + shift.x < size.x; tx++) {
                int sx = Math.min(tx, gridWidth - 1);
                float dx = vecs[row + sx];
                float dy = vecs[gridWidth * gridHeight + row + sx];
                float fdx = (float) Math.floor(dx);
                float fdy = (float) Math.floor(dy);
                int o = ((shift.y + ty) * size.x + shift.x + tx) * 4;
                atlas[o] = fdx / rawHalf.x;
                atlas[o + 1] = fdy / rawHalf.y;
                atlas[o + 2] = dx - fdx;
                atlas[o + 3] = dy - fdy;
            }
        }
        Log.d(TAG, "frame " + f + " (" + frame.pair.curlayer.name() + " x" + whiteEff
                + "): " + tAlign + "ms");
    }

    @Override
    public void close() {
        try {
            if (gyroPrewarp != null) {
                gyroPrewarp.close();
                gyroPrewarp = null;
            }
        } finally {
            if (ctx != 0) {
                Log.d(TAG, "GPU prewarp: " + warpedFrames + " frames, upload+warp+readback="
                        + prewarpNs / 1_000_000.0 + "ms; native matching=" + matchingNs / 1_000_000.0 + "ms");
                nRelease(ctx);
                ctx = 0;
            }
        }
    }
}
