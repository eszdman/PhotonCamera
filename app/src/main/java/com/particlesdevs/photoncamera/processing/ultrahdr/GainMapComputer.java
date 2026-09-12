package com.particlesdevs.photoncamera.processing.ultrahdr;

import android.graphics.Bitmap;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Normalises the GPU-encoded gain map produced by PostPipeline's
 * scene-anchored pass (pre-local-tone-map scene luma anchored to the stored
 * base's top percentile) into the final Ultra HDR gain map and derives its
 * {@code GainMapMin}/{@code GainMapMax} metadata.
 *
 * <p>The comparison shader stores, per pixel, {@code v = log2(gain) / SCALE}
 * clamped to [0,1], where gain is the ratio of anchored scene luminance to
 * linear SDR luminance (both + the decode offset). This pass recovers the
 * actual log-gain range present in the image and requantizes it to fill
 * [0,255]. The derived {@code GainMapMin}/{@code GainMapMax} (in log2 units)
 * go into the hdrgm XMP metadata so any ISO 21496-1 decoder can reconstruct
 * the HDR rendition from the SDR base for any display headroom.
 */
public final class GainMapComputer {

    /**
     * Total log2 range covered by the encoding (6 stops = 64x boost). Must
     * equal uScale passed to ultrahdr/gainmap.glsl.
     */
    public static final float SCALE = 6.0f;
    /**
     * OffsetSDR/OffsetHDR of the hdrgm XMP metadata (1/64). The comparison
     * shader adds it to both luminance sides before taking the ratio so the
     * ISO 21496-1 decode reproduces the HDR rendition exactly.
     */
    public static final float DECODE_OFFSET = 1.0f / 64.0f;

    // Pad so extreme pixels don't sit exactly on the metadata endpoints.
    private static final float RANGE_PAD_FRACTION = 0.02f;
    private static final float MIN_RANGE = 1e-3f;

    private GainMapComputer() {}

    public static class Result {
        /** Single-channel gain map, packed as R=G=B=value, A=255 (ARGB_8888). */
        public final Bitmap gainMap;
        public final float gainMapMin;
        public final float gainMapMax;
        public final float hdrCapacityMax;
        public final int gainW;
        public final int gainH;

        Result(Bitmap gainMap, float gainMapMin, float gainMapMax) {
            this.gainMap = gainMap;
            this.gainMapMin = gainMapMin;
            this.gainMapMax = gainMapMax;
            // HDRCapacityMax must be greater than HDRCapacityMin (0); otherwise
            // the viewer's headroom weight divides by zero/negative and every
            // viewer clamps it differently.
            this.hdrCapacityMax = Math.max(gainMapMax, 1e-3f);
            this.gainW = gainMap.getWidth();
            this.gainH = gainMap.getHeight();
        }
    }

    /**
     * @param src   encoded gain map straight from the GPU (R=G=B in [0,1]),
     *              full resolution (the GPU already reduces to the final grid)
     * @param scale total log2 range used at encode time (== {@link #SCALE})
     */
    public static Result compute(Bitmap src, float scale) {
        final int sw = src.getWidth();
        final int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) {
            throw new IllegalArgumentException("Empty gain map: " + sw + "x" + sh);
        }

        // Streamed row-by-row over the source bitmap: peak CPU memory is one
        // source row plus one gain-map row instead of several full-image
        // arrays (multi-hundred MB at full resolution). Two passes are needed
        // because the requantization range depends on the global maximum.
        // Both passes split rows across worker threads (see MaxTask /
        // QuantizeTask): bit-exact, as argued there.
        float maxBoost = MaxTask.computeMax(src, sw, sh, scale);

        // Gains are non-negative by construction (headroom >= 1), so the
        // metadata range is anchored at exactly 0: identity gain for everything
        // below white, matching the midtone-exact behaviour of the shader.
        if (!Float.isFinite(maxBoost) || maxBoost < 0f) maxBoost = 0f;
        if (maxBoost < MIN_RANGE) maxBoost = MIN_RANGE;

        final float gMin = 0f;
        final float gMax = maxBoost + Math.max(maxBoost * RANGE_PAD_FRACTION, MIN_RANGE);
        final float range = gMax - gMin;

        // Pass 2: identical walk, requantizing with the final range and
        // emitting completed gain-map rows straight into the output bitmap.
        // The GPU path already supplies the final-grid bitmap; reuse it when
        // mutable: each source row is read before that same row is
        // overwritten, so no future input is destroyed.
        final Bitmap out = src.isMutable()
                ? src
                : Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        QuantizeTask.quantize(src, out, sw, sh, scale, gMin, range);

        return new Result(out, gMin, gMax);
    }

    /** Target rows per ForkJoin leaf (tunes task count, not output). */
    private static final int BANDS_PER_TASK = 64;

    /**
     * Pass-1 row task: finds the local maximum of {@code R/255*scale} exactly
     * like the serial loop (same row order, same NaN-skipping comparison). The
     * global max combines pairwise with that comparison, which is exact: max
     * is associative and commutative, and NaNs lose every comparison in both
     * versions.
     */
    private static final class MaxTask extends RecursiveAction {
        private final Bitmap src;
        private final int sw, sh, loBand, hiBand;
        private final float scale;
        private float localMax = Float.NEGATIVE_INFINITY;

        MaxTask(Bitmap src, int sw, int sh, float scale, int loBand, int hiBand) {
            this.src = src;
            this.sw = sw;
            this.sh = sh;
            this.scale = scale;
            this.loBand = loBand;
            this.hiBand = hiBand;
        }

        static float computeMax(Bitmap src, int sw, int sh, float scale) {
            MaxTask root = new MaxTask(src, sw, sh, scale, 0, sh);
            ForkJoinPool.commonPool().invoke(root);
            return root.localMax;
        }

        @Override
        protected void compute() {
            if (hiBand - loBand <= BANDS_PER_TASK) {
                runBands();
                return;
            }
            int mid = loBand + (hiBand - loBand) / 2;
            MaxTask left = new MaxTask(src, sw, sh, scale, loBand, mid);
            MaxTask right = new MaxTask(src, sw, sh, scale, mid, hiBand);
            invokeAll(left, right);
            if (right.localMax > localMax) localMax = right.localMax;
            if (left.localMax > localMax) localMax = left.localMax;
        }

        private void runBands() {
            int[] row = new int[sw];
            float max = Float.NEGATIVE_INFINITY;
            for (int b = loBand; b < hiBand; b++) {
                src.getPixels(row, 0, sw, 0, b, sw, 1);
                for (int x = 0; x < sw; x++) {
                    float v = (((row[x] >> 16) & 0xFF) / 255f) * scale;
                    if (v > max) max = v;
                }
            }
            localMax = max;
        }
    }

    /**
     * Pass-2 row task: requantizes its rows with the final range, writing
     * disjoint output rows. Read-before-write per row preserves the in-place
     * path (out == src at the final grid); rows never cross task boundaries.
     */
    private static final class QuantizeTask extends RecursiveAction {
        private final Bitmap src;
        private final Bitmap out;
        private final int sw, sh, loBand, hiBand;
        private final float scale, gMin, range;

        QuantizeTask(Bitmap src, Bitmap out, int sw, int sh,
                     float scale, float gMin, float range,
                     int loBand, int hiBand) {
            this.src = src;
            this.out = out;
            this.sw = sw;
            this.sh = sh;
            this.scale = scale;
            this.gMin = gMin;
            this.range = range;
            this.loBand = loBand;
            this.hiBand = hiBand;
        }

        static void quantize(Bitmap src, Bitmap out, int sw, int sh,
                             float scale, float gMin, float range) {
            ForkJoinPool.commonPool().invoke(
                    new QuantizeTask(src, out, sw, sh, scale,
                            gMin, range, 0, sh));
        }

        @Override
        protected void compute() {
            if (hiBand - loBand <= BANDS_PER_TASK) {
                runBands();
                return;
            }
            int mid = loBand + (hiBand - loBand) / 2;
            invokeAll(new QuantizeTask(src, out, sw, sh, scale,
                            gMin, range, loBand, mid),
                    new QuantizeTask(src, out, sw, sh, scale,
                            gMin, range, mid, hiBand));
        }

        private void runBands() {
            int[] row = new int[sw];
            int[] outRow = new int[sw];
            for (int b = loBand; b < hiBand; b++) {
                src.getPixels(row, 0, sw, 0, b, sw, 1);
                for (int x = 0; x < sw; x++) {
                    float bandBoost = (((row[x] >> 16) & 0xFF) / 255f) * scale;
                    float vNorm = (bandBoost - gMin) / range;
                    if (vNorm < 0f) vNorm = 0f;
                    else if (vNorm > 1f) vNorm = 1f;
                    final int byteVal = Math.round(vNorm * 255.0f);
                    outRow[x] = (0xFF << 24) | (byteVal << 16) | (byteVal << 8) | byteVal;
                }
                out.setPixels(outRow, 0, sw, 0, b, sw, 1);
            }
        }
    }
}
