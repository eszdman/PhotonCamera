package com.particlesdevs.photoncamera.processing.ultrahdr;

import android.graphics.Bitmap;
import android.graphics.Point;

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
     * Adaptive gain-map/scene-luma scale: full resolution up to 16 MP, above
     * that the raw megapixels over 16 rounded to the nearest integer
     * (50.3 MP -> 3, 64 MP -> 4). Captures at or below 16 MP keep the exact
     * full-resolution path.
     */
    public static int computeScaleDown(Point rawSize) {
        double mp = (double) rawSize.x * (double) rawSize.y / 1_000_000.0;
        if (mp <= 16.0) return 1;
        return (int) Math.round(mp / 16.0);
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
        final int[] row = new int[sw];
        final long[] sums = new long[gw];
        final int[] counts = new int[gw];
        final float[] bandBoost = new float[gw];

        // Pass 1: box-average per map pixel - decoding v to logBoost happens
        // per band, averaging in log domain (geometric mean of gains, robust
        // to outliers) - while tracking the global maximum.
        float maxBoost = Float.NEGATIVE_INFINITY;
        resetAccumulators(sums, counts);
        for (int y = 0; y < sh; y++) {
            src.getPixels(row, 0, sw, 0, y, sw, 1);
            accumulateRow(row, sums, counts, gw, down);
            if ((y + 1) % down == 0 || y == sh - 1) {
                finishBand(sums, counts, bandBoost, scale, gw);
                for (int gx = 0; gx < gw; gx++) {
                    if (bandBoost[gx] > maxBoost) maxBoost = bandBoost[gx];
                }
                resetAccumulators(sums, counts);
            }
        }

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
        final Bitmap out = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
        final int[] outRow = new int[gw];
        resetAccumulators(sums, counts);
        for (int y = 0, gy = 0; y < sh; y++) {
            src.getPixels(row, 0, sw, 0, y, sw, 1);
            accumulateRow(row, sums, counts, gw, down);
            if ((y + 1) % down == 0 || y == sh - 1) {
                finishBand(sums, counts, bandBoost, scale, gw);
                for (int gx = 0; gx < gw; gx++) {
                    float vNorm = (bandBoost[gx] - gMin) / range;
                    if (vNorm < 0f) vNorm = 0f;
                    else if (vNorm > 1f) vNorm = 1f;
                    final int byteVal = Math.round(vNorm * 255.0f);
                    outRow[gx] = (0xFF << 24) | (byteVal << 16) | (byteVal << 8) | byteVal;
                }
                out.setPixels(outRow, 0, gw, 0, gy, gw, 1);
                gy++;
                resetAccumulators(sums, counts);
            }
        }

        return new Result(out, gMin, gMax);
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
