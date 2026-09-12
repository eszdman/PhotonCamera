package com.particlesdevs.photoncamera.processing.encoder;

import android.graphics.Bitmap;
import android.os.Build;

import androidx.heifwriter.HeifWriter;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.util.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SDR HEIC encode via {@code androidx.heifwriter} (API 28+, hardware HEVC
 * encoder required). Callers must gate with
 * {@link HeicSupport#isHeicEncodeSupported()}; this class throws
 * {@link UnsupportedOperationException} otherwise instead of silently
 * producing a broken file.
 */
public final class SdrHeicEncoder {

    private static final String TAG = "SdrHeicEncoder";
    /** HEVC quality matching {@code ImageSaver.JPG_QUALITY} (98). */
    public static final int HEIC_QUALITY = 98;
    /**
     * HeifWriter's single-frame block size: without tiling, the video
     * fallback rounds the encoded width up to a multiple of this.
     */
    private static final int ENCODING_BLOCK_SIZE = 32;

    private SdrHeicEncoder() {}

    /**
     * Encodes {@code sdr} to {@code dest} (must end in {@code .heic}).
     * The bitmap is recycled on success, mirroring
     * {@code ImageSaver.Util.saveBitmapAsJPG}.
     *
     * @return true on success.
     */
    public static boolean encodeToFile(Path dest, Bitmap sdr, ParseExif.ExifData exif) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw new UnsupportedOperationException("HEIC encode needs API 28+");
        }
        if (!HeicSupport.isHeicEncodeSupported()) {
            throw new UnsupportedOperationException("No HEVC HEIC encoder on this device");
        }
        if (sdr == null || sdr.isRecycled()) {
            throw new IllegalArgumentException("Null/recycled SDR bitmap");
        }
        if (exif != null) {
            exif.IMAGE_WIDTH = String.valueOf(sdr.getWidth());
            exif.IMAGE_LENGTH = String.valueOf(sdr.getHeight());
        }
        byte[] exifPayload = ExifBlob.fromExifData(exif);
        if (exifPayload == null) {
            Log.e(TAG, "EXIF blob null, HEIC will carry no EXIF");
        }
        boolean ok = false;
        try {
            writeHeic(dest.toFile(), sdr, exifPayload);
            ok = true;
        } catch (Exception e) {
            Log.e(TAG, "HEIC encode failed: " + Log.getStackTraceString(e));
            ok = false;
        }
        if (ok) {
            try {
                sdr.recycle();
            } catch (Exception ignored) {
            }
        }
        return ok;
    }

    /**
     * Single writer for every HEIC item (SDR base, Ultra HDR base and gain
     * map). Prefers HeifWriter's single-frame encode (grid disabled): one
     * frame has better rate control and no 512x512 tile-boundary loss.
     * HeifWriter re-enables tiling when the selected encoder cannot handle
     * the full size, and its single-frame video fallback rounds the width up
     * to {@link #ENCODING_BLOCK_SIZE}, so non-aligned widths are verified and
     * a padded result is re-encoded with the default (grid) behavior. Any
     * grid-off failure retries with the grid default, which is the proven
     * pre-existing path.
     *
     * @param exifPayload raw {@code Exif\0\0 + TIFF} payload or null
     */
    static void writeHeic(File dest, Bitmap bitmap, byte[] exifPayload) throws Exception {
        try {
            writeHeic(dest, bitmap, exifPayload, false);
            if (bitmap.getWidth() % ENCODING_BLOCK_SIZE != 0
                    && !hasExactDimensions(dest, bitmap)) {
                throw new IllegalStateException("grid-off encode rounded dimensions");
            }
        } catch (Exception gridOffFailed) {
            Log.d(TAG, "Single-frame HEIC encode unavailable ("
                    + gridOffFailed.getMessage() + "), retrying with grid");
            try {
                Files.deleteIfExists(dest.toPath());
            } catch (Exception ignored) {
            }
            writeHeic(dest, bitmap, exifPayload, true);
        }
    }

    private static void writeHeic(File dest, Bitmap bitmap, byte[] exifPayload,
            boolean gridEnabled) throws Exception {
        HeifWriter writer = null;
        try {
            writer = new HeifWriter.Builder(
                    dest.getAbsolutePath(),
                    bitmap.getWidth(), bitmap.getHeight(),
                    HeifWriter.INPUT_MODE_BITMAP)
                    .setQuality(HEIC_QUALITY)
                    .setGridEnabled(gridEnabled)
                    .build();
            writer.start();
            writer.addBitmap(bitmap);
            if (exifPayload != null) {
                try {
                    writer.addExifData(0, exifPayload, 0, exifPayload.length);
                    Log.d(TAG, "HEIC EXIF attached: " + exifPayload.length + " bytes");
                } catch (Exception e) {
                    Log.e(TAG, "addExifData failed (non-fatal)", e);
                }
            }
            writer.stop(0);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** True when the written file's primary item ispe matches the bitmap. */
    private static boolean hasExactDimensions(File dest, Bitmap bitmap) {
        try {
            int[] size = UltraHdrHeicContainer.primarySize(
                    Files.readAllBytes(dest.toPath()));
            return size[0] == bitmap.getWidth() && size[1] == bitmap.getHeight();
        } catch (Exception e) {
            return false;
        }
    }
}
