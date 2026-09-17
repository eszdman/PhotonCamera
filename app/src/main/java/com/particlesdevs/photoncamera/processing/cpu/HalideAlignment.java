package com.particlesdevs.photoncamera.processing.cpu;

import android.annotation.SuppressLint;
import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.scripts.PyramidAlignment;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_NEAREST;

/**
 * CPU (Halide / NEON) burst alignment - drop-in replacement for
 * {@link PyramidAlignment}, producing an identical Result atlas so the merge
 * stage is untouched.
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
 * The atlas is packed on the CPU like alignment/pack.glsl with startLevel =
 * 1: vectors live on the overlapped raw/32 tile grid (min_level = 1, stride
 * 8 texels there) and are broadcast over the 2x2 block of raw/16-grid cells
 * they cover, in alignmentToVec4 encoding - floor(d)/rawHalf in xy,
 * fract(d) in zw, d in raw/2 texel units with alt(p + d) ~= base(p).
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

    private final ArrayList<ImageFrame> images;
    private final Point size;
    private long ctx;

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

    public HalideAlignment(Point size, ArrayList<ImageFrame> images) {
        this.size = size;
        this.images = images;
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

    @SuppressLint("DefaultLocale")
    public void Run() {
        // The baked kernels match on a 64-raw-px native grid and the pack
        // loop below broadcasts with a fixed >> 2, so everything here assumes
        // parameters.tile == 16 (mergeAlign's TILE_AL). Warn loudly rather
        // than silently landing the vectors on a wrong grid.
        if (parameters.tile != TILE) {
            Log.w(TAG, "parameters.tile=" + parameters.tile + " but the Halide aligner"
                    + " is baked for a 16-raw-px atlas cell; vectors would land on the"
                    + " wrong grid - use the GL aligner for custom tile sizes");
        }
        final int rawW = parameters.rawSize.x;
        final int rawH = parameters.rawSize.y;
        final Point rawHalf = new Point(rawW / 2, rawH / 2);

        // Halide vector grid at MIN_LEVEL (raw/4 image): overlapped 16-texel
        // tiles with origins every 8 texels (native stride 32 raw px), last
        // tile ending at the image edge. Must match nInit's formula.
        int w1 = rawW;
        int h1 = rawH;
        for (int i = 0; i < MIN_LEVEL + 1; i++) { w1 /= 2; h1 /= 2; }
        final int NTX = Math.max(1, (w1 - TILE) / STRIDE + 1);
        final int NTY = Math.max(1, (h1 - TILE) / STRIDE + 1);

        Log.d(TAG, "raw " + rawW + "x" + rawH + ", " + images.size()
                + " frames (1 base + " + (images.size() - 1) + " aligned)");
        try {
            ctx = nInit(rawW, rawH);
        } catch (Throwable t) {
            throw fail("nInit(rawW=" + rawW + ", rawH=" + rawH + ")", t);
        }
        if (ctx == 0) throw new IllegalStateException("HalideAlignment nInit failed");

        // Stage 1: base pyramid once (normalized fp16 -> effective white 1).
        ImageFrame base = images.get(0);
        long t0 = System.currentTimeMillis();
        int err = nBase(ctx, base.buffer, 1.0f);
        if (err != 0) throw new IllegalStateException("alignburst_base_f16 failed: " + err);
        Log.d(TAG, "base pyramid: " + (System.currentTimeMillis() - t0) + "ms");

        // Zero-initialized atlas = identity alignment for uncovered cells.
        float[] atlas = new float[size.x * size.y * 4];

        for (int f = 1; f < images.size(); f++) {
            ImageFrame frame = images.get(f);
            // Alt normalized values are layerMpy times larger than base's;
            // effective white rescales them back (highlights clamp at the
            // base's range, same as the GLSL exposure clamp).
            float whiteEff = frame.pair.layerMpy > 0.f ? frame.pair.layerMpy : 1.f;
            t0 = System.currentTimeMillis();
            float[] vecs = nAlignFrame(ctx, frame.buffer, whiteEff);
            if (vecs == null) {
                throw new IllegalStateException("alignburst_f16 failed for frame " + f);
            }
            long tAlign = System.currentTimeMillis() - t0;

            if (f == 1) {
                // Sample vectors for diagnostics: center + corners of the
                // native field, in raw/2 texel units (before atlas packing).
                int[][] probe = {{NTX / 2, NTY / 2}, {1, 1}, {NTX - 2, 1},
                        {1, NTY - 2}, {NTX - 2, NTY - 2}};
                StringBuilder sb = new StringBuilder("frame 1 native vectors:");
                for (int[] pc : probe) {
                    sb.append(String.format(" [(%d,%d) d=(%+.1f,%+.1f)]", pc[0], pc[1],
                            vecs[pc[1] * NTX + pc[0]],
                            vecs[NTX * NTY + pc[1] * NTX + pc[0]]));
                }
                Log.d(TAG, sb.toString());
            }
            Point shift = PyramidAlignment.alignmentShift(parameters, f);
            // Broadcast the raw/32-grid vectors over the raw/16 alignment
            // grid (pack.glsl's startLevel = 1; values already raw/2 units):
            // each native vector covers a 2x2 block of atlas cells.
            for (int ty = 0; ty < parameters.alignmentSize.y && ty + shift.y < size.y; ty++) {
                int sy = Math.min(ty >> 1, NTY - 1);
                int row = sy * NTX;
                for (int tx = 0; tx < parameters.alignmentSize.x && tx + shift.x < size.x; tx++) {
                    int sx = Math.min(tx >> 1, NTX - 1);
                    float dx = vecs[row + sx];
                    float dy = vecs[NTX * NTY + row + sx];
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

        Result = new GLTexture(size, new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                BufferUtils.getFrom(atlas), GL_NEAREST, GL_CLAMP_TO_EDGE);
    }

    @Override
    public void close() {
        if (ctx != 0) {
            nRelease(ctx);
            ctx = 0;
        }
    }
}
