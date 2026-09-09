package com.particlesdevs.photoncamera.processing.ultrahdr;

import android.graphics.Bitmap;
import android.graphics.Point;

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
 * linear SDR luminance (both + the decode offset). This pass optionally
 * box-filters the map down ({@code down} pixels per axis), recovers the actual
 * log-gain range present in the image and requantizes it to fill [0,255]. The
 * derived {@code GainMapMin}/{@code GainMapMax} (in log2 units) go into the
 * hdrgm XMP metadata so any ISO 21496-1 decoder can reconstruct the HDR
 * rendition from the SDR base for any display headroom.
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
    /**
     * Downsample factor per axis for fixed use cases; prefer
     * {@link #computeScaleDown(Point)} for resolution-adaptive captures.
     */
    public static final int SCALE_DOWN = 1;

    /**
     * @deprecated Resolution-based scaling removed in favor of explicit
     * {@code Ultra HDR 4x downscale} toggle. This method now always returns
     * 1 (full resolution). Kept for API compat; callers should use
     * {@code PhotonCamera.getSettings().ultraHdr4x ? 4 : 1}.
     * Previous behavior was adaptive: full res up to 16 MP, above that
     * raw MP/16 rounded (50.3 MP -> 3, 64 MP -> 4). See revert of 634e996f.
     */
    @Deprecated
    public static int computeScaleDown(Point rawSize) {
        return 1;
    }
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
     * @param src   encoded gain map straight from the GPU (R=G=B in [0,1])
     * @param down  box-filter factor per axis; 1 keeps full resolution. Averaging
     *              happens in log domain, i.e. a geometric mean of gains.
     * @param scale total log2 range used at encode time (== {@link #SCALE})
     */
    public static Result compute(Bitmap src, int down, float scale) {
        final int sw = src.getWidth();
        final int sh = src.getHeight();
        final int gw = Math.max(1, sw / down);
        final int gh = Math.max(1, sh / down);
        if (sw <= 0 || sh <= 0) {
            throw new IllegalArgumentException("Empty gain map: " + sw + "x" + sh);
        }

        // Streamed band-by-band over the source bitmap: peak CPU memory is
        // one source row plus one gain-map row instead of several full-image
        // arrays (multi-hundred MB at full resolution). Two passes are needed
        // because the requantization range depends on the global maximum.
        // Both passes split bands across worker threads (see MaxTask /
        // QuantizeTask): bit-exact, as argued there.
        float maxBoost = MaxTask.computeMax(src, sw, sh, gw, gh, down, scale);

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
        //
        // The adaptive GPU path already supplies a final-grid bitmap
        // (down == 1). Reuse it when mutable: each source row is read before
        // that same row is overwritten, so no future input is destroyed.
        //
        // A source that still requires CPU downsampling has different output
        // dimensions and must retain the separate output bitmap.
        final Bitmap out = src.isMutable() && sw == gw && sh == gh
                ? src
                : Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
        QuantizeTask.quantize(src, out, sw, sh, gw, gh, down, scale, gMin, range);

        return new Result(out, gMin, gMax);
    }

    /** Target bands per ForkJoin leaf (tunes task count, not output). */
    private static final int BANDS_PER_TASK = 64;

    /**
     * Pass-1 band task: box-averages its bands exactly like the serial loop
     * (private accumulators, same row order) and tracks the local maximum
     * with the same NaN-skipping comparison. The global max combines
     * pairwise with that comparison, which is exact: max is associative and
     * commutative, and NaNs lose every comparison in both versions.
     */
    private static final class MaxTask extends RecursiveAction {
        private final Bitmap src;
        private final int sw, sh, gw, down, loBand, hiBand;
        private final float scale;
        private float localMax = Float.NEGATIVE_INFINITY;

        MaxTask(Bitmap src, int sw, int sh, int gw, int down, float scale,
                int loBand, int hiBand) {
            this.src = src;
            this.sw = sw;
            this.sh = sh;
            this.gw = gw;
            this.down = down;
            this.scale = scale;
            this.loBand = loBand;
            this.hiBand = hiBand;
        }

        static float computeMax(Bitmap src, int sw, int sh, int gw, int gh,
                                int down, float scale) {
            MaxTask root = new MaxTask(src, sw, sh, gw, down, scale, 0, gh);
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
            MaxTask left = new MaxTask(src, sw, sh, gw, down, scale, loBand, mid);
            MaxTask right = new MaxTask(src, sw, sh, gw, down, scale, mid, hiBand);
            invokeAll(left, right);
            if (right.localMax > localMax) localMax = right.localMax;
            if (left.localMax > localMax) localMax = left.localMax;
        }

        private void runBands() {
            int[] row = new int[sw];
            long[] sums = new long[gw];
            int[] counts = new int[gw];
            float[] bandBoost = new float[gw];
            float max = Float.NEGATIVE_INFINITY;
            for (int b = loBand; b < hiBand; b++) {
                int yEnd = Math.min((b + 1) * down, sh);
                resetAccumulators(sums, counts);
                for (int y = b * down; y < yEnd; y++) {
                    src.getPixels(row, 0, sw, 0, y, sw, 1);
                    accumulateRow(row, sums, counts, gw, down);
                }
                finishBand(sums, counts, bandBoost, scale, gw);
                for (int gx = 0; gx < gw; gx++) {
                    if (bandBoost[gx] > max) max = bandBoost[gx];
                }
            }
            localMax = max;
        }
    }

    /**
     * Pass-2 band task: requantizes its bands with the final range, writing
     * disjoint output rows. Read-before-write per row preserves the in-place
     * path (out == src at down == 1); rows never cross task boundaries.
     */
    private static final class QuantizeTask extends RecursiveAction {
        private final Bitmap src;
        private final Bitmap out;
        private final int sw, sh, gw, gh, loBand, hiBand;
        private final int down;
        private final float scale, gMin, range;

        QuantizeTask(Bitmap src, Bitmap out, int sw, int sh, int gw, int gh,
                     int down, float scale, float gMin, float range,
                     int loBand, int hiBand) {
            this.src = src;
            this.out = out;
            this.sw = sw;
            this.sh = sh;
            this.gw = gw;
            this.gh = gh;
            this.down = down;
            this.scale = scale;
            this.gMin = gMin;
            this.range = range;
            this.loBand = loBand;
            this.hiBand = hiBand;
        }

        static void quantize(Bitmap src, Bitmap out, int sw, int sh, int gw, int gh,
                             int down, float scale, float gMin, float range) {
            ForkJoinPool.commonPool().invoke(
                    new QuantizeTask(src, out, sw, sh, gw, gh, down, scale,
                            gMin, range, 0, gh));
        }

        @Override
        protected void compute() {
            if (hiBand - loBand <= BANDS_PER_TASK) {
                runBands();
                return;
            }
            int mid = loBand + (hiBand - loBand) / 2;
            invokeAll(new QuantizeTask(src, out, sw, sh, gw, gh, down, scale,
                            gMin, range, loBand, mid),
                    new QuantizeTask(src, out, sw, sh, gw, gh, down, scale,
                            gMin, range, mid, hiBand));
        }

        private void runBands() {
            int[] row = new int[sw];
            long[] sums = new long[gw];
            int[] counts = new int[gw];
            float[] bandBoost = new float[gw];
            int[] outRow = new int[gw];
            for (int b = loBand; b < hiBand; b++) {
                int yEnd = Math.min((b + 1) * down, sh);
                resetAccumulators(sums, counts);
                for (int y = b * down; y < yEnd; y++) {
                    src.getPixels(row, 0, sw, 0, y, sw, 1);
                    accumulateRow(row, sums, counts, gw, down);
                }
                finishBand(sums, counts, bandBoost, scale, gw);
                for (int gx = 0; gx < gw; gx++) {
                    float vNorm = (bandBoost[gx] - gMin) / range;
                    if (vNorm < 0f) vNorm = 0f;
                    else if (vNorm > 1f) vNorm = 1f;
                    final int byteVal = Math.round(vNorm * 255.0f);
                    outRow[gx] = (0xFF << 24) | (byteVal << 16) | (byteVal << 8) | byteVal;
                }
                out.setPixels(outRow, 0, gw, 0, b, gw, 1);
            }
        }
    }

    private static void resetAccumulators(long[] sums, int[] counts) {
        java.util.Arrays.fill(sums, 0L);
        java.util.Arrays.fill(counts, 0);
    }

    /** Accumulates one source row's red bytes into their gain-map blocks. */
    private static void accumulateRow(int[] row, long[] sums, int[] counts, int gw, int down) {
        for (int x = 0; x < row.length; x++) {
            final int gx = Math.min(x / down, gw - 1);
            sums[gx] += (row[x] >> 16) & 0xFF;
            counts[gx]++;
        }
    }

    private static void finishBand(long[] sums, int[] counts, float[] outBoost, float scale, int gw) {
        for (int gx = 0; gx < gw; gx++) {
            outBoost[gx] = (sums[gx] / (counts[gx] * 255f)) * scale;
        }
    }
}
