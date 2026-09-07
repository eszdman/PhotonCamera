package com.particlesdevs.photoncamera.processing.encoder;

import android.graphics.Bitmap;
import android.os.Build;

import androidx.heifwriter.HeifWriter;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.util.Log;

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
    /** HEVC quality tuned to roughly match the JPEG q98 output. */
    public static final int HEIC_QUALITY = 90;

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
        byte[] exifPayload = ExifBlob.fromExifData(exif);
        HeifWriter writer = null;
        boolean ok = false;
        try {
            writer = new HeifWriter.Builder(
                    dest.toAbsolutePath().toString(),
                    sdr.getWidth(), sdr.getHeight(),
                    HeifWriter.INPUT_MODE_BITMAP)
                    .setQuality(HEIC_QUALITY)
                    .build();
            writer.start();
            writer.addBitmap(sdr);
            if (exifPayload != null) {
                try {
                    writer.addExifData(0, exifPayload, 0, exifPayload.length);
                } catch (Exception e) {
                    Log.e(TAG, "addExifData failed (non-fatal)", e);
                }
            }
            writer.stop(0);
            ok = true;
        } catch (Exception e) {
            Log.e(TAG, "HEIC encode failed: " + Log.getStackTraceString(e));
            ok = false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
        if (ok) {
            try {
                sdr.recycle();
            } catch (Exception ignored) {
            }
        }
        return ok;
    }
}
