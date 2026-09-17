package com.particlesdevs.photoncamera.processing.ultrahdr;

import android.graphics.Bitmap;

import com.particlesdevs.photoncamera.api.ParseExif;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orchestrates the full Ultra HDR encode:
 * <ol>
 *   <li>take the encoded gain map from PostPipeline's scene-anchored pass
 *       (pre-LTM scene luma, midtone-anchored to the stored base) and
 *       normalize it via {@link GainMapComputer#compute}</li>
 *   <li>compress the SDR base to a JPEG (optionally with EXIF), compress the
 *       gain map to a JPEG</li>
 *   <li>assemble the Ultra HDR container ({@link UltraHdrContainer})</li>
 * </ol>
 *
 * The caller is responsible for freeing the returned buffer / recycling the bitmaps.
 */
public final class UltraHdrEncoder {

    private static final int DEFAULT_QUALITY = 95;

    private UltraHdrEncoder() {}

    /**
     * @param sdr  SDR display bitmap (ARGB_8888, sRGB)
     * @param gm   gain-map result produced by {@link GainMapComputer#compute}
     * @param exif optional EXIF to embed in the primary JPEG (may be null)
     * @return Ultra HDR JPEG bytes
     */
    public static byte[] encode(Bitmap sdr, GainMapComputer.Result gm, ParseExif.ExifData exif) {
        if (sdr != null && !sdr.isRecycled() && exif != null) {
            exif.IMAGE_WIDTH = String.valueOf(sdr.getWidth());
            exif.IMAGE_LENGTH = String.valueOf(sdr.getHeight());
        }
        // The two compresses are independent (distinct bitmaps, deterministic
        // per-input encoders): run them together, so latency is the max
        // instead of the sum. Byte-identical outputs either way.
        final byte[][] sdrHolder = new byte[1][];
        final byte[][] gainHolder = new byte[1][];
        final Throwable[] failure = new Throwable[1];
        Thread sdrThread = new Thread(() -> {
            try {
                sdrHolder[0] = compress(sdr, DEFAULT_QUALITY);
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        Thread gainThread = new Thread(() -> {
            try {
                final ByteArrayOutputStream gainOut = new ByteArrayOutputStream();
                if (!gm.gainMap.compress(Bitmap.CompressFormat.JPEG, DEFAULT_QUALITY, gainOut)) {
                    throw new RuntimeException("Failed to compress gain map");
                }
                gainHolder[0] = gainOut.toByteArray();
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        sdrThread.start();
        gainThread.start();
        try {
            sdrThread.join();
            gainThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during parallel compress", e);
        }
        if (failure[0] != null) {
            if (failure[0] instanceof RuntimeException) {
                throw (RuntimeException) failure[0];
            }
            throw new RuntimeException("Parallel compress failed", failure[0]);
        }
        final byte[] sdrJpeg = sdrHolder[0];
        final byte[] sdrJpegExif = (exif != null) ? injectExif(sdrJpeg, exif) : sdrJpeg;
        final byte[] gainMapJpeg = gainHolder[0];

        return UltraHdrContainer.encode(sdrJpegExif, gainMapJpeg,
                gm.gainMapMin, gm.gainMapMax, gm.hdrCapacityMax);
    }

    private static byte[] compress(Bitmap bmp, int quality) {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        if (!bmp.compress(Bitmap.CompressFormat.JPEG, quality, os)) {
            throw new RuntimeException("Failed to compress SDR JPEG");
        }
        return os.toByteArray();
    }

    /**
     * Writes {@code jpeg} to a temp file, applies {@code exif} via
     * {@link ParseExif#setAllAttributes} (which inserts an EXIF APP1), and reads
     * the result back. Doing this on a baseline JPEG (no XMP/MPF yet) keeps all
     * existing segments intact.
     *
     * <p>Fast path first: splice a stub-built APP1 after SOI (same tags,
     * ~KBs of file I/O instead of roundtripping the full JPEG). Falls back
     * to the temp-file roundtrip below on any failure.
     */
    private static byte[] injectExif(byte[] jpeg, ParseExif.ExifData exif) {
        byte[] spliced = trySpliceApp1(jpeg, exif);
        if (spliced != null) {
            return spliced;
        }
        File tmp = null;
        try {
            tmp = File.createTempFile("uhdr_exif_", ".jpg");
            Files.write(tmp.toPath(), jpeg);
            ExifInterface inter = ParseExif.setAllAttributes(tmp, exif);
            if (inter != null) inter.saveAttributes();
            return Files.readAllBytes(tmp.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return jpeg; // fall back to EXIF-less base
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    /**
     * Returns {@code SOI + APP1 + rest} for a baseline JPEG, or null when the
     * input isn't one (caller keeps its file path). Marker order after SOI
     * may differ from ExifInterface placement, but the tag payload is built
     * by the same setAllAttributes call.
     */
    private static byte[] trySpliceApp1(byte[] jpeg, ParseExif.ExifData exif) {
        try {
            if (jpeg == null || jpeg.length < 4
                    || jpeg[0] != (byte) 0xFF || jpeg[1] != (byte) 0xD8) {
                return null;
            }
            byte[] app1 = ParseExif.buildApp1Segment(exif);
            if (app1 == null || app1.length < 4) {
                return null;
            }
            byte[] out = new byte[2 + app1.length + (jpeg.length - 2)];
            out[0] = (byte) 0xFF;
            out[1] = (byte) 0xD8;
            System.arraycopy(app1, 0, out, 2, app1.length);
            System.arraycopy(jpeg, 2, out, 2 + app1.length, jpeg.length - 2);
            return out;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
