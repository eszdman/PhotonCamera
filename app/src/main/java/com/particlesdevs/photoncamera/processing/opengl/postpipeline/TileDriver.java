package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES31.GL_ALL_BARRIER_BITS;
import static android.opengl.GLES31.glMemoryBarrier;

/**
 * Tiled execution driver (tiling Phase T1+): renders the post chain in
 * horizontal output bands so the working set (main1/2) is tile-sized
 * instead of full-frame.
 *
 * <p>Positioning convention (non-negotiable, see the sink replay in
 * {@code GLCoreBlockProcessing.drawBlocksToOutput(Bitmap)}): viewport
 * {@code (0, 0, W, h)} plus a {@code yOffset} uniform carrying the band
 * origin. Shaders MUST evaluate in absolute image coords
 * ({@code gl_FragCoord + yOffset}); the viewport-offset convention used by
 * intermediate {@code GLProg.drawBlocks} covers full textures only. Mixing
 * the two double-counts the origin — every migrated shader gets an explicit
 * convention assertion during review.
 *
 * <p>Halo rule: a band's input window is the band expanded by the chain-max
 * stencil radius ({@link com.particlesdevs.photoncamera.processing.opengl.nodes.Node#halo()});
 * skirts use exact mirror fetch (Amaze {@code pad} pattern), never clamp.
 */
public final class TileDriver {

    /**
     * Bump on every oracle-affecting change: proves which code a device log
     * came from (source and assets can skew otherwise, silently).
     */
    public static final String ORACLE_BUILD = "2026-09-12-t4d-input-sized-cap";

    private TileDriver() {}

    /**
     * Output bands for an image of height {@code h} with band height
     * {@code tileRows}: {@code [start, end)} pairs, last band clamped.
     * Pure geometry, JVM-tested.
     */
    public static List<int[]> computeBands(int h, int tileRows) {
        if (h < 0 || tileRows <= 0) {
            throw new IllegalArgumentException("h=" + h + " tileRows=" + tileRows);
        }
        List<int[]> bands = new ArrayList<>();
        for (int y = 0; y < h; y += tileRows) {
            bands.add(new int[]{y, Math.min(y + tileRows, h)});
        }
        return bands;
    }

    /**
     * TILE-aligned top/middle/bottom bands for oracle spot-checks, snapped
     * to {@code align} multiples and deduplicated (small images yield one
     * band). Pure geometry, JVM-tested.
     */
    public static List<int[]> snapBands(int imgH, int align) {
        if (imgH < 0 || align <= 0) {
            throw new IllegalArgumentException("imgH=" + imgH + " align=" + align);
        }
        int[][] req = {
                {0, Math.min(2 * align, imgH)},
                {Math.max(0, imgH / 2 - align), Math.min(imgH, imgH / 2 + align)},
                {Math.max(0, imgH - 2 * align), imgH},
        };
        List<int[]> bands = new ArrayList<>();
        Set<Integer> done = new HashSet<>();
        for (int[] band : req) {
            int b0 = (band[0] / align) * align;
            int b1 = Math.min(imgH, ((band[1] + align - 1) / align) * align);
            if (b1 > b0 && done.add(b0)) {
                bands.add(new int[]{b0, b1});
            }
        }
        return bands;
    }

    /**
     * Node-level oracle: renders TILE-aligned top/middle/bottom bands through
     * {@code fn} and requires bit-exactness vs the full render
     * {@code fullOut} (same input textures, same uniforms — only the output
     * window differs). {@code fn} receives {@code (b0, rows)} and returns a
     * fresh region texture holding image rows [{@code b0}, {@code b0+rows});
     * this method closes it. Restores nothing: {@code fn} must leave its node
     * exactly as found (fields + WorkingTexture). Returns worst maxDiff.
     */
    public interface RegionRun {
        GLTexture run(int b0, int rows);
    }

    /**
     * Input rows needed to render output rows [{@code o0}, {@code o1}) through
     * a resampling stage with output-to-input scale {@code zoom} (input row =
     * output row times zoom) plus a {@code halo} skirt, as {@code {w0, w1)}
     * clamped to the input frame. Conservative floor/ceil covers fractional
     * sample positions. Pure geometry, JVM-tested.
     */
    public static int[] inputWindow(int o0, int o1, int inH, float zoom, int halo) {
        int w0 = Math.max(0, (int) Math.floor(o0 * zoom) - halo);
        int w1 = Math.min(inH, (int) Math.ceil(o1 * zoom) + halo);
        if (w1 < w0) {
            w1 = w0;
        }
        return new int[]{w0, w1};
    }

    /**
     * Halo-expanded window for band [{@code b0}, {@code b1}) as
     * {@code {w0, w1)}, clamped to the frame. Pure geometry, JVM-tested.
     */
    public static int[] expandWindow(int b0, int b1, int imgH, int halo) {
        return new int[]{Math.max(0, b0 - halo), Math.min(imgH, b1 + halo)};
    }

    /**
     * Provable interior rows of band [{@code b0}, {@code b0+rows}) for stencil
     * shaders without an origin uniform, as {@code {rs, re}} (possibly empty).
     * Drops the bottom {@code halo} image rows (their outward taps fall outside
     * the frame and reflect tile-relatively), and — when {@code topSkip} — the
     * first row of non-top bands (capturesharpening's {@code <= 0} edge guard
     * keys off tile-relative coords and skips one tap there). Pure geometry,
     * JVM-tested.
     */
    public static int[] clampInterior(int b0, int rows, int imgH, int halo, boolean topSkip) {
        int rs = b0 + ((topSkip && b0 > 0) ? 1 : 0);
        int re = Math.min(b0 + rows, imgH - halo);
        if (re < rs) {
            re = rs;
        }
        return new int[]{rs, re};
    }

    /**
     * Column-axis mirror of {@link #clampInterior} (T4c transpose rotations
     * feed the sink from full-height column tiles): provable interior columns
     * of band [{@code c0}, {@code c0+cols}), as {@code {cs, ce}}. Drops the
     * right {@code halo} image columns and — when {@code leftSkip} — the
     * first column of non-left bands (capturesharpening's {@code <= 0} edge
     * guard is x/y symmetric, so it skips one tap there exactly like the
     * top-row case). Pure geometry, JVM-tested.
     */
    public static int[] clampInteriorX(int c0, int cols, int imgW, int halo, boolean leftSkip) {
        int cs = c0 + ((leftSkip && c0 > 0) ? 1 : 0);
        int ce = Math.min(c0 + cols, imgW - halo);
        if (ce < cs) {
            ce = cs;
        }
        return new int[]{cs, ce};
    }

    /**
     * Column-axis mirror of {@link #blitBand(GLTexture, GLTexture, int, int)}:
     * copies full-height columns [{@code srcX}, {@code srcX}+{@code cols}) of
     * {@code src} into {@code dst} at column 0 (exact copy, NEAREST). Heights
     * must match; the dst width holds the copied columns.
     */
    public static void blitColumn(GLTexture src, GLTexture dst, int srcX, int cols) {
        if (src.mSize.y != dst.mSize.y) {
            Log.e("TiledHarness", "blit height mismatch src=" + src.mSize.y
                    + " dst=" + dst.mSize.y + " (would scale, not copy)");
            return;
        }
        if (cols < 0 || cols > dst.mSize.x) {
            Log.e("TiledHarness", "blit column window " + cols
                    + " outside dst width " + dst.mSize.x);
            return;
        }
        src.BufferLoad();
        dst.BufferLoad();
        android.opengl.GLES30.glBindFramebuffer(
                android.opengl.GLES30.GL_READ_FRAMEBUFFER, src.mBuffer);
        android.opengl.GLES30.glBindFramebuffer(
                android.opengl.GLES30.GL_DRAW_FRAMEBUFFER, dst.mBuffer);
        android.opengl.GLES30.glBlitFramebuffer(
                srcX, 0, srcX + cols, src.mSize.y,
                0, 0, cols, dst.mSize.y,
                android.opengl.GLES30.GL_COLOR_BUFFER_BIT,
                android.opengl.GLES30.GL_NEAREST);
        // Same Adreno GMEM/binning coherency need as blitBand (see there).
        glMemoryBarrier(GL_ALL_BARRIER_BITS);
        int err;
        boolean bad = false;
        while ((err = android.opengl.GLES30.glGetError()) != android.opengl.GLES30.GL_NO_ERROR) {
            bad = true;
        }
        if (bad) {
            Log.e("TiledHarness", "blit col [" + srcX + "," + (srcX + cols) + ") src="
                    + src.mSize.x + "x" + src.mSize.y + " dst="
                    + dst.mSize.x + "x" + dst.mSize.y + " GL error");
        }
    }

    /**
     * Column-axis mirror of {@link #compareBand}: compares full-height columns
     * [{@code c0}, {@code c0}+{@code cols}) of a region texture against the
     * same columns of a full render, in 256-column chunks. Returns the worst
     * float max-abs-diff (0 = bit-exact).
     */
    public static float compareColumn(GLTexture fullOut, GLTexture regTex,
                                      int imgH, int c0, int cols, String tag) {
        return compareColumnRange(fullOut, regTex, imgH, c0, cols, 0, tag);
    }

    /**
     * Column-axis mirror of {@link #compareBandRange}: compares full-render
     * columns [{@code c0}, {@code c0}+{@code cols}) against region-texture
     * columns [{@code regX0}, {@code regX0}+{@code cols}) — for halo-expanded
     * region textures whose interior starts left of column 0.
     */
    public static float compareColumnRange(GLTexture fullOut, GLTexture regTex,
                                           int imgH, int c0, int cols, int regX0,
                                           String tag) {
        GLFormat float4 = new GLFormat(GLFormat.DataType.FLOAT_16, 4);
        int chunk = 256;
        int chunkBytes = chunk * imgH * 4 * 4;
        ByteBuffer bufA = Allocator.allocate(chunkBytes);
        ByteBuffer bufB = Allocator.allocate(chunkBytes);
        if (bufA == null || bufB == null) {
            Log.e(tag, "col [" + c0 + "," + (c0 + cols) + "): scratch alloc failed");
            if (bufA != null) Allocator.free(bufA);
            if (bufB != null) Allocator.free(bufB);
            return Float.POSITIVE_INFINITY;
        }
        if (regX0 < 0 || regX0 + cols > regTex.mSize.x) {
            Log.e(tag, "col [" + c0 + "," + (c0 + cols) + "): region window ["
                    + regX0 + "," + (regX0 + cols) + ") outside region width "
                    + regTex.mSize.x);
            Allocator.free(bufA);
            Allocator.free(bufB);
            return Float.POSITIVE_INFINITY;
        }
        float worst = 0f;
        double sumA = 0;
        double sumB = 0;
        int firstBadX = -1;
        int diffLogged = 0;
        double[] chA = new double[4];
        double[] chB = new double[4];
        long px = 0;
        try {
            for (int x = 0; x < cols; x += chunk) {
                int cw = Math.min(chunk, cols - x);
                int n = cw * imgH * 4 * 4;
                bufA.position(0);
                bufA.limit(n);
                regTex.BufferLoad();
                regTex.textureBuffer(float4, bufA, regX0 + x, 0, cw, imgH);
                bufB.position(0);
                bufB.limit(n);
                fullOut.BufferLoad();
                fullOut.textureBuffer(float4, bufB, c0 + x, 0, cw, imgH);
                float m = TiledCompareUtil.byteBufferFloatMaxAbsDiff(bufA, bufB);
                if (m > worst) {
                    worst = m;
                }
                if (firstBadX < 0 && m > 0f) {
                    firstBadX = c0 + x;
                }
                java.nio.FloatBuffer fa = bufA.duplicate()
                        .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
                java.nio.FloatBuffer fb = bufB.duplicate()
                        .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
                int floats = n / 4;
                for (int i = 0; i < floats; i += 4) {
                    float va = fa.get(i);
                    float vb = fb.get(i);
                    if (va == va) sumA += va;
                    if (vb == vb) sumB += vb;
                    px++;
                    float pd = 0f;
                    for (int c = 0; c < 4; c++) {
                        float xa = fa.get(i + c);
                        float xb = fb.get(i + c);
                        if (xa == xa) chA[c] += xa;
                        if (xb == xb) chB[c] += xb;
                        if (c < 3) {
                            float dd = xa - xb;
                            if (dd < 0) dd = -dd;
                            if (dd > pd) pd = dd;
                        }
                    }
                    if (pd > 0f && diffLogged < 8) {
                        int pix = i / 4;
                        int pr = pix / cw;
                        int pc = pix % cw;
                        Log.d(tag, "diffpx col [" + c0 + "," + (c0 + cols) + ") fullCol="
                                + (c0 + x + pc) + " regCol=" + (regX0 + x + pc) + " y=" + pr
                                + " reg=(" + fa.get(i) + "," + fa.get(i + 1) + "," + fa.get(i + 2)
                                + ") full=(" + fb.get(i) + "," + fb.get(i + 1) + ","
                                + fb.get(i + 2) + ")");
                        diffLogged++;
                    }
                }
            }
            long denom = Math.max(1, px);
            Log.d(tag, "col [" + c0 + "," + (c0 + cols) + ") maxDiff=" + worst
                    + (firstBadX >= 0 ? (" firstDiffCol=" + firstBadX) : "")
                    + " meanReg=" + (sumA / denom)
                    + " meanFull=" + (sumB / denom)
                    + " chReg=[" + chA[0] / denom + "," + chA[1] / denom + ","
                    + chA[2] / denom + "," + chA[3] / denom + "]"
                    + " chFull=[" + chB[0] / denom + "," + chB[1] / denom + ","
                    + chB[2] / denom + "," + chB[3] / denom + "]");
        } catch (Throwable t) {
            Log.e(tag, "col [" + c0 + "," + (c0 + cols) + ") failed", t);
            worst = Float.POSITIVE_INFINITY;
        } finally {
            Allocator.free(bufA);
            Allocator.free(bufB);
        }
        return worst;
    }

    public static float verifyNodeBands(GLTexture fullOut, int imgW, int imgH,
                                        int align, String tag, RegionRun fn) {
        float worst = 0f;
        for (int[] band : snapBands(imgH, align)) {
            int b0 = band[0], rows = band[1] - band[0];
            GLTexture reg = null;
            try {
                reg = fn.run(b0, rows);
                if (reg == null) {
                    Log.e(tag, "band [" + b0 + "," + (b0 + rows) + "): no region texture");
                    return Float.POSITIVE_INFINITY;
                }
                float m = compareBand(fullOut, reg, imgW, b0, rows, tag);
                if (m > worst) {
                    worst = m;
                }
            } catch (Throwable t) {
                Log.e(tag, "band [" + b0 + "," + (b0 + rows) + ") failed", t);
                return Float.POSITIVE_INFINITY;
            } finally {
                if (reg != null) {
                    try {
                        reg.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        Log.d(tag, "node strips maxDiff=" + worst + " build=" + ORACLE_BUILD);
        return worst;
    }

    /**
     * Copies image rows [{@code srcY}, {@code srcY}+{@code rows}) of
     * {@code src} into {@code dst} at row 0 (exact copy, NEAREST). Lets a
     * node oracle feed a tiled input exactly as the production driver will,
     * so the oracle proves the production configuration (not just region
     * rendering off a full input). Both textures keep their sizes; FBOs are
     * (re)attached as needed.
     */
    public static void blitBand(GLTexture src, GLTexture dst, int srcY, int rows) {
        blitBand(src, dst, srcY, rows, 0);
    }

    /**
     * Copies image rows [{@code srcY}, {@code srcY}+{@code rows}) of
     * {@code src} into {@code dst} at row {@code dstY} (exact copy, NEAREST).
     * Widths must match (see the guard); heights may exceed the copied rows.
     */
    public static void blitBand(GLTexture src, GLTexture dst, int srcY, int rows, int dstY) {
        if (src.mSize.x != dst.mSize.x) {
            Log.e("TiledHarness", "blit width mismatch src=" + src.mSize.x
                    + " dst=" + dst.mSize.x + " (would scale, not copy)");
            return;
        }
        if (dstY < 0 || dstY + rows > dst.mSize.y) {
            Log.e("TiledHarness", "blit dst window [" + dstY + "," + (dstY + rows)
                    + ") outside dst height " + dst.mSize.y);
            return;
        }
        src.BufferLoad();
        dst.BufferLoad();
        android.opengl.GLES30.glBindFramebuffer(
                android.opengl.GLES30.GL_READ_FRAMEBUFFER, src.mBuffer);
        android.opengl.GLES30.glBindFramebuffer(
                android.opengl.GLES30.GL_DRAW_FRAMEBUFFER, dst.mBuffer);
        // DST rect is rows, never dst height: partial takes (frame-bottom
        // sub-blits) must copy exactly, not NEAREST-scale into the tail.
        android.opengl.GLES30.glBlitFramebuffer(
                0, srcY, src.mSize.x, srcY + rows,
                0, dstY, dst.mSize.x, dstY + rows,
                android.opengl.GLES30.GL_COLOR_BUFFER_BIT,
                android.opengl.GLES30.GL_NEAREST);
        // Order blit writes before subsequent fetches/reads. A partial
        // silent blit (exact head rows, stale tail, no GL error) was observed
        // on a wide sub-blit: Adreno GMEM/binning coherency needs the barrier
        // (cheap GPU ordering, no CPU stall; harness-only path).
        glMemoryBarrier(GL_ALL_BARRIER_BITS);
        checkBlit(src, dst, srcY, rows);
    }

    /** Logs (only) any GL error after a blit; failures are silent otherwise. */
    private static void checkBlit(GLTexture src, GLTexture dst, int srcY, int rows) {
        int err;
        boolean bad = false;
        while ((err = android.opengl.GLES30.glGetError()) != android.opengl.GLES30.GL_NO_ERROR) {
            bad = true;
        }
        if (bad) {
            Log.e("TiledHarness", "blit [" + srcY + "," + (srcY + rows) + ") src="
                    + src.mSize.x + "x" + src.mSize.y + " dst="
                    + dst.mSize.x + "x" + dst.mSize.y + " GL error");
        }
    }

    /**
     * Compares image rows [{@code b0}, {@code b0}+{@code rows}) of a region
     * texture against the same rows of a full render, in 256-row chunks
     * (~38 MB transient at 64 MP, freed before return). Textures are read,
     * never modified. Returns the worst float max-abs-diff (0 = bit-exact).
     */
    public static float compareBand(GLTexture fullOut, GLTexture regTex,
                                    int imgW, int b0, int rows, String tag) {
        return compareBandRange(fullOut, regTex, imgW, b0, rows, 0, tag);
    }

    /**
     * Compares full-render rows [{@code b0}, {@code b0}+{@code rows}) against
     * region-texture rows [{@code regY0}, {@code regY0}+{@code rows}) — for
     * halo-expanded region textures whose interior starts below row 0.
     * Otherwise identical to {@link #compareBand}.
     */
    public static float compareBandRange(GLTexture fullOut, GLTexture regTex,
                                         int imgW, int b0, int rows, int regY0,
                                         String tag) {
        GLFormat float4 = new GLFormat(GLFormat.DataType.FLOAT_16, 4);
        int chunk = 256;
        int chunkBytes = imgW * chunk * 4 * 4;
        ByteBuffer bufA = Allocator.allocate(chunkBytes);
        ByteBuffer bufB = Allocator.allocate(chunkBytes);
        if (bufA == null || bufB == null) {
            Log.e(tag, "band [" + b0 + "," + (b0 + rows) + "): scratch alloc failed");
            if (bufA != null) Allocator.free(bufA);
            if (bufB != null) Allocator.free(bufB);
            return Float.POSITIVE_INFINITY;
        }
        if (regY0 < 0 || regY0 + rows > regTex.mSize.y) {
            Log.e(tag, "band [" + b0 + "," + (b0 + rows) + "): region window ["
                    + regY0 + "," + (regY0 + rows) + ") outside region height "
                    + regTex.mSize.y);
            Allocator.free(bufA);
            Allocator.free(bufB);
            return Float.POSITIVE_INFINITY;
        }
        float worst = 0f;
        double sumA = 0;
        double sumB = 0;
        // First full-image row whose chunk differs (clean renders compare
        // exactly 0.0, so any nonzero chunk is real). Localizes the defect:
        // strip-aligned starts implicate the divider/viewport, scattered or
        // edge-only starts implicate corruption or guards respectively.
        int firstBadY = -1;
        // First diverging pixels (RGB only; tile alpha is 0 vs 1 by design).
        // Values name the mechanism: zeros = undrawn rows, garbage =
        // corruption, shifted content = fetch offset, edge rows = guards.
        // Silent on green (identical renders compare exactly 0.0).
        int diffLogged = 0;
        // Per-channel means: a G/B/A-only divergence is invisible to the
        // R-only mean above yet decisive for diagnosis (e.g. alpha garbage
        // renders as black downstream while replaying deterministically).
        double[] chA = new double[4];
        double[] chB = new double[4];
        long px = 0;
        try {
            for (int y = 0; y < rows; y += chunk) {
                int rh = Math.min(chunk, rows - y);
                int n = imgW * rh * 4 * 4;
                bufA.position(0);
                bufA.limit(n);
                // BufferLoad (not BindBuffer): compute-written textures may
                // never have had an FBO, which reads back as zeros.
                // Region rows start at regY0 (halo-expanded tiles hold the
                // interior below row 0); the full side starts at b0 + y.
                regTex.BufferLoad();
                regTex.textureBuffer(float4, bufA, 0, regY0 + y, imgW, rh);
                bufB.position(0);
                bufB.limit(n);
                fullOut.BufferLoad();
                fullOut.textureBuffer(float4, bufB, 0, b0 + y, imgW, rh);
                float m = TiledCompareUtil.byteBufferFloatMaxAbsDiff(bufA, bufB);
                if (m > worst) {
                    worst = m;
                }
                if (firstBadY < 0 && m > 0f) {
                    firstBadY = b0 + y;
                }
                // Directional means: identifies which side is dark without
                // extra readbacks (red channel is representative here).
                java.nio.FloatBuffer fa = bufA.duplicate()
                        .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
                java.nio.FloatBuffer fb = bufB.duplicate()
                        .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
                int floats = n / 4;
                for (int i = 0; i < floats; i += 4) {
                    float va = fa.get(i);
                    float vb = fb.get(i);
                    if (va == va) sumA += va;
                    if (vb == vb) sumB += vb;
                    px++;
                    float pd = 0f;
                    for (int c = 0; c < 4; c++) {
                        float xa = fa.get(i + c);
                        float xb = fb.get(i + c);
                        if (xa == xa) chA[c] += xa;
                        if (xb == xb) chB[c] += xb;
                        if (c < 3) {
                            float dd = xa - xb;
                            if (dd < 0) dd = -dd;
                            if (dd > pd) pd = dd;
                        }
                    }
                    if (pd > 0f && diffLogged < 8) {
                        int pix = i / 4;
                        int pr = pix / imgW;
                        int pc = pix % imgW;
                        Log.d(tag, "diffpx band [" + b0 + "," + (b0 + rows) + ") fullRow="
                                + (b0 + y + pr) + " regRow=" + (regY0 + y + pr) + " x=" + pc
                                + " reg=(" + fa.get(i) + "," + fa.get(i + 1) + "," + fa.get(i + 2)
                                + ") full=(" + fb.get(i) + "," + fb.get(i + 1) + ","
                                + fb.get(i + 2) + ")");
                        diffLogged++;
                    }
                }
            }
            long denom = Math.max(1, px);
            Log.d(tag, "band [" + b0 + "," + (b0 + rows) + ") maxDiff=" + worst
                    + (firstBadY >= 0 ? (" firstDiffRow=" + firstBadY) : "")
                    + " meanReg=" + (sumA / denom)
                    + " meanFull=" + (sumB / denom)
                    + " chReg=[" + chA[0] / denom + "," + chA[1] / denom + ","
                    + chA[2] / denom + "," + chA[3] / denom + "]"
                    + " chFull=[" + chB[0] / denom + "," + chB[1] / denom + ","
                    + chB[2] / denom + "," + chB[3] / denom + "]");
        } catch (Throwable t) {
            Log.e(tag, "band [" + b0 + "," + (b0 + rows) + ") failed", t);
            worst = Float.POSITIVE_INFINITY;
        } finally {
            Allocator.free(bufA);
            Allocator.free(bufB);
        }
        return worst;
    }

    /**
     * Single-channel variant of {@link #compareBand} (R16F gain grid, P3-H):
     * compares rows [{@code b0}, {@code b0}+{@code rows}) of two same-size
     * single-channel textures in 256-row chunks (~16 MB transient at 64 MP).
     * Returns the worst float max-abs-diff (0 = bit-exact).
     */
    public static float compareBand1(GLTexture fullOut, GLTexture regTex,
                                     int imgW, int b0, int rows, String tag) {
        GLFormat float1 = new GLFormat(GLFormat.DataType.FLOAT_16, 1);
        int chunk = 256;
        int chunkBytes = imgW * chunk * 4;
        ByteBuffer bufA = Allocator.allocate(chunkBytes);
        ByteBuffer bufB = Allocator.allocate(chunkBytes);
        if (bufA == null || bufB == null) {
            Log.e(tag, "band1 [" + b0 + "," + (b0 + rows) + "): scratch alloc failed");
            if (bufA != null) Allocator.free(bufA);
            if (bufB != null) Allocator.free(bufB);
            return Float.POSITIVE_INFINITY;
        }
        float worst = 0f;
        double sumA = 0;
        double sumB = 0;
        int firstBadY = -1;
        int diffLogged = 0;
        long px = 0;
        try {
            for (int y = 0; y < rows; y += chunk) {
                int rh = Math.min(chunk, rows - y);
                int n = imgW * rh * 4;
                bufA.position(0);
                bufA.limit(n);
                regTex.BufferLoad();
                regTex.textureBuffer(float1, bufA, 0, y, imgW, rh);
                bufB.position(0);
                bufB.limit(n);
                fullOut.BufferLoad();
                fullOut.textureBuffer(float1, bufB, 0, b0 + y, imgW, rh);
                float m = TiledCompareUtil.byteBufferFloatMaxAbsDiff(bufA, bufB);
                if (m > worst) {
                    worst = m;
                }
                if (firstBadY < 0 && m > 0f) {
                    firstBadY = b0 + y;
                }
                java.nio.FloatBuffer fa = bufA.duplicate()
                        .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
                java.nio.FloatBuffer fb = bufB.duplicate()
                        .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
                int floats = n / 4;
                for (int i = 0; i < floats; i++) {
                    float va = fa.get(i);
                    float vb = fb.get(i);
                    if (va == va) sumA += va;
                    if (vb == vb) sumB += vb;
                    px++;
                    float pd = va - vb;
                    if (pd < 0) pd = -pd;
                    if (pd > 0f && diffLogged < 8) {
                        int pr = i / imgW;
                        int pc = i % imgW;
                        Log.d(tag, "diffpx band1 [" + b0 + "," + (b0 + rows) + ") fullRow="
                                + (b0 + y + pr) + " regRow=" + (y + pr) + " x=" + pc
                                + " reg=" + va + " full=" + vb);
                        diffLogged++;
                    }
                }
            }
            long denom = Math.max(1, px);
            Log.d(tag, "band1 [" + b0 + "," + (b0 + rows) + ") maxDiff=" + worst
                    + (firstBadY >= 0 ? (" firstDiffRow=" + firstBadY) : "")
                    + " meanReg=" + (sumA / denom)
                    + " meanFull=" + (sumB / denom));
        } catch (Throwable t) {
            Log.e(tag, "band1 [" + b0 + "," + (b0 + rows) + ") failed", t);
            worst = Float.POSITIVE_INFINITY;
        } finally {
            Allocator.free(bufA);
            Allocator.free(bufB);
        }
        return worst;
    }

    /**
     * Harness-only binding snapshot: logs which program is actually bound vs
     * believed, and which unit a sampler points at (Adreno quirk hunting after
     * T2b/T3a: bindings are only queried, never modified). Queried after the
     * draw — unit/program bindings persist (FBO ops and readbacks don't touch
     * them), so this reflects draw-time state.
     */
    static void logProgramState(String tag, String what, GLProg prog, String sampler, int expected) {
        try {
            int[] cur = new int[]{-1};
            android.opengl.GLES30.glGetIntegerv(
                    android.opengl.GLES30.GL_CURRENT_PROGRAM, cur, 0);
            int current = cur[0];
            int believed = prog.mCurrentProgramActive;
            int loc = android.opengl.GLES30.glGetUniformLocation(believed, sampler);
            int[] unit = new int[]{-1};
            if (loc >= 0) {
                android.opengl.GLES30.glGetUniformiv(believed, loc, unit, 0);
            }
            Log.d(tag, "bindstate " + what + " " + sampler + " current=" + current
                    + " believed=" + believed + " expected=" + expected
                    + " loc=" + loc + " unit=" + unit[0]);
        } catch (Throwable t) {
            Log.e(tag, "bindstate failed", t);
        }
    }

    private static void closeQuietly(GLTexture tex) {        if (tex != null) {
            try {
                tex.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static GLTexture newTile(int imgW, int rows, GLFormat fmt) {
        return new GLTexture(new Point(imgW, rows),
                new GLFormat(fmt), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
    }

    /** Production tail band height (T4): halo overhead per band is constant,
     * so wide bands minimize recompute; 512 also divides every target cleanly. */
    public static final int TAIL_TILE_ROWS = 512;

    /**
     * T4 tail driver, shared by the A/B proof and production: tiles the proven
     * segment (CaptureSharpening when active, then Sharpen2) in TAIL_TILE_ROWS
     * bands with cascading halo windows, assembles FULL band rows (not just
     * proven interiors — a missing row would read as stale downstream) into
     * {@code assembly}, and restores both nodes. Rendered tiles align with
     * their INPUT windows (buffer r holds image we0+r); sizes and offsets
     * below honor that everywhere.
     *
     * <p>{@code legacyFull} null (production) skips all compares;
     * {@code prove} false additionally skips every readback, so the production
     * path is pure GPU work. Each stage binds its program ONCE for all its
     * bands (per-band program rebinds black later draws; missing binds render
     * under the wrong program — see T2b). Tiles freed per band; assembly and
     * entry belong to the caller. Returns worst tile maxDiff (0 when nothing
     * compared; INF on band failure — production callers treat nonzero as
     * fallback).
     */
    public static float runTailTiled(CaptureSharpening cap, boolean captureActive,
                                     Sharpen2 shp, GLTexture entry, GLTexture legacyFull,
                                     GLTexture assembly, boolean prove) {
        String tag = "TiledHarness";
        boolean canProve = prove && legacyFull != null;
        if (entry == null) {
            if (prove) {
                Log.d(tag, "tailproof skipped (no entry snapshot)");
                return 0f;
            }
            throw new IllegalStateException("tail produce without entry");
        }
        if (assembly == null) {
            if (prove) {
                Log.d(tag, "tailproof skipped (no assembly)");
                return 0f;
            }
            throw new IllegalStateException("tail produce without assembly");
        }
        int imgW = assembly.mSize.x;
        int imgH = assembly.mSize.y;
        if (entry.mSize.x != imgW || entry.mSize.y != imgH) {
            if (prove) {
                Log.e(tag, "tailproof skipped (entry " + entry.mSize.x + "x" + entry.mSize.y
                        + " vs output " + imgW + "x" + imgH + ")");
                return 0f;
            }
            throw new IllegalStateException("tail produce entry/output size mismatch");
        }
        if (prove) {
            long px = (long) imgW * imgH;
            if (px > 16L * 1024 * 1024) {
                Log.d(tag, "tailproof skipped (" + px + "px over gate)");
                return 0f;
            }
        }
        GLFormat float4 = new GLFormat(GLFormat.DataType.FLOAT_16, 4);
        int hCap = (captureActive && cap != null) ? cap.halo() : 0;
        int hShp = shp.halo();
        GLTexture capLegacyFull = (canProve && captureActive && cap != null)
                ? cap.WorkingTexture : null;
        java.util.List<int[]> bands = computeBands(imgH, TAIL_TILE_ROWS);
        java.util.List<GLTexture> capTiles = new java.util.ArrayList<>();
        boolean failed = false;
        float worst = 0f;
        float worstAssembly = 0f;
        int bandCount = 0;
        try {
            if (captureActive && cap != null) {
                // Phase 1: all capture bands under capture's program. The
                // tiles are stored for phase 2 (freed in finally).
                cap.glProg.rebindProgram(cap.tileProgram);
                for (int[] band : bands) {
                    int b0 = band[0], b1 = band[1];
                    int[] wc = expandWindow(b0, b1, imgH, hShp + hCap);
                    int[] we = expandWindow(wc[0], wc[1], imgH, hCap);
                    GLTexture capInTile = null, capTile = null;
                    try {
                        capInTile = newTile(imgW, we[1] - we[0], float4);
                        blitBand(entry, capInTile, we[0], we[1] - we[0]);
                        if (prove) {
                            float bd = compareBand(entry, capInTile, imgW, we[0], we[1] - we[0],
                                    "TiledHarness-blit");
                            if (bd != 0f) {
                                Log.e(tag, "tailproof entry blit [" + we[0] + "," + we[1]
                                        + ") maxDiff=" + bd);
                            }
                        }
                        capTile = newTile(imgW, we[1] - we[0], float4);
                        cap.renderTile(capInTile, capTile);
                        logProgramState(tag, "cap-band" + b0, cap.glProg,
                                "InputBuffer", cap.tileProgram);
                        // Stage-localizing compare: capture tile interior vs
                        // the legacy capture render (proves which stage a
                        // final mismatch comes from).
                        // Rendered tiles align with their INPUT window (renders
                        // preserve input alignment): buffer r holds image
                        // we0+r, not wc0+r. Offsets below must use we0.
                        int[] keepC = clampInterior(b0, b1 - b0, imgH, hCap, false);
                        if (canProve && keepC[1] > keepC[0] && capLegacyFull != null) {
                            float cm = compareBandRange(capLegacyFull, capTile, imgW, keepC[0],
                                    keepC[1] - keepC[0], keepC[0] - we[0], tag);
                            Log.d(tag, "tailproof capture band [" + keepC[0] + "," + keepC[1]
                                    + ") maxDiff=" + cm);
                            if (cm > worst) {
                                worst = cm;
                            }
                        }
                        capTiles.add(capTile);
                        capTile = null;
                    } catch (Throwable t) {
                        Log.e(tag, "tailproof capture band [" + b0 + "," + b1 + ") failed", t);
                        failed = true;
                        closeQuietly(capTile);
                        break;
                    } finally {
                        closeQuietly(capInTile);
                    }
                }
            }
            if (!failed) {
                // Phase 2: all sharpen bands under sharpen's program.
                shp.glProg.rebindProgram(shp.tileProgram);
                for (int i = 0; i < bands.size(); i++) {
                    int[] band = bands.get(i);
                    int b0 = band[0], b1 = band[1];
                    int[] ws = expandWindow(b0, b1, imgH, hShp);
                    GLTexture sharpInTile = null, sharpTile = null;
                    try {
                        sharpInTile = newTile(imgW, ws[1] - ws[0], float4);
                        if (captureActive && cap != null) {
                            int[] wc = expandWindow(ws[0], ws[1], imgH, hCap);
                            // Stored tiles keep the INPUT window shape: buffer r
                            // holds image we0+r (renders preserve alignment), and
                            // we reaches the frame end. take therefore always
                            // covers the full sharpen window; the old clamped
                            // take left uninitialized texels at the frame edge.
                            int[] we1 = expandWindow(wc[0], wc[1], imgH, hCap);
                            int off = ws[0] - we1[0];
                            int take = ws[1] - ws[0];
                            GLTexture storedCap = capTiles.get(i);
                            if (off < 0 || off + take > storedCap.mSize.x) {
                                throw new IllegalStateException("tailproof window mismatch off="
                                        + off + " take=" + take + " cap=" + storedCap.mSize.x);
                            }
                            blitBand(storedCap, sharpInTile, off, take);
                            if (prove) {
                                float sd = compareBandRange(storedCap, sharpInTile, imgW,
                                        off, take, 0, "TiledHarness-blit");
                                if (sd != 0f) {
                                    Log.e(tag, "tailproof stage blit [" + ws[0] + "," + ws[1]
                                            + ") maxDiff=" + sd);
                                }
                            }
                        } else {
                            blitBand(entry, sharpInTile, ws[0], ws[1] - ws[0]);
                            if (prove) {
                                float bd = compareBand(entry, sharpInTile, imgW, ws[0],
                                        ws[1] - ws[0], "TiledHarness-blit");
                                if (bd != 0f) {
                                    Log.e(tag, "tailproof entry blit [" + ws[0] + "," + ws[1]
                                            + ") maxDiff=" + bd);
                                }
                            }
                        }
                        sharpTile = newTile(imgW, ws[1] - ws[0], float4);
                        shp.renderTile(sharpInTile, sharpTile);
                        logProgramState(tag, "shp-band" + b0, shp.glProg,
                                "InputBuffer", shp.tileProgram);
                        int[] keepS = clampInterior(b0, b1 - b0, imgH, hShp, false);
                        int[] keep = keepS;
                        if (captureActive && cap != null) {
                            int[] keepC = clampInterior(b0, b1 - b0, imgH, hCap, false);
                            int end = Math.min(keepS[1], keepC[1]);
                            if (b1 == imgH) {
                                // Frame-bottom cascade: capture rows past H-hCap
                                // are inexact (beyond-frame taps), so sharpen
                                // rows tapping them are excluded as well.
                                end = Math.min(end, keepC[1] - hShp);
                            }
                            keep = new int[]{Math.max(keepS[0], keepC[0]), end};
                        }
                        float m = 0f;
                        if (canProve && keep[1] > keep[0]) {
                            m = compareBandRange(legacyFull, sharpTile, imgW, keep[0],
                                    keep[1] - keep[0], keep[0] - ws[0], tag);
                        } else if (canProve && keep[1] <= keep[0]) {
                            Log.d(tag, "tailproof band [" + b0 + "," + b1 + ") empty interior");
                        }
                        if (m > worst) {
                            worst = m;
                        }
                        // Assemble FULL band rows (not just the proven interior:
                        // edge rows render with documented halo deviation, but a
                        // missing row would read as stale downstream).
                        blitBand(sharpTile, assembly, b0 - ws[0], b1 - b0, b0);
                        bandCount++;
                        if (canProve) {
                            float am = compareBandRange(legacyFull, assembly, imgW, keep[0],
                                    keep[1] - keep[0], keep[0], tag);
                            Log.d(tag, "tailproof assembly band [" + keep[0] + "," + keep[1]
                                    + ") maxDiff=" + am);
                            if (am > worstAssembly) {
                                worstAssembly = am;
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(tag, "tailproof band [" + b0 + "," + b1 + ") failed", t);
                        failed = true;
                        break;
                    } finally {
                        closeQuietly(sharpInTile);
                        closeQuietly(sharpTile);
                    }
                }
            }
            if (failed) {
                worst = Float.POSITIVE_INFINITY;
            }
        } finally {
            for (GLTexture t : capTiles) {
                closeQuietly(t);
            }
        }
        // Restore both nodes: WorkingTextures point at the assembly (real
        // output for Sharpen2; documented placeholder for CaptureSharpening,
        // whose true output lives only in tiles); the program stays sharpen's
        // (as after its legacy Run).
        if (captureActive && cap != null) {
            cap.WorkingTexture = assembly;
            cap.glProg.setTexture("InputBuffer", entry);
        }
        shp.WorkingTexture = assembly;
        shp.glProg.setTexture("InputBuffer", assembly);
        shp.glProg.setTexture("BlurBuffer", assembly);
        if (prove) {
            Log.d(tag, "tailproof strips maxDiff=" + worst + " build=" + ORACLE_BUILD);
            Log.d(tag, "tailproof assembly maxDiff=" + worstAssembly + " build=" + ORACLE_BUILD);
        } else {
            Log.d(tag, "tail tiled produce bands=" + bandCount + " rows=" + imgH
                    + " assembled (no compares)");
        }
        return worst;
    }

    /**
     * T4b fused production tail: interleaved per-band capture&rarr;sharpen&rarr;
     * rotate&rarr;sink with band-sized scratch and NO assembly main. Same
     * capture/sharpen windows, reads and writes as {@link #runTailTiled}
     * (shared helpers, phase-2 statements mirrored verbatim); the rotate band
     * draws sharpTile straight to the sink bitmap via the proven yOffset
     * mechanism. Band geometry per rotation (see the loop): 0/180 tile input
     * rows (180 mirrors the window end-for-end), 90/270 tile full-height
     * input columns (270 mirrors them); cropSize/rawSize stay full-frame so
     * the zero (rawSize-cropSize) offsets hold.
     * Programs rotate per band via bare rebindProgram (plain glUseProgram;
     * uniforms/textures are set per band before every draw exactly as in the
     * phased version). No readbacks except the sink's own pixels. Throws on
     * band failure (callers fall back to legacy); restores nodes to valid
     * placeholders on success (true output lives in the sink bitmap now).
     * Returns streamed band count.
     */
    public static int runTailProduce(CaptureSharpening cap, Sharpen2 shp,
                                     RotateWatermark rot, GLTexture entry,
                                     GLCoreBlockProcessing glproc,
                                     java.nio.ByteBuffer wrapped) {
        String tag = "TiledHarness";
        if (cap == null || shp == null || rot == null) {
            throw new IllegalStateException("tail produce without segment");
        }
        if (entry == null || entry.mSize == null) {
            throw new IllegalStateException("tail produce without entry");
        }
        if (glproc == null || wrapped == null) {
            throw new IllegalStateException("tail produce without sink");
        }
        if (rot.watermarkTex == null || rot.noiseTex == null) {
            throw new IllegalStateException("tail produce without rotate samplers");
        }
        int imgW = entry.mSize.x;
        int imgH = entry.mSize.y;
        GLFormat float4 = new GLFormat(GLFormat.DataType.FLOAT_16, 4);
        int hCap = cap.halo();
        int hShp = shp.halo();
        GLTexture.logLive(tag, "tail-produce-base");
        int bandCount = 0;
        // Band geometry per rotation: 0/180 tile input rows (180 mirrors the
        // window band end-for-end); 90/270 tile full-height input columns
        // (270 mirrors them). Output bands always cover output rows.
        boolean columns = rot.tileRot == 1 || rot.tileRot == 3;
        boolean mirror = rot.tileRot == 2 || rot.tileRot == 1;
        int outH = columns ? imgW : imgH;
        int outW = columns ? imgH : imgW;
        int wLen = columns ? imgW : imgH;
        for (int[] band : computeBands(outH, TAIL_TILE_ROWS)) {
            int b0 = band[0], b1 = band[1];
            int wb0 = mirror ? wLen - b1 : b0;
            int wb1 = mirror ? wLen - b0 : b1;
            // Capture window: identical to runTailTiled phase 1.
            int[] wc = expandWindow(wb0, wb1, wLen, hShp + hCap);
            int[] we = expandWindow(wc[0], wc[1], wLen, hCap);
            int[] ws = expandWindow(wb0, wb1, wLen, hShp);
            // Capture tiles are input-aligned: buffer r holds image we0+r.
            // The stored tile must therefore keep the INPUT window shape; the
            // old wc-shaped tile could not reach the frame end (wc clamps
            // earlier than we), so the sharpen input's far edge sampled
            // uninitialized texels and produced the bright garbage strip.
            // With the input shape, take always covers the full sharpen window.
            int off = ws[0] - we[0];
            int take = ws[1] - ws[0];
            if (off < 0 || off + take > (we[1] - we[0])) {
                throw new IllegalStateException("tail produce window mismatch off=" + off
                        + " take=" + take + " in=" + (we[1] - we[0]));
            }
            GLTexture capInTile = null, capTile = null;
            GLTexture sharpInTile = null, sharpTile = null;
            try {
                cap.glProg.rebindProgram(cap.tileProgram);
                capInTile = columns ? newTile(we[1] - we[0], imgH, float4)
                        : newTile(imgW, we[1] - we[0], float4);
                if (columns) {
                    blitColumn(entry, capInTile, we[0], we[1] - we[0]);
                } else {
                    blitBand(entry, capInTile, we[0], we[1] - we[0]);
                }
                capTile = columns ? newTile(we[1] - we[0], imgH, float4)
                        : newTile(imgW, we[1] - we[0], float4);
                cap.renderTile(capInTile, capTile);
                logProgramState(tag, "cap-band" + b0, cap.glProg,
                        "InputBuffer", cap.tileProgram);
                shp.glProg.rebindProgram(shp.tileProgram);
                sharpInTile = columns ? newTile(ws[1] - ws[0], imgH, float4)
                        : newTile(imgW, ws[1] - ws[0], float4);
                if (columns) {
                    blitColumn(capTile, sharpInTile, off, take);
                } else {
                    blitBand(capTile, sharpInTile, off, take);
                }
                sharpTile = columns ? newTile(ws[1] - ws[0], imgH, float4)
                        : newTile(imgW, ws[1] - ws[0], float4);
                if (b0 == 0) {
                    GLTexture.logLive(tag, "tail-produce-peak");
                }
                shp.renderTile(sharpInTile, sharpTile);
                logProgramState(tag, "shp-band" + b0, shp.glProg,
                        "InputBuffer", shp.tileProgram);
                // Rotate band straight to the sink (same yOffset sampling the
                // oracles prove; same viewport/draw/readPixels sequence as
                // the shared sink loop, via streamBand). Compensation per
                // rotation (see the tile-fed proof): 0 windows the band, 180
                // mirrors rows, 90 windows columns, 270 mirrors columns.
                rot.glProg.rebindProgram(rot.tileProgram);
                rot.glProg.setTexture("InputBuffer", sharpTile);
                rot.glProg.setTexture("Watermark", rot.watermarkTex);
                rot.glProg.setTexture("Noise", rot.noiseTex);
                int yOff;
                if (rot.tileRot == 0 || rot.tileRot == 3) {
                    // Buffer-relative (buffer cell 0 holds image cell ws0):
                    // rows for rot 0, columns for rot 3.
                    yOff = b0 - ws[0];
                } else if (rot.tileRot == 2) {
                    yOff = b0 + ws[1] - imgH + 1;
                } else {
                    yOff = b0 - (imgW - ws[1]);
                }
                rot.glProg.setVar("yOffset", yOff);
                logProgramState(tag, "rot-band" + b0, rot.glProg,
                        "InputBuffer", rot.tileProgram);
                glproc.streamBand(b0, b1 - b0, wrapped, outW * 4);
                bandCount++;
            } catch (Throwable t) {
                throw new IllegalStateException(
                        "tail produce band [" + b0 + "," + b1 + ") failed", t);
            } finally {
                closeQuietly(capInTile);
                closeQuietly(capTile);
                closeQuietly(sharpInTile);
                closeQuietly(sharpTile);
            }
        }
        // Restore nodes to valid placeholders (same post-state convention as
        // runTailTiled; true output lives in the sink bitmap now).
        cap.WorkingTexture = entry;
        cap.glProg.setTexture("InputBuffer", entry);
        shp.WorkingTexture = entry;
        shp.glProg.setTexture("InputBuffer", entry);
        shp.glProg.setTexture("BlurBuffer", entry);
        rot.glProg.setTexture("InputBuffer", entry);
        Log.d(tag, "tail tiled produce bands=" + bandCount + " rows=" + outH
                + " fused to sink rot=" + rot.tileRot + " (no compares)");
        return bandCount;
    }
}
