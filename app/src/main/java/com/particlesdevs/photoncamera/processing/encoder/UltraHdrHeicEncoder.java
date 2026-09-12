package com.particlesdevs.photoncamera.processing.encoder;

import android.graphics.Bitmap;
import android.os.Build;

import androidx.heifwriter.HeifWriter;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.processing.ultrahdr.GainMapComputer;
import com.particlesdevs.photoncamera.util.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ultra HDR HEIC encode (API 34+ only).
 *
 * <p>Takes the same {@link GainMapComputer.Result} as the JPEG path, HEVC
 * encodes the SDR base and the gain map via {@code HeifWriter} into temp
 * files, then muxes them with {@link UltraHdrHeicContainer} (manual BMFF,
 * no native dependency). Any failure throws so callers fall back to SDR
 * HEIC — a mux bug can never lose a shot.
 */
public final class UltraHdrHeicEncoder {

    private static final String TAG = "UltraHdrHeicEncoder";

    private UltraHdrHeicEncoder() {}

    /**
     * Encodes and writes {@code dest} (must end in {@code .heic}).
     * Both bitmaps are recycled <b>only on success</b> — on failure they are
     * left alive so {@link StillEncoder} fallbacks can still use them.
     */
    public static void encodeToFile(Path dest, Bitmap sdr,
            GainMapComputer.Result gain, ParseExif.ExifData exif) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            throw new UnsupportedOperationException("HEIC Ultra HDR needs API 34+");
        }
        if (!HeicSupport.isUltraHdrHeicSupported()) {
            throw new UnsupportedOperationException("HEIC Ultra HDR not supported on this device");
        }
        if (sdr == null || sdr.isRecycled() || gain == null || gain.gainMap == null) {
            throw new IllegalArgumentException("Null/recycled SDR or gain map");
        }
        if (exif != null) {
            exif.IMAGE_WIDTH = String.valueOf(sdr.getWidth());
            exif.IMAGE_LENGTH = String.valueOf(sdr.getHeight());
        }
        File baseTmp = File.createTempFile("uhdr_heic_base_", ".heic");
        File gainTmp = File.createTempFile("uhdr_heic_gain_", ".heic");
        boolean success = false;
        try {
            writeSingleHeic(baseTmp, sdr);
            writeSingleHeic(gainTmp, gain.gainMap);
            // Gain pixels are on disk now and only its metadata (captured
            // above as ints) is needed downstream: release the ~244 MB
            // (64 MP) before the read/merge/verify peak. sdr deliberately
            // stays alive: the SDR fallback needs it.
            recycleQuietly(gain.gainMap);
            byte[] baseBytes = Files.readAllBytes(baseTmp.toPath());
            byte[] gainBytes = Files.readAllBytes(gainTmp.toPath());
            Log.d(TAG, "HEIC mux inputs: base=" + (baseBytes.length / 1024)
                    + "KB gain=" + (gainBytes.length / 1024) + "KB");
            UltraHdrHeicContainer.Inputs in = new UltraHdrHeicContainer.Inputs();
            in.baseHeic = baseBytes;
            in.gainHeic = gainBytes;
            in.baseW = sdr.getWidth();
            in.baseH = sdr.getHeight();
            in.gainW = gain.gainW;
            in.gainH = gain.gainH;
            in.gainMapMin = gain.gainMapMin;
            in.gainMapMax = gain.gainMapMax;
            in.hdrCapacityMax = gain.hdrCapacityMax;
            in.exifPayload = ExifBlob.fromExifData(exif);
            if (in.exifPayload != null) {
                Log.d(TAG, "HEIC EXIF blob: " + in.exifPayload.length + " bytes"
                        + (ExifBlob.hasTiffPayload(in.exifPayload) ? " (TIFF ok)" : " (TIFF BAD)"));
            } else {
                Log.e(TAG, "EXIF blob null, HEIC will carry no EXIF");
            }
            byte[] merged = UltraHdrHeicContainer.merge(in);
            // Inputs served their purpose: release before the write/verify
            // window so base+gain+merged aren't all heap-resident at once.
            in.baseHeic = null;
            in.gainHeic = null;
            baseBytes = null;
            gainBytes = null;
            Log.d(TAG, "HEIC mux output: merged=" + (merged.length / 1024) + "KB");
            // Structural verify before writing: parses the boxes we just
            // assembled (layout + primary ispe) without ImageDecoder, whose
            // abort path could still decode the whole image.
            int[] primary = UltraHdrHeicContainer.primarySize(merged);
            if (primary[0] != sdr.getWidth() || primary[1] != sdr.getHeight()) {
                throw new IllegalStateException("merged HEIC dimensions "
                        + primary[0] + "x" + primary[1]
                        + " != expected " + sdr.getWidth() + "x" + sdr.getHeight());
            }
            try {
                Files.write(dest, merged);
            } catch (Exception writeFailed) {
                try {
                    Files.deleteIfExists(dest);
                } catch (Exception ignored) {
                }
                throw writeFailed;
            }
            success = true;
        } finally {
            // noinspection ResultOfMethodCallIgnored
            baseTmp.delete();
            // noinspection ResultOfMethodCallIgnored
            gainTmp.delete();
            if (success) {
                recycleQuietly(sdr);
                recycleQuietly(gain.gainMap);
            }
        }
        Log.d(TAG, "HEIC Ultra HDR written: " + dest);
    }

    private static void recycleQuietly(Bitmap bitmap) {
        try {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        } catch (Exception ignored) {
        }
    }

    private static void writeSingleHeic(File dest, Bitmap bitmap) throws Exception {
        HeifWriter writer = null;
        try {
            writer = new HeifWriter.Builder(
                    dest.getAbsolutePath(),
                    bitmap.getWidth(), bitmap.getHeight(),
                    HeifWriter.INPUT_MODE_BITMAP)
                    .setQuality(SdrHeicEncoder.HEIC_QUALITY)
                    .build();
            writer.start();
            writer.addBitmap(bitmap);
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
}
