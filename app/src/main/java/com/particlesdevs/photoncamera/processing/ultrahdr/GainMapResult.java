package com.particlesdevs.photoncamera.processing.ultrahdr;

/**
 * The processed Ultra HDR gain map: a complete JPEG encoding of the
 * single-channel gain map aligned with the final base image orientation, and
 * the max/min content boost (the HDR/SDR brightness ratios at which the gain
 * map reaches its full/empty value). Holding the compressed JPEG instead of a
 * Bitmap keeps the capture pipeline's peak memory low for high-resolution
 * sensors.
 */
public class GainMapResult {
    public final byte[] gainMapJpeg;
    public final float maxContentBoost;
    public final float minContentBoost;

    public GainMapResult(byte[] gainMapJpeg, float maxContentBoost,
                         float minContentBoost) {
        this.gainMapJpeg = gainMapJpeg;
        this.maxContentBoost = maxContentBoost;
        this.minContentBoost = minContentBoost;
    }
}
